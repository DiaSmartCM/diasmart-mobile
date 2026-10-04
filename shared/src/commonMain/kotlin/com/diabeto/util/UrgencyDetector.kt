package com.diabeto.util

/**
 * Detecteur d'urgence medicale par mots-cles (patient diabetique).
 *
 * Analyse le message du patient AVANT l'appel au LLM (Gemini ou Gemma) pour :
 * 1. Afficher IMMEDIATEMENT les numeros d'urgence + conseils de survie (0 ms de latence)
 * 2. Eviter qu'un patient en detresse attende 5-15s la reponse du modele
 * 3. Fonctionne en ligne ET hors ligne (pure logique locale)
 *
 * Utilise par ChatbotRepository avant de router vers Gemini/Gemma.
 */
object UrgencyDetector {

    /**
     * Mots-cles suggerant une urgence medicale pour un diabetique.
     * Tous en minuscules, sans accents (on normalise le message avant matching).
     *
     * Couvre :
     * - Francais (langue officielle)
     * - Pidgin English (Regions Sud-Ouest et Nord-Ouest)
     * - Ewondo / Beti (Region du Centre, Sud)
     * - Duala (Region du Littoral)
     * - Bamileke / Ghomala (Region de l'Ouest)
     * - Bassa (Region du Littoral/Centre)
     * - Fulfulde / Fulani (Regions du Nord)
     */
    private val urgencyKeywords = listOf(
        // ═════════════ FRANCAIS ═════════════
        // Hypoglycemie severe (glycemie trop basse)
        "malaise", "evanoui", "je vais tomber", "tres faible", "perte de force",
        "tremble", "tremblement", "sueurs froides", "sueur froide", "transpir",
        "vertige", "etourdi", "etourdissement", "tete qui tourne",
        "confus", "desorient", "je ne sais plus", "perdu",
        "vision floue", "vois flou", "vois double",
        "palpitation", "coeur qui bat vite", "tachycardie",

        // Hyperglycemie severe (glycemie trop elevee)
        "soif enorme", "soif intense", "envie de boire", "bouche seche",
        "uriner beaucoup", "pipi souvent", "urines frequentes",
        "fatigue extreme", "epuise", "aucune energie",
        "nausee", "envie de vomir", "vomis", "vomissement",
        "haleine fruit", "odeur acetone", "souffle sucre",
        "respiration rapide", "essouffle", "mal a respirer",
        "mal au ventre", "douleur abdomen",

        // Urgence vitale (toutes pathologies)
        "coma", "inconscient", "perte connaissance", "ne reponds pas",
        "convulsion", "crise", "spasme",
        "douleur poitrine", "mal a la poitrine", "ecrasement poitrine",
        "engourdissement", "paralysie", "ne peux plus bouger",
        "difficulte a parler", "bouche tordue",

        // Appel explicite au secours
        "urgence", "au secours", "aidez moi", "aidez-moi",
        "danger", "grave", "tres mal", "ca va mal",
        "appelez", "appelle", "je meurs", "je vais mourir",

        // ═════════════ PIDGIN ENGLISH CAMEROUN ═════════════
        // Largement parle dans les regions anglophones (NW, SW) et urbain
        "help me", "helep me", "helep", "i di sick", "i de sick",
        "i no well", "i no fit", "i weak bad", "i weak",
        "something di pain me", "something de pain me",
        "ma belly di pain", "ma head di turn", "ma eye di turn",
        "i wan die", "i go die", "i di die",
        "call doctor", "call ambulance", "call sosa",
        "ma pikin sick", "pikin sick bad",

        // ═════════════ EWONDO / BETI (Centre, Sud) ═════════════
        // Langue parlee par 1M+ personnes, region de Yaounde
        "ma wu", "ma wou",              // je meurs / je suis mal
        "ma kon", "ma kone",            // je suis malade
        "evu", "evou",                  // maladie
        "mvon",                         // douleur
        "kelan", "kelen", "bata",       // aide-moi
        "ma ya'a", "ma ya",             // je vais mal
        "nnem",                         // coeur (douleur thoracique)

        // ═════════════ DUALA (Littoral, Douala) ═════════════
        // Langue vehiculaire cotiere, 300k+ locuteurs
        "na si malamba", "na malamba",  // je ne suis pas bien
        "musima", "musoma",             // douleur / souffrance
        "lambo", "lambwa",              // aide
        "na maha",                      // je suis fatigue/faible
        "kwedi",                        // mort
        "na bwele",                     // je suis malade
        "na wusu",                      // je ne suis pas bien

        // ═════════════ BAMILEKE / GHOMALA (Ouest) ═════════════
        // Groupe de langues de l'Ouest, 2M+ locuteurs
        "ma ko", "ma kô",               // je meurs
        "ngwa", "ngwà",                 // maladie
        "nkap",                         // douleur
        "pa' me",                       // aide-moi

        // ═════════════ BASSA (Littoral, Centre) ═════════════
        // Langue bantoue, 300k+ locuteurs
        "mut",                          // mort
        "ngen",                         // douleur
        "bika be", "bika bè",           // malade
        "nlema",                        // je suis faible
        "hola",                         // aide

        // ═════════════ FULFULDE / FULANI (Nord) ═════════════
        // Langue des regions du Nord (Adamaoua, Nord, Extreme-Nord)
        "mi yahi",                      // je vais mal
        "wallu mi", "walla mi",         // aide-moi
        "mi ronki",                     // je suis fatigue
        "nyawnde",                      // maladie
        "naawki",                       // douleur
        "mi maayan"                     // je meurs
    )

