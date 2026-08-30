package com.diabeto.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log

/**
 * Exemption d'optimisation de batterie et reglages constructeur.
 *
 * Pourquoi ce fichier existe
 * --------------------------
 * Le podometre, les alarmes de traitement et la capture GPS reposent tous sur
 * la capacite de l'application a travailler ecran eteint. Android suspend par
 * defaut ce travail pour economiser la batterie, et les surcouches
 * constructeurs vont beaucoup plus loin : Xiaomi, Oppo, Realme, Vivo, Huawei et
 * Samsung ferment purement et simplement les applications qui ne figurent pas
 * dans leur liste blanche, service de premier plan compris.
 *
 * Aucune quantite de code ne contourne cela — c'est une decision du systeme.
 * La seule voie est de demander l'exemption a l'utilisateur, puis de le
 * conduire au reglage constructeur, qui n'est atteignable par aucun chemin
 * standard.
 *
 * D'ou les deux fonctions distinctes : [demanderExemption] ouvre la boite de
 * dialogue Android officielle, [ouvrirReglagesConstructeur] tente l'ecran
 * proprietaire quand il existe.
 */
object OptimisationBatterie {

    private const val TAG = "OptimBatterie"

    /** L'application est-elle deja exemptee ? */
    fun estExemptee(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            pm.isIgnoringBatteryOptimizations(context.packageName)
        } else true

    /**
     * Ouvre la demande d'exemption Android.
     *
     * On passe par ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, qui affiche une
     * boite de dialogue ou l'utilisateur accepte en un geste. En cas d'echec —
     * certains constructeurs retirent cet ecran — on retombe sur la liste
     * complete, ou l'application se cherche a la main.
     */
    fun demanderExemption(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false
        if (estExemptee(context)) return true
        return try {
            context.startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    .setData(Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            true
        } catch (e: Exception) {
            Log.w(TAG, "Demande directe indisponible, repli sur la liste", e)
            try {
                context.startActivity(
                    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                true
            } catch (e2: Exception) {
                Log.w(TAG, "Aucun ecran d'optimisation disponible", e2)
                false
            }
        }
    }

    /**
     * Ecrans proprietaires de demarrage automatique.
     *
     * Ces composants ne sont pas documentes et changent d'une version de
     * surcouche a l'autre. On les tente dans l'ordre et on s'arrete au premier
     * qui s'ouvre ; si aucun ne repond, on ouvre la fiche de l'application, d'ou
     * l'utilisateur peut toujours atteindre les reglages de batterie.
     */
    private val ECRANS_CONSTRUCTEUR = listOf(
        // Xiaomi / Redmi / POCO
        "com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity",
        // Oppo / Realme / OnePlus (ColorOS, realme UI)
        "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
        "com.coloros.safecenter" to "com.coloros.safecenter.startupapp.StartupAppListActivity",
        "com.oppo.safe" to "com.oppo.safe.permission.startup.StartupAppListActivity",
        // Vivo (Funtouch OS)
        "com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
        "com.iqoo.secure" to "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity",
        // Huawei / Honor
        "com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
        "com.huawei.systemmanager" to "com.huawei.systemmanager.optimize.process.ProtectActivity",
        // Samsung
        "com.samsung.android.lool" to "com.samsung.android.sm.ui.battery.BatteryActivity",
        // Asus, Letv, Honor divers
        "com.asus.mobilemanager" to "com.asus.mobilemanager.entry.FunctionActivity",
        "com.letv.android.letvsafe" to "com.letv.android.letvsafe.AutobootManageActivity",
    )

    /** @return true si un ecran a pu etre ouvert. */
    fun ouvrirReglagesConstructeur(context: Context): Boolean {
        for ((paquet, classe) in ECRANS_CONSTRUCTEUR) {
            try {
                val intent = Intent().setComponent(ComponentName(paquet, classe))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (context.packageManager.resolveActivity(intent, 0) != null) {
                    context.startActivity(intent)
                    return true
                }
            } catch (_: Exception) {
                // Composant absent sur cet appareil : on essaie le suivant.
            }
        }
        // Repli universel : la fiche de l'application.
        return try {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            true
        } catch (e: Exception) {
            Log.w(TAG, "Aucun ecran de reglage accessible", e)
            false
        }
    }

    /**
     * Marques connues pour fermer les applications en arriere-plan, meme
     * exemptees. Sert a n'afficher le conseil constructeur que la ou il sert.
     */
    fun constructeurRestrictif(): Boolean {
        val m = Build.MANUFACTURER.lowercase()
        return listOf(
            "xiaomi", "redmi", "poco", "oppo", "realme", "oneplus",
            "vivo", "iqoo", "huawei", "honor", "samsung", "meizu",
            "asus", "letv", "tecno", "infinix", "itel"
        ).any { m.contains(it) }
    }

    /** Ligne de diagnostic, reprise dans le rapport des rappels. */
    fun diagnostic(context: Context): String = buildString {
        appendLine("Optimisation batterie : " +
            if (estExemptee(context)) "application exemptee"
            else "APPLICATION SOUMISE aux restrictions")
        if (constructeurRestrictif()) {
            appendLine("Constructeur ${Build.MANUFACTURER} : ferme les applications " +
                "en arriere-plan meme exemptees. Le demarrage automatique doit " +
                "etre autorise dans ses propres reglages.")
        }
    }
}
