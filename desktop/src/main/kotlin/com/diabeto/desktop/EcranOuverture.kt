package com.diabeto.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.diabeto.util.ReglesEssais
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val clavierMdp = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done)

/** Apres la premiere connexion : choisir le mot de passe qui ouvrira DiaSmart sur ce PC. */
@Composable
fun EcranCreerMotDePasseLocal(fb: FirebaseRest, profil: Profil, onFini: () -> Unit) {
    val scope = rememberCoroutineScope()
    var mdp by remember { mutableStateOf("") }
    var mdp2 by remember { mutableStateOf("") }
    var erreur by remember { mutableStateOf<String?>(null) }
    var enCours by remember { mutableStateOf(false) }

    fun enregistrer() {
        erreur = when {
            mdp.length < SessionPc.LONGUEUR_MIN -> "Le mot de passe doit faire au moins ${SessionPc.LONGUEUR_MIN} caractères."
            mdp != mdp2 -> "Les deux mots de passe ne sont pas identiques."
            else -> null
        }
        val s = fb.session
        if (erreur != null || s == null || enCours) return
        enCours = true
        scope.launch {
            try {
                withContext(Dispatchers.Default) { SessionPc.enregistrer(s.uid, s.email, profil.nomComplet, s.refreshToken, mdp) }
                onFini()
            } catch (e: Exception) {
                erreur = "Enregistrement impossible sur ce PC (${e.message})."
            } finally { enCours = false }
        }
    }

    Carte {
        Text("Mot de passe de ce PC", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Indigo)
        Text("Choisissez un mot de passe pour ouvrir DiaSmart sur cet ordinateur. Les prochaines fois, " +
            "vous n'aurez plus besoin de l'email : seulement ce mot de passe.", fontSize = 14.sp, color = Color.DarkGray)
        OutlinedTextField(mdp, { mdp = it.take(64) }, label = { Text("Nouveau mot de passe (${SessionPc.LONGUEUR_MIN} caractères ou plus)") },
            singleLine = true, visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = clavierMdp.copy(imeAction = ImeAction.Next), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(mdp2, { mdp2 = it.take(64) }, label = { Text("Confirmer le mot de passe") },
            singleLine = true, visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = clavierMdp, keyboardActions = KeyboardActions(onDone = { enregistrer() }),
            modifier = Modifier.fillMaxWidth())
        erreur?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
        Button(onClick = { enregistrer() }, enabled = !enCours, modifier = Modifier.fillMaxWidth()) {
            if (enCours) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
            else Text("Enregistrer et ouvrir")
        }
    }
}

/** Ouverture des fois suivantes : nom de l'etablissement + mot de passe de ce PC. */
@Composable
fun EcranDeverrouillage(
    fb: FirebaseRest,
    enregistree: SessionPc.Enregistree,
    onOuvert: (Profil) -> Unit,
    onAutreCompte: (message: String?) -> Unit
) {
    val compte = remember { ServiceCompte(fb) }
    val limite = remember { LimiteurPc("mdp_local_pc") }
    val scope = rememberCoroutineScope()
    var mdp by remember { mutableStateOf("") }
    var erreur by remember { mutableStateOf<String?>(null) }
    var enCours by remember { mutableStateOf(false) }

    var maintenant by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(1000); maintenant = System.currentTimeMillis() } }
    val attente = maintenant.let { limite.attenteRestanteMs() }

    fun ouvrir() {
        if (enCours) return
        if (mdp.isEmpty()) { erreur = "Entrez le mot de passe de ce PC."; return }
        if (limite.attenteRestanteMs() > 0) { erreur = limite.messageBlocage(); return }
        enCours = true; erreur = null
        scope.launch {
            try {
                val jeton = withContext(Dispatchers.Default) { SessionPc.ouvrir(mdp) }
                if (jeton == null) {
                    limite.echec()
                    maintenant = System.currentTimeMillis()
                    erreur = limite.messageApresEchec("Mot de passe incorrect.")
                    return@launch
                }
                limite.reussite()
                val s = try {
                    fb.reprendre(enregistree.uid, enregistree.email, jeton)
                } catch (e: FirebaseRest.ErreurFirebase) {
                    if (e.code in 400..403) {
                        SessionPc.oublier()
                        onAutreCompte("La connexion gardée sur ce PC n'est plus valable (mot de passe du compte changé ?). " +
                            "Reconnectez-vous avec votre email et votre mot de passe.")
                        return@launch
                    }
                    throw e
                }
                val p = compte.profil(s.uid)
                if (p == null || !p.estSoignant) {
                    fb.deconnexion(); SessionPc.oublier()
                    onAutreCompte("Profil soignant introuvable. Reconnectez-vous avec votre email.")
                    return@launch
                }
                // Firebase peut renouveler le jeton de reconnexion : on garde le dernier.
                if (s.refreshToken != jeton) withContext(Dispatchers.Default) {
                    SessionPc.enregistrer(s.uid, s.email, p.nomComplet, s.refreshToken, mdp)
                }
                onOuvert(p)
            } catch (e: Exception) {
                erreur = e.message ?: "Ouverture impossible."
            } finally { enCours = false }
        }
    }

    Carte {
        Text("DiaSmart", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Indigo)
        if (enregistree.nomEtablissement.isNotBlank()) {
            Text(enregistree.nomEtablissement, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        }
        Text(enregistree.nomSoignant.ifBlank { enregistree.email }, fontSize = 14.sp, color = Color.DarkGray)
        OutlinedTextField(mdp, { mdp = it.take(64) }, label = { Text("Mot de passe de ce PC") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = clavierMdp, keyboardActions = KeyboardActions(onDone = { ouvrir() }),
            modifier = Modifier.fillMaxWidth())
        erreur?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
        if (attente > 0) Text("Trop d'essais : réessayez dans ${ReglesEssais.formaterAttente(attente)}.",
            fontSize = 13.sp, color = Orange, fontWeight = FontWeight.Medium)
        Button(onClick = { ouvrir() }, enabled = !enCours && attente == 0L, modifier = Modifier.fillMaxWidth()) {
            if (enCours) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
            else Text("Ouvrir")
        }
        TextButton(onClick = { SessionPc.oublier(); onAutreCompte(null) }, enabled = !enCours) {
            Text("Mot de passe oublié ou autre compte : se connecter avec l'email")
        }
    }
}

@Composable
private fun Carte(contenu: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Card(Modifier.widthIn(max = 460.dp)) {
            Column(Modifier.padding(32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { contenu() }
        }
    }
}
