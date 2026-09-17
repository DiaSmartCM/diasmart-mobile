package com.diabeto.data.model

import com.google.firebase.Timestamp

/**
 * Dossier numerique d'un patient chez UN medecin.
 *
 * Un dossier existe pour chaque lien medecin-patient et porte le meme
 * identifiant que ce lien (`data_sharing/{patientUid}_{medecinUid}`) : les
 * regles Firestore retrouvent ainsi le lien sans lecture supplementaire, et deux
 * medecins d'un meme patient tiennent chacun leur propre dossier.
 *
 * Firestore : `dossiers/{patientUid}_{medecinUid}` et ses fiches dans la
 * sous-collection `entrees`.
 */
data class DossierMedical(
    val patientUid: String = "",
    val medecinUid: String = "",
    val patientNom: String = "",
    val medecinNom: String = "",
    val createdAt: Timestamp = Timestamp.now(),
    val updatedAt: Timestamp = Timestamp.now()
) {
    val id: String get() = identifiant(patientUid, medecinUid)

    fun toMap(): Map<String, Any?> = mapOf(
        "patientUid" to patientUid,
        "medecinUid" to medecinUid,
        "patientNom" to patientNom,
        "medecinNom" to medecinNom,
        "createdAt" to createdAt,
        "updatedAt" to updatedAt
    )

    companion object {
        fun identifiant(patientUid: String, medecinUid: String) = "${patientUid}_${medecinUid}"

        fun fromMap(map: Map<String, Any?>) = DossierMedical(
            patientUid = map["patientUid"] as? String ?: "",
            medecinUid = map["medecinUid"] as? String ?: "",
            patientNom = map["patientNom"] as? String ?: "",
            medecinNom = map["medecinNom"] as? String ?: "",
            createdAt = map["createdAt"] as? Timestamp ?: Timestamp.now(),
            updatedAt = map["updatedAt"] as? Timestamp ?: Timestamp.now()
        )
    }
}

/** Les six parties du dossier, dans l'ordre ou le medecin les consulte. */
enum class SectionDossier(val libelle: String) {
    ADMINISTRATIF("Administratif"),
    ANTECEDENTS("Antécédents"),
    COMPTES_RENDUS("Comptes rendus"),
    EXAMENS("Examens"),
    TRAITEMENTS("Traitements"),
    SUIVI("Suivi")
}

/** Un champ de saisie d'une fiche. `long` = zone de texte sur plusieurs lignes. */
data class ChampDossier(val cle: String, val libelle: String, val long: Boolean = false)

/**
 * Chaque type de fiche fixe ses champs. Ajouter un type ici suffit : le
 * formulaire, l'affichage et l'enregistrement s'en servent directement.
 */
