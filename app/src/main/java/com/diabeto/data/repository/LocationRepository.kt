package com.diabeto.data.repository

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Recupere la position GPS courante de l'appareil via Google Play Services
 * (FusedLocationProviderClient). Non-bloquant, avec timeout implicite par l'API.
 *
 * Utilisation :
 *  - Medecin : capture sa position lors de l'edition du profil
 *  - Patient : capture sa position au lancement de la recherche de medecin
 *    pour trier par distance.
 */
@Singleton
class LocationRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    data class GeoPoint(
        val latitude: Double,
        val longitude: Double,
        val ville: String = "",
        val adresse: String = ""
    )

    fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    /**
     * Retourne la position courante, ou null si:
     *  - Pas de permission accordee
     *  - GPS desactive / pas de fix rapide possible
     *  - Erreur API
     * Inclut le reverse-geocoding best-effort pour remplir ville/adresse.
     */
    @SuppressLint("MissingPermission") // on verifie avant
    suspend fun getCurrentLocation(): GeoPoint? {
        if (!hasLocationPermission()) return null

        val client = LocationServices.getFusedLocationProviderClient(context)
        val loc: Location? = suspendCancellableCoroutine { cont ->
            client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resume(null) }
        }
        if (loc == null) return null

        val (ville, adresse) = reverseGeocode(loc.latitude, loc.longitude)
        return GeoPoint(loc.latitude, loc.longitude, ville, adresse)
    }

    /** Reverse-geocoding synchrone best-effort (ville, adresse courte). Ignore les erreurs. */
    private fun reverseGeocode(lat: Double, lon: Double): Pair<String, String> {
        return try {
            val geocoder = Geocoder(context, Locale.getDefault())
            @Suppress("DEPRECATION")
            val results = geocoder.getFromLocation(lat, lon, 1)
            val addr = results?.firstOrNull() ?: return "" to ""
            val ville = addr.locality ?: addr.subAdminArea ?: ""
            val adresse = listOfNotNull(
                addr.thoroughfare,
                addr.subLocality,
                addr.locality
            ).distinct().joinToString(", ")
            ville to adresse
        } catch (_: Exception) {
            "" to ""
        }
    }
}
