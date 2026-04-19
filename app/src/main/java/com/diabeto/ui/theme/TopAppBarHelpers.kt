package com.diabeto.ui.theme

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Couleurs harmonisees pour tous les TopAppBar de l'app.
 *
 * Pourquoi une helper ?
 * Avant, on ecrivait `containerColor = Surface` (val hardcode blanc #FFFFFF)
 * => en mode sombre, le titre lavande clair (#E8E5FF) etait quasi invisible
 *    sur fond blanc fixe.
 *
 * Ici on utilise MaterialTheme.colorScheme.surface qui suit le theme :
 *   - Clair  : #FFFFFF         (titre sombre #1A1A2E)
 *   - Sombre : #141428         (titre tres lumineux ci-dessous)
 *
 * En plus, on booste titleContentColor / navigationIconContentColor a
 * des valeurs plus intenses que les defauts M3 pour ameliorer la lisibilite
 * des titres d'ecrans (medecin comme patient).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun diaSmartTopAppBarColors(): TopAppBarColors {
    val isDark = LocalIsDarkTheme.current
    // Texte plus intense qu'avant pour meilleure lisibilite
    val titleColor = if (isDark) Color(0xFFFFFFFF) else Color(0xFF0F0F24)
    val iconColor = if (isDark) Color(0xFFF5F2FF) else Color(0xFF1A1A2E)
    return TopAppBarDefaults.topAppBarColors(
        containerColor = MaterialTheme.colorScheme.surface,
        scrolledContainerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = titleColor,
        navigationIconContentColor = iconColor,
        actionIconContentColor = iconColor,
    )
}
