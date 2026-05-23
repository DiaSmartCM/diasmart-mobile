package com.diabeto.data.model

import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v2.1.57 : tests DataSharingConsent — base du modele permission
 * patient↔medecin. Toute regression compromet l'acces medical des
 * 5000+ patients potentiels.
 *
 * Les Firestore rules (v2.1.43) s'appuient sur :
 *   data_sharing/{patientUid_medecinUid}.isActive == true
 * Si le toMap/fromMap rate ou si le pattern docId change, tout casse.
 */
class DataSharingConsentTest {

    @Test
    fun `documentId follows patientUid_medecinUid pattern`() {
        val c = DataSharingConsent(patientUid = "p123", medecinUid = "m456")
        assertEquals("p123_m456", c.documentId)
    }

    @Test
    fun `documentId order is patient first not medecin first`() {
        // Crucial : Firestore rules expectent {patientUid}_{medecinUid}.
        // Si on inverse, isLinkedDoctorOf() echoue silencieusement.
        val c = DataSharingConsent(patientUid = "P", medecinUid = "M")
        assertEquals("P_M", c.documentId)
        assertFalse("M_P" == c.documentId)
    }

    @Test
    fun `toMap then fromMap preserves all fields`() {
        val original = DataSharingConsent(
            patientUid = "p1",
            medecinUid = "m1",
            patientNom = "Alice",
            medecinNom = "Dr Bob",
            isActive = true,
            status = ConsentStatus.ACCEPTED,
            grantedAt = Timestamp(1000L, 0),
            revokedAt = Timestamp(2000L, 0),
            shareGlucose = true,
            shareHbA1c = false,
            shareMedications = true,
            shareBodyMetrics = false
        )
        val map = original.toMap()
        val restored = DataSharingConsent.fromMap(map)
        assertEquals(original.patientUid, restored.patientUid)
        assertEquals(original.medecinUid, restored.medecinUid)
        assertEquals(original.patientNom, restored.patientNom)
        assertEquals(original.medecinNom, restored.medecinNom)
        assertEquals(original.isActive, restored.isActive)
        assertEquals(original.status, restored.status)
        assertEquals(original.shareGlucose, restored.shareGlucose)
        assertEquals(original.shareHbA1c, restored.shareHbA1c)
        assertEquals(original.shareMedications, restored.shareMedications)
        assertEquals(original.shareBodyMetrics, restored.shareBodyMetrics)
    }

    @Test
    fun `default isActive is FALSE`() {
        // Securite : sans accord explicite du patient, pas de partage.
        val c = DataSharingConsent(patientUid = "p", medecinUid = "m")
        assertFalse(c.isActive)
        assertEquals(ConsentStatus.PENDING, c.status)
    }

    @Test
    fun `default share permissions are all TRUE`() {
        // Si le patient accepte, il accepte par defaut le partage complet.
        // Plus tard, on permettra de partage granulaire (V2).
        val c = DataSharingConsent(patientUid = "p", medecinUid = "m")
        assertTrue(c.shareGlucose)
        assertTrue(c.shareHbA1c)
        assertTrue(c.shareMedications)
        assertTrue(c.shareBodyMetrics)
    }

    @Test
    fun `fromMap with corrupted status fallback uses isActive heuristic`() {
        // Si on a une vieille version sans le champ "status" mais avec isActive=true,
        // on doit deduire ACCEPTED (compat backward).
        val mapActive = mapOf(
            "patientUid" to "p",
            "medecinUid" to "m",
            "isActive" to true,
            "status" to "BOGUS"
        )
        val c = DataSharingConsent.fromMap(mapActive)
        assertEquals(ConsentStatus.ACCEPTED, c.status)
    }

    @Test
    fun `fromMap with corrupted status and isActive false falls back to PENDING`() {
        val map = mapOf(
            "patientUid" to "p",
            "medecinUid" to "m",
            "isActive" to false,
            "status" to "GARBAGE"
        )
        val c = DataSharingConsent.fromMap(map)
        assertEquals(ConsentStatus.PENDING, c.status)
    }

    @Test
    fun `revokedAt is null on fresh consent`() {
        val c = DataSharingConsent(patientUid = "p", medecinUid = "m")
        assertNull(c.revokedAt)
    }

    @Test
    fun `ConsentStatus has exactly 3 stable values`() {
        assertEquals(3, ConsentStatus.entries.size)
        assertEquals("PENDING", ConsentStatus.entries[0].name)
        assertEquals("ACCEPTED", ConsentStatus.entries[1].name)
        assertEquals("REJECTED", ConsentStatus.entries[2].name)
    }

    @Test
    fun `fromMap empty returns valid skeleton`() {
        val c = DataSharingConsent.fromMap(emptyMap())
        assertNotNull(c)
        assertEquals("", c.patientUid)
        assertFalse(c.isActive)
    }
}
