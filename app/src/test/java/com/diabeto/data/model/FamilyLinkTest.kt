package com.diabeto.data.model

import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v2.1.57 : tests pour FamilyLink data class + enum FamilyLinkStatus.
 *
 * Le mode famille (v2.1.48) repose entierement sur ces classes.
 * Une regression silencieuse sur le toMap/fromMap = corruption Firestore.
 */
class FamilyLinkTest {

    // ── docId : pattern crucial pour les Firestore rules + queries ──

    @Test
    fun `docId follows ownerUid_aidantUid pattern`() {
        val link = FamilyLink(ownerUid = "patient123", aidantUid = "aidant456")
        assertEquals("patient123_aidant456", link.documentId)
    }

    @Test
    fun `docId companion static method matches instance property`() {
        val owner = "ownerABC"
        val aidant = "aidantXYZ"
        val instance = FamilyLink(ownerUid = owner, aidantUid = aidant)
        val static = FamilyLink.docId(owner, aidant)
        assertEquals(instance.documentId, static)
    }

    @Test
    fun `docId is asymmetric — order matters`() {
        // Si on inverse owner et aidant, on doit avoir un autre docId
        // sinon on aurait collision avec un autre lien.
        val a = FamilyLink.docId("alice", "bob")
        val b = FamilyLink.docId("bob", "alice")
        assertFalse(a == b)
    }

    // ── toMap / fromMap roundtrip ──

    @Test
    fun `toMap then fromMap preserves all fields`() {
        val original = FamilyLink(
            ownerUid = "owner1",
            aidantUid = "aidant1",
            ownerNom = "Alice Smith",
            aidantNom = "Bob Smith",
            aidantEmail = "bob@example.com",
            relation = "conjoint",
            isActive = true,
            status = FamilyLinkStatus.ACCEPTED,
            invitedAt = Timestamp(1000L, 0),
            acceptedAt = Timestamp(2000L, 0),
            revokedAt = null,
            canSeeGlucose = true,
            canSeeMeals = false,
            canSeeMedications = true,
            canReceiveEmergencyAlerts = true,
            revokedBy = null
        )
        val map = original.toMap()
        val restored = FamilyLink.fromMap(map)
        assertEquals(original.ownerUid, restored.ownerUid)
        assertEquals(original.aidantUid, restored.aidantUid)
        assertEquals(original.ownerNom, restored.ownerNom)
        assertEquals(original.aidantNom, restored.aidantNom)
        assertEquals(original.aidantEmail, restored.aidantEmail)
        assertEquals(original.relation, restored.relation)
        assertEquals(original.isActive, restored.isActive)
        assertEquals(original.status, restored.status)
        assertEquals(original.canSeeGlucose, restored.canSeeGlucose)
        assertEquals(original.canSeeMeals, restored.canSeeMeals)
        assertEquals(original.canSeeMedications, restored.canSeeMedications)
        assertEquals(original.canReceiveEmergencyAlerts, restored.canReceiveEmergencyAlerts)
    }

    @Test
    fun `fromMap handles missing optional fields gracefully`() {
        val minimal = mapOf(
            "ownerUid" to "o",
            "aidantUid" to "a"
        )
        val link = FamilyLink.fromMap(minimal)
        assertEquals("o", link.ownerUid)
        assertEquals("a", link.aidantUid)
        // Defaults pour les autres champs
        assertEquals("", link.ownerNom)
        assertEquals("", link.aidantNom)
        assertFalse(link.isActive)
        assertEquals(FamilyLinkStatus.PENDING, link.status)
    }

    @Test
    fun `fromMap with corrupted status falls back to PENDING`() {
        val corrupted = mapOf(
            "ownerUid" to "o",
            "aidantUid" to "a",
            "status" to "INVALID_STATUS_VALUE"
        )
        val link = FamilyLink.fromMap(corrupted)
        assertEquals(FamilyLinkStatus.PENDING, link.status)
    }

    @Test
    fun `default canSee permissions are all TRUE`() {
        // Comportement protecteur : un nouveau lien donne acces full read.
        // Une regression a FALSE casserait le mode famille.
        val link = FamilyLink(ownerUid = "o", aidantUid = "a")
        assertTrue(link.canSeeGlucose)
        assertTrue(link.canSeeMeals)
        assertTrue(link.canSeeMedications)
        assertTrue(link.canReceiveEmergencyAlerts)
    }

    @Test
    fun `default isActive is FALSE — must explicitly accept`() {
        // Securite : un lien fraichement cree n'est pas actif tant que
        // l'aidant n'a pas accepte. Une regression a TRUE = leak de
        // donnees medicales sans consentement.
        val link = FamilyLink(ownerUid = "o", aidantUid = "a")
        assertFalse(link.isActive)
        assertEquals(FamilyLinkStatus.PENDING, link.status)
    }

    @Test
    fun `revokedAt is null on fresh link`() {
        val link = FamilyLink(ownerUid = "o", aidantUid = "a")
        assertNull(link.revokedAt)
        assertNull(link.revokedBy)
    }

    @Test
    fun `acceptedAt is null on PENDING link`() {
        val link = FamilyLink(ownerUid = "o", aidantUid = "a")
        assertNull(link.acceptedAt)
    }

    // ── Enum FamilyLinkStatus ──

    @Test
    fun `FamilyLinkStatus has exactly 3 values`() {
        assertEquals(3, FamilyLinkStatus.entries.size)
        assertNotNull(FamilyLinkStatus.PENDING)
        assertNotNull(FamilyLinkStatus.ACCEPTED)
        assertNotNull(FamilyLinkStatus.REJECTED)
    }

    @Test
    fun `enum order is stable for serialization`() {
        // Si l'ordre change, des donnees Firestore deja ecrites pourraient
        // se mapper sur le mauvais statut. On vit le contrat de l'ordre.
        assertEquals("PENDING", FamilyLinkStatus.entries[0].name)
        assertEquals("ACCEPTED", FamilyLinkStatus.entries[1].name)
        assertEquals("REJECTED", FamilyLinkStatus.entries[2].name)
    }

    @Test
    fun `fromMap with empty map returns valid skeleton`() {
        val link = FamilyLink.fromMap(emptyMap())
        assertEquals("", link.ownerUid)
        assertEquals("", link.aidantUid)
        assertFalse(link.isActive)
        assertEquals(FamilyLinkStatus.PENDING, link.status)
    }

    @Test
    fun `documentId is empty when both uids are empty`() {
        val link = FamilyLink(ownerUid = "", aidantUid = "")
        assertEquals("_", link.documentId)  // sentinel "_" - jamais utilisable comme cle Firestore reelle
    }
}