enum class TypeEntree(
    val section: SectionDossier,
    val libelle: String,
    val champs: List<ChampDossier>
) {
    // ── Administratif ──
    IDENTITE(SectionDossier.ADMINISTRATIF, "Identité et coordonnées", listOf(
        ChampDossier("nom", "Nom et prénom"),
        ChampDossier("naissance", "Date de naissance"),
        ChampDossier("sexe", "Sexe"),
        ChampDossier("profession", "Profession"),
        ChampDossier("adresse", "Adresse", long = true),
        ChampDossier("telephone", "Téléphone"),
        ChampDossier("email", "E-mail")
    )),
    CONTACT_URGENCE(SectionDossier.ADMINISTRATIF, "Contact d'urgence", listOf(
        ChampDossier("nom", "Nom"),
        ChampDossier("lien", "Lien avec le patient"),
        ChampDossier("telephone", "Téléphone")
    )),
    COUVERTURE_SOCIALE(SectionDossier.ADMINISTRATIF, "Couverture sociale", listOf(
        ChampDossier("organisme", "Organisme ou assurance"),
        ChampDossier("numero", "Numéro d'assuré"),
        ChampDossier("validite", "Valable jusqu'au"),
        ChampDossier("prise_en_charge", "Taux ou détail de prise en charge")
    )),

    // ── Antecedents ──
    GROUPE_SANGUIN(SectionDossier.ANTECEDENTS, "Groupe sanguin", listOf(
        ChampDossier("groupe", "Groupe et rhésus (ex. O+)")
    )),
    ALLERGIE(SectionDossier.ANTECEDENTS, "Allergie", listOf(
        ChampDossier("allergene", "Allergène"),
        ChampDossier("reaction", "Réaction observée"),
        ChampDossier("gravite", "Gravité")
    )),
    ANTECEDENT_MEDICAL(SectionDossier.ANTECEDENTS, "Antécédent médical", listOf(
        ChampDossier("pathologie", "Maladie"),
        ChampDossier("annee", "Année ou âge"),
        ChampDossier("commentaire", "Commentaire", long = true)
    )),
    ANTECEDENT_CHIRURGICAL(SectionDossier.ANTECEDENTS, "Antécédent chirurgical", listOf(
        ChampDossier("intervention", "Intervention"),
        ChampDossier("annee", "Année"),
        ChampDossier("commentaire", "Commentaire", long = true)
    )),
    ANTECEDENT_FAMILIAL(SectionDossier.ANTECEDENTS, "Antécédent familial", listOf(
        ChampDossier("parente", "Lien de parenté"),
        ChampDossier("pathologie", "Maladie")
    )),
    FACTEUR_RISQUE(SectionDossier.ANTECEDENTS, "Facteur de risque", listOf(
        ChampDossier("facteur", "Facteur (tabac, sédentarité, HTA...)"),
        ChampDossier("detail", "Détail", long = true)
    )),

    // ── Comptes rendus ──
    CONSULTATION(SectionDossier.COMPTES_RENDUS, "Consultation", listOf(
        ChampDossier("motif", "Motif"),
        ChampDossier("examen", "Examen clinique", long = true),
        ChampDossier("conclusion", "Conclusion", long = true),
        ChampDossier("conduite", "Conduite à tenir", long = true)
    )),
    DIAGNOSTIC(SectionDossier.COMPTES_RENDUS, "Diagnostic", listOf(
        ChampDossier("diagnostic", "Diagnostic"),
        ChampDossier("cim10", "Code CIM-10"),
        ChampDossier("statut", "Statut (actif, résolu...)")
    )),
    HOSPITALISATION(SectionDossier.COMPTES_RENDUS, "Résumé d'hospitalisation", listOf(
        ChampDossier("etablissement", "Établissement"),
        ChampDossier("motif", "Motif"),
        ChampDossier("duree", "Durée du séjour"),
        ChampDossier("resume", "Résumé", long = true)
    )),
    COMPTE_RENDU_OPERATOIRE(SectionDossier.COMPTES_RENDUS, "Compte rendu opératoire", listOf(
        ChampDossier("intervention", "Intervention"),
        ChampDossier("operateur", "Opérateur"),
        ChampDossier("compte_rendu", "Compte rendu", long = true)
    )),

    // ── Examens ──
    BIOLOGIE(SectionDossier.EXAMENS, "Analyse de biologie", listOf(
        ChampDossier("examen", "Examen (HbA1c, créatinine...)"),
        ChampDossier("resultat", "Résultat"),
        ChampDossier("unite", "Unité"),
        ChampDossier("normes", "Valeurs de référence"),
        ChampDossier("interpretation", "Interprétation", long = true)
    )),
    IMAGERIE(SectionDossier.EXAMENS, "Imagerie médicale", listOf(
        ChampDossier("type", "Type (radiographie, échographie, IRM...)"),
        ChampDossier("region", "Région explorée"),
        ChampDossier("conclusion", "Conclusion", long = true)
    )),

    // ── Traitements ──
    ORDONNANCE(SectionDossier.TRAITEMENTS, "Ordonnance", listOf(
        ChampDossier("prescription", "Médicaments et posologie", long = true),
        ChampDossier("duree", "Durée du traitement"),
        ChampDossier("instructions", "Instructions", long = true)
    )),
    MEDICATION_EN_COURS(SectionDossier.TRAITEMENTS, "Médicament en cours", listOf(
        ChampDossier("medicament", "Médicament"),
        ChampDossier("posologie", "Posologie"),
        ChampDossier("debut", "Début"),
        ChampDossier("fin", "Fin prévue")
    )),
    VACCINATION(SectionDossier.TRAITEMENTS, "Vaccination", listOf(
        ChampDossier("vaccin", "Vaccin"),
        ChampDossier("dose", "Dose ou rappel"),
        ChampDossier("lot", "Numéro de lot")
    )),

    // ── Suivi ──
    SURVEILLANCE(SectionDossier.SUIVI, "Feuille de surveillance", listOf(
        ChampDossier("parametre", "Paramètre (tension, poids, glycémie...)"),
        ChampDossier("valeur", "Valeur"),
        ChampDossier("observation", "Observation", long = true)
    )),
    PLAN_DE_SOINS(SectionDossier.SUIVI, "Plan de soins", listOf(
        ChampDossier("objectifs", "Objectifs", long = true),
        ChampDossier("actions", "Actions prévues", long = true),
        ChampDossier("echeance", "Échéance")
    ));

    companion object {
        fun deSection(section: SectionDossier) = entries.filter { it.section == section }
        fun depuisNom(nom: String?) = entries.firstOrNull { it.name == nom }
    }
}

