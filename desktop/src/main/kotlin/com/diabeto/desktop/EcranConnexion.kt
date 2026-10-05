package com.diabeto.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class Etape { FORMULAIRE, PROFIL, CODE_EMAIL }

/**
 * Connexion ou creation d'un compte soignant (email + mot de passe),
 * avec les memes delais d'essai que l'app mobile et la verification
 * de l'email par code a 6 chiffres.
 */
@Composable
fun EcranConnexion(fb: FirebaseRest, onConnecte: (Profil) -> Unit) {
    val compte = remember { ServiceCompte(fb) }
    val limiteConnexion = remember { LimiteurPc("connexion_pc") }
    val limiteCode = remember { LimiteurPc("code_email_pc") }
    val scope = rememberCoroutineScope()

    var inscription by remember { mutableStateOf(false) }
    var etape by remember { mutableStateOf(Etape.FORMULAIRE) }
    var email by remember { mutableStateOf("") }
    var mdp by remember { mutableStateOf("") }
    var mdp2 by remember { mutableStateOf("") }
    var nom by remember { mutableStateOf("") }
    var prenom by remember { mutableStateOf("") }
    var accepte by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf("") }
    var erreur by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    var enCours by remember { mutableStateOf(false) }

    // Horloge pour le compte a rebours des blocages
    var maintenant by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(1000); maintenant = System.currentTimeMillis() } }
    val attenteConnexion = maintenant.let { limiteConnexion.attenteRestanteMs() }
    val attenteCode = maintenant.let { limiteCode.attenteRestanteMs() }

    fun lancer(action: suspend () -> Unit) {
        if (enCours) return
        enCours = true; erreur = null
        scope.launch {
            try { action() }
            catch (e: Exception) { erreur = e.message ?: "Une erreur est survenue." }
            finally { enCours = false }
        }
    }

    /** Compte ouvert : verifie le profil soignant puis l'email. */
    suspend fun finaliser() {
        val uid = fb.session?.uid ?: return
        val p = compte.profil(uid)
        when {
            p == null -> { etape = Etape.PROFIL; info = "Complétez votre profil soignant." }
            !p.estSoignant -> {
                fb.deconnexion(); etape = Etape.FORMULAIRE
                erreur = "Ce compte est un compte patient. La version PC est réservée aux soignants : " +
                    "créez un compte soignant avec une autre adresse email."
            }
            !fb.emailVerifie() -> {
                etape = Etape.CODE_EMAIL
                info = try {
                    compte.envoyerCodeEmail()
                    "Un code à 6 chiffres a été envoyé à ${fb.session?.email}. Regardez aussi dans les spams."
                } catch (e: Exception) {
                    "Le code n'a pas pu être envoyé (${e.message}). Cliquez sur « Renvoyer le code »."
                }
            }
            else -> onConnecte(p)
        }
    }

    fun seConnecter() {
        if (email.isBlank() || mdp.isBlank()) { erreur = "Entrez votre email et votre mot de passe."; return }
        if (limiteConnexion.attenteRestanteMs() > 0) { erreur = limiteConnexion.messageBlocage(); return }
        lancer {
            try {
                fb.connexion(email, mdp)
            } catch (e: FirebaseRest.ErreurFirebase) {
                if (e.identifiantsFaux) {
                    limiteConnexion.echec()
                    maintenant = System.currentTimeMillis()
                    throw Exception(limiteConnexion.messageApresEchec(e.message ?: ""))
                }
                throw e
            }
            limiteConnexion.reussite()
            finaliser()
        }
    }

    fun creerCompte() {
        erreur = when {
            nettoyer(nom, 60).length < 2 || nettoyer(prenom, 60).isEmpty() -> "Entrez votre nom et votre prénom."
            !Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$").matches(email.trim()) -> "Adresse email invalide."
            mdp.length < 8 -> "Le mot de passe doit faire au moins 8 caractères."
            mdp != mdp2 -> "Les deux mots de passe ne sont pas identiques."
            !accepte -> "Cochez la case pour confirmer que vous êtes soignant."
            else -> null
        }
        if (erreur != null) return
        lancer {
            val s = fb.inscription(email, mdp)
            compte.creerProfilSoignant(s.uid, s.email, nom, prenom)
            finaliser()
        }
    }

    fun completerProfil() {
        if (nettoyer(nom, 60).length < 2 || nettoyer(prenom, 60).isEmpty()) { erreur = "Entrez votre nom et votre prénom."; return }
        lancer {
            val s = fb.session ?: return@lancer
            compte.creerProfilSoignant(s.uid, s.email, nom, prenom)
            finaliser()
        }
    }

    fun validerCode() {
        if (code.length != 6) { erreur = "Le code fait 6 chiffres."; return }
        if (limiteCode.attenteRestanteMs() > 0) { erreur = limiteCode.messageBlocage(); return }
        lancer {
            try {
                compte.verifierCodeEmail(code)
            } catch (e: FirebaseRest.ErreurFirebase) {
                if (e.code in 400..499) {
                    limiteCode.echec()
                    maintenant = System.currentTimeMillis()
                    throw Exception(limiteCode.messageApresEchec(e.message ?: "Code incorrect."))
                }
                throw e
            }
            limiteCode.reussite()
            fb.jetonValide(force = true)  // le jeton doit porter "email verifie"
            finaliser()
        }
    }

    fun motDePasseOublie() {
        if (!email.contains('@')) { erreur = "Entrez d'abord votre email ci-dessus."; return }
        lancer {
            runCatching { fb.motDePasseOublie(email) }
            info = "Si un compte avec mot de passe existe pour cet email, un lien de réinitialisation vient d'être envoyé. " +
                "Regardez aussi dans les spams."
        }
    }

    val entree = KeyboardOptions(imeAction = ImeAction.Done)

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Card(Modifier.widthIn(max = 460.dp)) {
            Column(
                Modifier.padding(32.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("DiaSmart", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Indigo)
                Text("Version PC pour les soignants et les établissements de santé.", fontSize = 14.sp, color = Color.DarkGray)

                when (etape) {
                    Etape.FORMULAIRE -> {
                        TabRow(if (inscription) 1 else 0) {
                            Tab(!inscription, { inscription = false; erreur = null; info = null }, text = { Text("Se connecter") })
                            Tab(inscription, { inscription = true; erreur = null; info = null }, text = { Text("Créer un compte") })
                        }
                        if (inscription) {
                            Text("Compte soignant (médecin, infirmier, éducateur…). Si votre structure a déjà un espace " +
                                "DiaSmart, créez votre compte puis rejoignez-la avec le code soignant donné par l'administrateur.",
                                fontSize = 13.sp, color = Color.Gray)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(prenom, { prenom = it.take(60) }, label = { Text("Prénom") }, singleLine = true, modifier = Modifier.weight(1f))
                                OutlinedTextField(nom, { nom = it.take(60) }, label = { Text("Nom") }, singleLine = true, modifier = Modifier.weight(1f))
                            }
                        } else {
                            Text("Utilisez le même compte que sur l'application mobile (email + mot de passe).",
                                fontSize = 13.sp, color = Color.Gray)
                        }
                        OutlinedTextField(email, { email = it.trim().take(120) }, label = { Text("Email") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(mdp, { mdp = it.take(128) }, label = { Text("Mot de passe") }, singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = entree.copy(keyboardType = KeyboardType.Password),
                            keyboardActions = KeyboardActions(onDone = { if (!inscription) seConnecter() }),
                            modifier = Modifier.fillMaxWidth())
                        if (inscription) {
                            OutlinedTextField(mdp2, { mdp2 = it.take(128) }, label = { Text("Confirmer le mot de passe") }, singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(accepte, { accepte = it })
                                Text("Je suis professionnel de santé et j'accepte les conditions d'utilisation de DiaSmart.", fontSize = 13.sp)
                            }
                        }
                    }
                    Etape.PROFIL -> {
                        Text("Votre compte existe mais n'a pas encore de profil. Il sera créé comme compte soignant.", fontSize = 13.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(prenom, { prenom = it.take(60) }, label = { Text("Prénom") }, singleLine = true, modifier = Modifier.weight(1f))
                            OutlinedTextField(nom, { nom = it.take(60) }, label = { Text("Nom") }, singleLine = true, modifier = Modifier.weight(1f))
                        }
                    }
                    Etape.CODE_EMAIL -> {
                        Text("Vérification de l'email", fontWeight = FontWeight.SemiBold)
                        OutlinedTextField(code, { code = it.filter(Char::isDigit).take(6) }, label = { Text("Code à 6 chiffres") },
                            singleLine = true, keyboardOptions = entree.copy(keyboardType = KeyboardType.Number),
                            keyboardActions = KeyboardActions(onDone = { validerCode() }), modifier = Modifier.fillMaxWidth())
                    }
                }

                info?.let { Text(it, fontSize = 13.sp, color = Indigo) }
                erreur?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }

                val attente = when {
                    etape == Etape.FORMULAIRE && !inscription -> attenteConnexion
                    etape == Etape.CODE_EMAIL -> attenteCode
                    else -> 0L
                }
                if (attente > 0) Text("Trop d'essais : réessayez dans ${ReglesEssais.formaterAttente(attente)}.",
                    fontSize = 13.sp, color = Orange, fontWeight = FontWeight.Medium)

                Button(
                    onClick = {
                        when (etape) {
                            Etape.FORMULAIRE -> if (inscription) creerCompte() else seConnecter()
                            Etape.PROFIL -> completerProfil()
                            Etape.CODE_EMAIL -> validerCode()
                        }
                    },
                    enabled = !enCours && attente == 0L,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (enCours) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                    else Text(when (etape) {
                        Etape.FORMULAIRE -> if (inscription) "Créer mon compte soignant" else "Se connecter"
                        Etape.PROFIL -> "Enregistrer"
                        Etape.CODE_EMAIL -> "Valider le code"
                    })
                }

                when (etape) {
                    Etape.FORMULAIRE -> if (!inscription) TextButton(onClick = { motDePasseOublie() }, enabled = !enCours) {
                        Text("Mot de passe oublié ?")
                    }
                    Etape.CODE_EMAIL -> Row {
                        TextButton(onClick = {
                            lancer { compte.envoyerCodeEmail(); info = "Nouveau code envoyé à ${fb.session?.email}." }
                        }, enabled = !enCours) { Text("Renvoyer le code") }
                        TextButton(onClick = { fb.deconnexion(); etape = Etape.FORMULAIRE; code = ""; info = null; erreur = null }) {
                            Text("Retour")
                        }
                    }
                    Etape.PROFIL -> TextButton(onClick = { fb.deconnexion(); etape = Etape.FORMULAIRE; info = null; erreur = null }) {
                        Text("Retour")
                    }
                }
            }
        }
    }
}
