package com.diabeto.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.diabeto.MainActivity
import com.diabeto.R
import com.diabeto.data.database.DiabetoDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter

/**
 * Widget Android affichant la glycémie actuelle + tendance
 * sur l'écran d'accueil
 */
class GlucoseWidgetProvider : AppWidgetProvider() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { appWidgetId ->
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val widgetIds = appWidgetManager.getAppWidgetIds(
                ComponentName(context, GlucoseWidgetProvider::class.java)
            )
            onUpdate(context, appWidgetManager, widgetIds)
        }
    }

    private fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_glucose)

        // Click ouvre l'app
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

        // Données par défaut
        views.setTextViewText(R.id.widget_glucose_value, "---")
        views.setTextViewText(R.id.widget_trend_text, "Chargement...")
        views.setTextColor(R.id.widget_trend_text, 0xFFB0BEC5.toInt())
        views.setTextViewText(R.id.widget_context, "Appuyez pour ouvrir")
        views.setTextViewText(R.id.widget_time, "")
        appWidgetManager.updateAppWidget(appWidgetId, views)

        // Charger données async
        scope.launch {
            try {
                val db = DiabetoDatabase.getInstance(context)
                val glucoseDao = db.glucoseDao()

                // Récupérer le premier patient (widget simplifié)
                val patients = db.patientDao().getAllPatientsList(
                    com.google.firebase.auth.FirebaseAuth.getInstance()
                        .currentUser?.uid ?: "__aucun__"
                )
                if (patients.isEmpty()) {
                    views.setTextViewText(R.id.widget_glucose_value, "---")
                    views.setTextViewText(R.id.widget_trend_text, "Aucun patient")
                    views.setTextColor(R.id.widget_trend_text, 0xFFB0BEC5.toInt())
                    views.setTextViewText(R.id.widget_context, "Ajoutez un patient")
                    appWidgetManager.updateAppWidget(appWidgetId, views)
                    return@launch
                }

                val patientId = patients.first().id
                val latest = glucoseDao.getLatestReading(patientId)

                if (latest == null) {
                    views.setTextViewText(R.id.widget_glucose_value, "---")
                    views.setTextViewText(R.id.widget_trend_text, "Aucune mesure")
                    views.setTextColor(R.id.widget_trend_text, 0xFFB0BEC5.toInt())
                    views.setTextViewText(R.id.widget_context, "Mesurez votre glycémie")
                    appWidgetManager.updateAppWidget(appWidgetId, views)
                    return@launch
                }

                // Valeur actuelle
                val valeur = latest.valeur.toInt()
                views.setTextViewText(R.id.widget_glucose_value, valeur.toString())

                // Couleur selon valeur
                val valueColor = when {
                    valeur < 70 -> 0xFFFF5252.toInt()   // Rouge - Hypo
                    valeur > 180 -> 0xFFFFAB40.toInt()   // Orange - Hyper
                    else -> 0xFF69F0AE.toInt()            // Vert - Normal
                }
                views.setTextColor(R.id.widget_glucose_value, valueColor)

                // Tendance (basée sur les 6 dernières lectures)
                val recentLectures = glucoseDao.getLecturesByPatientList(patientId, 6)
                val trend = analyzeTrend(recentLectures.map { it.valeur })

                val (trendText, trendColor, trendIcon) = when (trend) {
                    Trend.RISING -> Triple("En hausse ↗", 0xFFFFAB40.toInt(), android.R.drawable.arrow_up_float)
                    Trend.FALLING -> Triple("En baisse ↘", 0xFF42A5F5.toInt(), android.R.drawable.arrow_down_float)
                    Trend.STABLE -> Triple("Stable →", 0xFF69F0AE.toInt(), android.R.drawable.presence_online)
                    Trend.RISING_FAST -> Triple("Hausse rapide ⬆", 0xFFFF5252.toInt(), android.R.drawable.arrow_up_float)
                    Trend.FALLING_FAST -> Triple("Baisse rapide ⬇", 0xFFFF5252.toInt(), android.R.drawable.arrow_down_float)
                }

                views.setTextViewText(R.id.widget_trend_text, trendText)
                views.setTextColor(R.id.widget_trend_text, trendColor)
                views.setImageViewResource(R.id.widget_trend_arrow, trendIcon)

                // Heure dernière mesure
                val timeFormat = DateTimeFormatter.ofPattern("HH:mm")
                views.setTextViewText(
                    R.id.widget_time,
                    latest.dateHeure.format(timeFormat)
                )

                // Contexte
                val dateFormat = DateTimeFormatter.ofPattern("dd/MM HH:mm")
                views.setTextViewText(
                    R.id.widget_context,
                    "${latest.contexte.getDisplayName()} • ${latest.dateHeure.format(dateFormat)}"
                )

                appWidgetManager.updateAppWidget(appWidgetId, views)
            } catch (e: Exception) {
                views.setTextViewText(R.id.widget_trend_text, "Erreur")
                views.setTextColor(R.id.widget_trend_text, 0xFFFF5252.toInt())
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }
    }

    private fun analyzeTrend(values: List<Double>): Trend {
        if (values.size < 3) return Trend.STABLE
        val recent = values.take(3).average()
        val older = values.drop(3).take(3)
        if (older.isEmpty()) return Trend.STABLE
        val avgOlder = older.average()
        val diff = recent - avgOlder
        return when {
            diff > 30 -> Trend.RISING_FAST
            diff > 15 -> Trend.RISING
            diff < -30 -> Trend.FALLING_FAST
            diff < -15 -> Trend.FALLING
            else -> Trend.STABLE
        }
    }

    private enum class Trend {
        RISING, FALLING, STABLE, RISING_FAST, FALLING_FAST
    }

    companion object {
        const val ACTION_REFRESH = "com.diabeto.widget.ACTION_REFRESH"

        /**
         * Mettre à jour le widget depuis n'importe où dans l'app
         */
        fun refreshWidget(context: Context) {
            val intent = Intent(context, GlucoseWidgetProvider::class.java).apply {
                action = ACTION_REFRESH
            }
            context.sendBroadcast(intent)
        }
    }
}
