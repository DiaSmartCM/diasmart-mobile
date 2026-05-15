package com.diabeto.util

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CalendarContract
import android.util.Log
import androidx.core.content.ContextCompat
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.TimeZone

/**
 * Helper pour ajouter automatiquement les rendez-vous DiaSmart dans le
 * calendrier systeme (Google Calendar, Outlook, calendrier local Android).
 *
 * Strategie :
 *  - Si la permission WRITE_CALENDAR est accordee → insertion directe via
 *    ContentResolver dans le calendrier par defaut (rapide, transparent).
 *  - Sinon → fallback Intent.ACTION_INSERT qui ouvre l'app calendrier
 *    pre-remplie ; l'utilisateur n'a qu'a taper "Enregistrer".
 *
 * Pas de risque de doublon : on stocke les requestId deja ajoutes dans
 * SharedPreferences. Si l'utilisateur supprime l'evenement, c'est tant pis,
 * on ne le re-ajoutera pas (comportement attendu).
 */
object CalendarHelper {

    private const val TAG = "CalendarHelper"
    private const val PREFS = "diasmart_calendar_sync"
    private const val KEY_ADDED_RDV = "added_rdv_ids"

    fun hasPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.WRITE_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Insere un evenement dans le calendrier par defaut.
     *
     * @param requestId identifiant unique de la demande RDV — sert d'idempotence
     * @param title     titre court de l'evenement (ex: "Consultation Dr X")
     * @param dateHeureIso date+heure au format ISO local (ex: "2026-05-20T14:30")
     * @param dureeMinutes duree en minutes
     * @param description notes (motif, recommandations...)
     * @param location lieu (cabinet, "Teleconsultation"...)
     *
     * @return true si l'evenement a ete ajoute (ou etait deja la), false si erreur
     */
    fun addOrUpdateEvent(
        context: Context,
        requestId: String,
        title: String,
        dateHeureIso: String,
        dureeMinutes: Int,
        description: String = "",
        location: String = ""
    ): AddResult {
        if (requestId.isBlank()) return AddResult.Error("requestId vide")
        if (isAlreadyAdded(context, requestId)) {
            Log.d(TAG, "RDV $requestId deja dans le calendrier — skip")
            return AddResult.AlreadyAdded
        }

        val startMillis = parseStartMillis(dateHeureIso)
            ?: return AddResult.Error("Date invalide: $dateHeureIso")
        val endMillis = startMillis + dureeMinutes.coerceAtLeast(15) * 60_000L

        return if (hasPermission(context)) {
            insertViaContentResolver(
                context, requestId, title, startMillis, endMillis, description, location
            )
        } else {
            // Pas de permission → ouvre l'app calendrier prefile
            launchInsertIntent(
                context, requestId, title, startMillis, endMillis, description, location
            )
        }
    }