    /**
     * Symptomes necessitant une prise en charge immediate mais non vitale.
     * Emet un avertissement plus leger (pas d'appel SAMU).
     */
    private val warningKeywords = listOf(
        "fatigue", "mal a la tete", "maux de tete",
        "faim", "affame", "affamee",
        "anxiete", "stress", "inquiet"
    )

    /**
     * Normalise un texte francais pour le matching (minuscules, sans accents).
     */
    private fun normalize(text: String): String {
        return text.lowercase()
            .replace('é', 'e').replace('è', 'e').replace('ê', 'e').replace('ë', 'e')
            .replace('à', 'a').replace('â', 'a').replace('ä', 'a')
            .replace('ù', 'u').replace('û', 'u').replace('ü', 'u')
            .replace('î', 'i').replace('ï', 'i')
            .replace('ô', 'o').replace('ö', 'o')
            .replace('ç', 'c')
    }

    /**
     * Retourne true si le message contient un mot-cle d'urgence.
     */
    fun detectUrgency(message: String): Boolean {
        val normalized = normalize(message)
        return urgencyKeywords.any { normalized.contains(it) }
    }

    /**
     * Retourne true si le message contient un mot-cle de mise en garde (non urgent).
     */
    fun detectWarning(message: String): Boolean {
        val normalized = normalize(message)
        return warningKeywords.any { normalized.contains(it) }
    }

    /**
     * Reponse d'urgence formatee avec Markdown.
     * Affichee IMMEDIATEMENT avant la reponse du LLM (latence 0 ms).
     *
     * Contient :
     * - Numeros d'urgence Cameroun (SAMU, Police, Pompiers)
     * - Plan d'action immediat (mesurer, sucre rapide, eau)
     * - Message rassurant
     */
    fun getEmergencyResponse(): String = """
### ALERTE URGENCE DETECTEE

Tes symptomes peuvent etre graves. Voici ce qu'il faut faire MAINTENANT :

**1. Mesure ta glycemie tout de suite** (lecteur ou capteur)

**2. Si HYPOGLYCEMIE (< 0.70 g/L) :**
   - Mange/bois du sucre rapide : jus de fruit, 3 morceaux de sucre, miel, coca
   - Repose-toi 15 min puis remesure
   - Si toujours bas -> recommence

**3. Si HYPERGLYCEMIE (> 3.00 g/L) :**
   - Bois beaucoup d'eau (pas de sucre)
   - Verifie les cetones si possible
   - Appelle ton medecin

**4. Si tu te sens tres mal, NE RESTE PAS SEUL(E). Appelle :**

| Service | Numero |
|---------|--------|
| SAMU Cameroun | **119** |
| Police Secours | **117** |
| Pompiers | **118** |

Previens une personne de confiance.

---

ROLLY continue de t'aider ci-dessous, mais ta securite passe avant tout.

""".trimIndent()

    /**
     * Reponse de mise en garde (symptomes non urgents mais a surveiller).
     */
    fun getWarningResponse(): String = """
### Attention

Tes symptomes ne sont pas urgents mais merite surveillance.
Mesure ta glycemie et repose-toi. Si ca empire, appelle le **119**.

---

""".trimIndent()
}
