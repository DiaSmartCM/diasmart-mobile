package com.diabeto.notifications

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Alarmes exactes pour les rappels de traitement et de rendez-vous.
 *
 * Pourquoi ce fichier remplace la planification precedente
 * -------------------------------------------------------
 * Les rappels reposaient sur un PeriodicWorkRequest de huit heures pour les
 * medicaments, d'une heure pour les rendez-vous. Deux defauts en decoulaient :
 *
 *  1. WorkManager est volontairement INEXACT. Il regroupe et differe les
 *     taches, davantage encore en veille profonde. Une prise prevue a 08h00 ne
 *     pouvait donc pas sonner a 08h00 — au mieux dans les huit heures suivant
 *     le reveil de la tache.
 *  2. Le worker ne lisait meme pas `heurePrise`. Il notifiait TOUS les
 *     medicaments actifs a chaque execution, produisant des rafales a des
 *     moments arbitraires plutot qu'un rappel a l'heure prescrite.
 *
 * Un rappel de traitement est une alarme : il se declenche a une heure precise
 * ou il ne sert a rien. On passe donc a AlarmManager, avec une alarme posee par
 * echeance. Les permissions SCHEDULE_EXACT_ALARM et USE_EXACT_ALARM etaient
 * deja declarees au manifeste ; elles n'avaient jamais ete utilisees.
 *
 * Les alarmes ne survivent pas au redemarrage : BootReceiver les repose.
 */
object AlarmScheduler {

    private const val TAG = "AlarmScheduler"

    /** Espaces d'identifiants disjoints, pour ne pas ecraser une alarme d'un autre type. */
    private const val BASE_MEDICAMENT = 100_000
    private const val BASE_RENDEZ_VOUS = 200_000

    const val EXTRA_TYPE = "type"
    const val EXTRA_ID = "id"
    const val EXTRA_TITRE = "titre"
    const val EXTRA_TEXTE = "texte"

    const val TYPE_MEDICAMENT = "medicament"
    const val TYPE_RENDEZ_VOUS = "rendez_vous"

    /** Combien de temps avant un rendez-vous l'alarme se declenche. */
    private const val PREAVIS_RDV_MINUTES = 60L

    private fun manager(context: Context): AlarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /**
     * L'utilisateur peut avoir refuse les alarmes exactes (Android 12+).
     * On le verifie plutot que de laisser l'appel lever une exception.
     */
    fun peutPoserAlarmeExacte(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            manager(context).canScheduleExactAlarms()
        } else true

