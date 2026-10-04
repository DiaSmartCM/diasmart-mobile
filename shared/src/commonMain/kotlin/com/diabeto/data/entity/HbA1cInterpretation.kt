package com.diabeto.data.entity

enum class HbA1cInterpretation {
    NORMAL,              // < 5.7%
    PREDIABETE,          // 5.7 - 6.4%
    CIBLE_ATTEINTE,      // 6.5 - 7.0%
    AU_DESSUS_CIBLE,     // 7.0 - 8.0%
    MAUVAIS_CONTROLE,    // 8.0 - 9.0%
    TRES_MAUVAIS_CONTROLE; // > 9.0%

    fun getDisplayName(): String = when (this) {
        NORMAL -> "Normal"
        PREDIABETE -> "Prédiabète"
        CIBLE_ATTEINTE -> "Cible atteinte"
        AU_DESSUS_CIBLE -> "Au-dessus de la cible"
        MAUVAIS_CONTROLE -> "Mauvais contrôle"
        TRES_MAUVAIS_CONTROLE -> "Très mauvais contrôle"
    }

    fun getDescription(): String = when (this) {
        NORMAL -> "Glycémie dans les valeurs normales"
        PREDIABETE -> "Risque de diabète, surveillance recommandée"
        CIBLE_ATTEINTE -> "Bon contrôle du diabète, continuez ainsi"
        AU_DESSUS_CIBLE -> "Ajustement thérapeutique recommandé"
        MAUVAIS_CONTROLE -> "Risque accru de complications, consultez"
        TRES_MAUVAIS_CONTROLE -> "Urgence : consultation médicale nécessaire"
    }
}