/** Une fiche du dossier. `date` au format JJ/MM/AAAA, telle que saisie. */
data class EntreeDossier(
    val id: String = "",
    val patientUid: String = "",
    val medecinUid: String = "",
    val type: TypeEntree = TypeEntree.CONSULTATION,
    val date: String = "",
    val champs: Map<String, String> = emptyMap(),
    val visiblePatient: Boolean = true,
    val auteurUid: String = "",
    val auteurNom: String = "",
    val createdAt: Timestamp = Timestamp.now(),
    val updatedAt: Timestamp = Timestamp.now()
) {
    /** Premier champ rempli : sert de titre a la carte. */
    val titre: String
        get() = type.champs.firstNotNullOfOrNull { champs[it.cle]?.takeIf { v -> v.isNotBlank() } }
            ?: type.libelle

    fun toMap(): Map<String, Any?> = mapOf(
        "patientUid" to patientUid,
        "medecinUid" to medecinUid,
        "section" to type.section.name,
        "type" to type.name,
        "date" to date,
        "champs" to champs,
        "visiblePatient" to visiblePatient,
        "auteurUid" to auteurUid,
        "auteurNom" to auteurNom,
        "createdAt" to createdAt,
        "updatedAt" to updatedAt
    )

    companion object {
        fun fromMap(id: String, map: Map<String, Any?>): EntreeDossier? {
            val type = TypeEntree.depuisNom(map["type"] as? String) ?: return null
            @Suppress("UNCHECKED_CAST")
            val champs = (map["champs"] as? Map<String, Any?>)
                ?.mapValues { it.value?.toString().orEmpty() }
                .orEmpty()
            return EntreeDossier(
                id = id,
                patientUid = map["patientUid"] as? String ?: "",
                medecinUid = map["medecinUid"] as? String ?: "",
                type = type,
                date = map["date"] as? String ?: "",
                champs = champs,
                visiblePatient = map["visiblePatient"] as? Boolean ?: false,
                auteurUid = map["auteurUid"] as? String ?: "",
                auteurNom = map["auteurNom"] as? String ?: "",
                createdAt = map["createdAt"] as? Timestamp ?: Timestamp.now(),
                updatedAt = map["updatedAt"] as? Timestamp ?: Timestamp.now()
            )
        }
    }
}