    private fun insertViaContentResolver(
        context: Context,
        requestId: String,
        title: String,
        startMillis: Long,
        endMillis: Long,
        description: String,
        location: String
    ): AddResult {
        val calendarId = findDefaultCalendarId(context)
            ?: return AddResult.Error("Aucun calendrier configure sur l'appareil")

        val tz = TimeZone.getDefault().id
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, title.take(120))
            put(CalendarContract.Events.DESCRIPTION, description.take(2000))
            put(CalendarContract.Events.EVENT_LOCATION, location.take(200))
            put(CalendarContract.Events.DTSTART, startMillis)
            put(CalendarContract.Events.DTEND, endMillis)
            put(CalendarContract.Events.EVENT_TIMEZONE, tz)
            put(CalendarContract.Events.HAS_ALARM, 1)
            put(CalendarContract.Events.ALL_DAY, 0)
        }
        return try {
            val uri = context.contentResolver.insert(
                CalendarContract.Events.CONTENT_URI, values
            ) ?: return AddResult.Error("Insertion calendrier echouee")
            val eventId = ContentUris.parseId(uri)
            // Rappel 1h avant
            val reminder = ContentValues().apply {
                put(CalendarContract.Reminders.EVENT_ID, eventId)
                put(CalendarContract.Reminders.MINUTES, 60)
                put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
            }
            runCatching {
                context.contentResolver.insert(
                    CalendarContract.Reminders.CONTENT_URI, reminder
                )
            }
            markAdded(context, requestId)
            Log.d(TAG, "Evenement #$eventId ajoute au calendrier $calendarId")
            AddResult.Added(eventId)
        } catch (e: SecurityException) {
            Log.w(TAG, "Permission revoked", e)
            AddResult.Error("Permission revoquee — ouvrez les Parametres pour autoriser le calendrier")
        } catch (e: Exception) {
            Log.e(TAG, "ContentResolver insert failed", e)
            AddResult.Error("Erreur insertion : ${e::class.java.simpleName}")
        }
    }

    /**
     * Fallback Intent — ouvre l'app calendrier avec les champs pre-remplis.
     * L'utilisateur tape "Enregistrer" pour confirmer.
     */
    private fun launchInsertIntent(
        context: Context,
        requestId: String,
        title: String,
        startMillis: Long,
        endMillis: Long,
        description: String,
        location: String
    ): AddResult {
        return try {
            val intent = Intent(Intent.ACTION_INSERT).apply {
                data = CalendarContract.Events.CONTENT_URI
                putExtra(CalendarContract.Events.TITLE, title)
                putExtra(CalendarContract.Events.DESCRIPTION, description)
                putExtra(CalendarContract.Events.EVENT_LOCATION, location)
                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMillis)
                putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endMillis)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            // On ne peut pas confirmer que l'utilisateur a sauvegarde — on
            // memorise quand meme l'ID pour eviter de relancer l'Intent en
            // boucle. Si l'utilisateur ferme sans sauvegarder, il pourra
            // relancer manuellement depuis le bouton "Ajouter au calendrier".
            markAdded(context, requestId)
            AddResult.IntentLaunched
        } catch (e: Exception) {
            Log.e(TAG, "launchInsertIntent failed", e)
            AddResult.Error("Aucune app calendrier installee")
        }
    }

    private fun findDefaultCalendarId(context: Context): Long? {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.IS_PRIMARY,
            CalendarContract.Calendars.ACCOUNT_NAME
        )
        return try {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                "${CalendarContract.Calendars.VISIBLE} = 1",
                null,
                "${CalendarContract.Calendars.IS_PRIMARY} DESC"
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getLong(0) else null
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "READ_CALENDAR permission missing", e)
            null
        }
    }

    private fun parseStartMillis(dateHeureIso: String): Long? {
        // Format attendu : "2026-05-20T14:30:00" (LocalDateTime) ou "2026-05-20T14:30"
        return runCatching {
            val cleaned = if (dateHeureIso.length == 16) dateHeureIso + ":00" else dateHeureIso
            val ldt = LocalDateTime.parse(cleaned, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            ldt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }.getOrNull()
    }

    private fun isAlreadyAdded(context: Context, requestId: String): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY_ADDED_RDV, emptySet())
            ?.contains(requestId) == true

    private fun markAdded(context: Context, requestId: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val current = prefs.getStringSet(KEY_ADDED_RDV, emptySet())?.toMutableSet()
            ?: mutableSetOf()
        current.add(requestId)
        prefs.edit().putStringSet(KEY_ADDED_RDV, current).apply()
    }

    /** Reset cache si l'utilisateur veut re-synchroniser tout. */
    fun resetSyncCache(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(KEY_ADDED_RDV).apply()
    }

    sealed class AddResult {
        data class Added(val eventId: Long) : AddResult()
        object AlreadyAdded : AddResult()
        object IntentLaunched : AddResult()
        data class Error(val message: String) : AddResult()
    }
}