    private fun intentPour(
        context: Context, type: String, id: Int, titre: String, texte: String,
    ): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = "com.diabeto.ALARME_$type$id"
            putExtra(EXTRA_TYPE, type)
            putExtra(EXTRA_ID, id)
            putExtra(EXTRA_TITRE, titre)
            putExtra(EXTRA_TEXTE, texte)
        }
        return PendingIntent.getBroadcast(
            context, id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun poser(context: Context, quand: LocalDateTime, pi: PendingIntent) {
        val millis = quand.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (millis <= System.currentTimeMillis()) return

        val am = manager(context)
        try {
            if (peutPoserAlarmeExacte(context)) {
                // ...AndAllowWhileIdle : la veille profonde ne doit pas
                // repousser un rappel de traitement.
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
            } else {
                // Repli sans permission : inexact, mais toujours tolerant a la
                // veille. Mieux vaut un rappel approximatif qu'aucun rappel.
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
                Log.w(TAG, "Alarme exacte refusee : repli sur une alarme approximative")
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
            Log.w(TAG, "SecurityException sur alarme exacte : repli", e)
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Medicaments
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Pose la PROCHAINE occurrence d'un traitement.
     *
     * Une seule alarme a la fois, replanifiee par le recepteur apres chaque
     * declenchement : AlarmManager n'offre pas de repetition exacte fiable, et
     * poser des semaines d'alarmes d'avance saturerait le systeme.
     */
    fun programmerMedicament(
        context: Context,
        medicamentId: Long,
        nom: String,
        dosage: String,
        heurePrise: LocalTime,
        dateFin: LocalDate?,
    ) {
        val maintenant = LocalDateTime.now()
        var prochaine = LocalDateTime.of(LocalDate.now(), heurePrise)
        if (!prochaine.isAfter(maintenant)) prochaine = prochaine.plusDays(1)

        // Traitement termine : rien a poser.
        if (dateFin != null && prochaine.toLocalDate().isAfter(dateFin)) {
            annulerMedicament(context, medicamentId)
            return
        }

        val id = BASE_MEDICAMENT + medicamentId.toInt()
        poser(
            context, prochaine,
            intentPour(
                context, TYPE_MEDICAMENT, id,
                titre = "Rappel de traitement",
                texte = "$nom $dosage — c'est l'heure de votre prise.",
            ),
        )
        Log.d(TAG, "Medicament $medicamentId : alarme posee pour $prochaine")
    }

    fun annulerMedicament(context: Context, medicamentId: Long) {
        val id = BASE_MEDICAMENT + medicamentId.toInt()
        manager(context).cancel(intentPour(context, TYPE_MEDICAMENT, id, "", ""))
    }

    // ─────────────────────────────────────────────────────────────────────
    // Rendez-vous
    // ─────────────────────────────────────────────────────────────────────

    fun programmerRendezVous(
        context: Context,
        rdvId: Long,
        titre: String,
        dateHeure: LocalDateTime,
        lieu: String,
    ) {
        val quand = dateHeure.minusMinutes(PREAVIS_RDV_MINUTES)
        if (quand.isBefore(LocalDateTime.now())) return

        val id = BASE_RENDEZ_VOUS + rdvId.toInt()
        val heure = "%02d:%02d".format(dateHeure.hour, dateHeure.minute)
        val texte = buildString {
            append("$titre a $heure")
            if (lieu.isNotBlank()) append(" — $lieu")
        }
        poser(
            context, quand,
            intentPour(context, TYPE_RENDEZ_VOUS, id, "Rendez-vous dans 1 heure", texte),
        )
        Log.d(TAG, "RDV $rdvId : alarme posee pour $quand")
    }

    fun annulerRendezVous(context: Context, rdvId: Long) {
        val id = BASE_RENDEZ_VOUS + rdvId.toInt()
        manager(context).cancel(intentPour(context, TYPE_RENDEZ_VOUS, id, "", ""))
    }

    /**
     * Ouvre l'ecran systeme d'autorisation des alarmes exactes.
     *
     * Expliquer a l'utilisateur ou cliquer dans les reglages Android echoue
     * presque toujours : le chemin change selon le constructeur. Autant l'y
     * amener directement.
     */
    fun ouvrirReglageAlarmes(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        return try {
            context.startActivity(
                Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                    .setData(android.net.Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            true
        } catch (e: Exception) {
            Log.w(TAG, "Ecran d'autorisation des alarmes indisponible", e)
            // Repli : la fiche de l'application, d'ou l'utilisateur peut
            // atteindre le reglage quel que soit le constructeur.
            try {
                context.startActivity(
                    Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData(android.net.Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                true
            } catch (e2: Exception) {
                false
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Diagnostic
    // ─────────────────────────────────────────────────────────────────────

    /**
     * v2.1.87 : etat reel du systeme d'alarme, en clair.
     *
     * Trois versions ont ete publiees en devinant pourquoi les rappels ne
     * sonnaient pas. Un rapport lisible depuis l'ecran coute moins cher qu'un
     * aller-retour de plus : il dit ce que le systeme autorise reellement sur
     * CET appareil, plutot que ce que le code espere.
     */
    fun diagnostic(context: Context): String = buildString {
        val am = manager(context)

        appendLine("Android : API ${Build.VERSION.SDK_INT} (${Build.MANUFACTURER} ${Build.MODEL})")

        val exact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            am.canScheduleExactAlarms() else true
        appendLine("Alarmes exactes : " + if (exact) "autorisees" else "REFUSEES")
        if (!exact) {
            appendLine("  -> Parametres Android > Applications > DiaSmart >")
            appendLine("     Alarmes et rappels : autoriser.")
        }

        val nm = context.getSystemService(NotificationManager::class.java)
        appendLine("Notifications : " + if (nm.areNotificationsEnabled()) "activees" else "BLOQUEES")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val canal = nm.getNotificationChannel(NotificationHelper.CHANNEL_MEDICAMENTS)
            appendLine("Canal traitements : " + when {
                canal == null -> "ABSENT"
                canal.importance == NotificationManager.IMPORTANCE_NONE -> "DESACTIVE par l'utilisateur"
                else -> "importance ${canal.importance}"
            })
        }

        // v2.1.89 : la cause la plus frequente du silence n'est ni l'alarme ni
        // la notification, mais la mise en veille de l'application par le
        // systeme. On l'affiche donc dans le meme rapport.
        append(com.diabeto.util.OptimisationBatterie.diagnostic(context))

        // Une alarme deja posee prouve que le mecanisme fonctionne bout en bout.
        val dejaPosee = PendingIntent.getBroadcast(
            context, BASE_MEDICAMENT,
            Intent(context, AlarmReceiver::class.java)
                .setAction("com.diabeto.ALARME_$TYPE_MEDICAMENT$BASE_MEDICAMENT"),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) != null
        appendLine("Alarme de test deja posee : " + if (dejaPosee) "oui" else "non")
    }

    /**
     * Pose une alarme dans une minute et renvoie ce qui s'est reellement passe.
     * Permet de distinguer un refus systeme d'un blocage constructeur.
     */
    fun testerDansUneMinute(context: Context): String {
        return try {
            val quand = LocalDateTime.now().plusMinutes(1)
            val id = BASE_MEDICAMENT + 9999
            poser(
                context, quand,
                intentPour(
                    context, TYPE_MEDICAMENT, id,
                    titre = "Test d'alarme DiaSmart",
                    texte = "Si vous lisez ceci, les rappels fonctionnent sur cet appareil.",
                ),
            )
            val mode = if (peutPoserAlarmeExacte(context)) "exacte" else "approximative"
            "Alarme $mode posee pour dans 1 minute. Verrouillez l'ecran et attendez."
        } catch (e: Exception) {
            "Echec : ${e.javaClass.simpleName} — ${e.message}"
        }
    }
}
