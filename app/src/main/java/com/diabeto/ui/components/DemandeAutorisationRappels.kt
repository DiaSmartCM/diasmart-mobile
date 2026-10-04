package com.diabeto.ui.components

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.diabeto.notifications.AlarmScheduler
import com.diabeto.util.OptimisationBatterie
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Demande d'autorisation des rappels AU MOMENT OU on programme un rappel
 * (traitement ou rendez-vous), au lieu d'une carte permanente dans les
 * Parametres.
 *
 * Les ViewModels appellent [verifier] juste apres avoir pose une alarme. Si
 * le telephone risque de la bloquer (mise en veille de l'appli ou alarmes
 * exactes refusees), le dialogue [DemandeAutorisationRappelsDialog], place
 * une seule fois a la racine de la navigation, s'affiche.
 */
object DemandeAutorisationRappels {
    internal val aAfficher = MutableStateFlow(false)

    // Une seule fois par lancement de l'appli si l'utilisateur dit "Plus tard"
    @Volatile private var refuseCeLancement = false

    fun manque(context: Context): Boolean =
        !OptimisationBatterie.estExemptee(context) || !AlarmScheduler.peutPoserAlarmeExacte(context)

    fun verifier(context: Context) {
        if (!refuseCeLancement && manque(context)) aAfficher.value = true
    }

    internal fun plusTard() {
        refuseCeLancement = true
        aAfficher.value = false
    }
}

@Composable
fun DemandeAutorisationRappelsDialog() {
    val afficher by DemandeAutorisationRappels.aAfficher.collectAsState()
    if (!afficher) return
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = DemandeAutorisationRappels::plusTard,
        icon = { Icon(Icons.Default.NotificationsActive, null) },
        title = { Text("Autoriser les rappels") },
        text = {
            Column {
                Text(
                    "Pour que ce rappel sonne à l'heure, même écran éteint, " +
                        "le téléphone doit laisser DiaSmart fonctionner en arrière-plan.",
                    fontSize = 14.sp
                )
                if (OptimisationBatterie.constructeurRestrictif()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Sur ${android.os.Build.MANUFACTURER}, activez aussi le démarrage automatique de DiaSmart.",
                        fontSize = 13.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                DemandeAutorisationRappels.aAfficher.value = false
                // Une autorisation a la fois : la plus bloquante d'abord.
                // La suivante sera demandee au prochain rappel programme.
                when {
                    !OptimisationBatterie.estExemptee(context) -> OptimisationBatterie.demanderExemption(context)
                    !AlarmScheduler.peutPoserAlarmeExacte(context) -> AlarmScheduler.ouvrirReglageAlarmes(context)
                }
            }){ Text("Autoriser") }
        },
        dismissButton = {
            TextButton(onClick = DemandeAutorisationRappels::plusTard) { Text("Plus tard") }
        }
    )
}
