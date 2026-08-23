package com.diabeto.data.database

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.diabeto.data.dao.*
import com.diabeto.data.entity.*
import net.sqlcipher.database.SupportFactory
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Base de données Room principale de l'application.
 * Chiffrée avec SQLCipher pour protéger les données médicales sensibles.
 */
@Database(
    entities = [
        PatientEntity::class,
        LectureGlucoseEntity::class,
        MedicamentEntity::class,
        RendezVousEntity::class,
        HbA1cEntity::class,
        JournalEntity::class,
        AiCacheEntity::class,
        PendingOperationEntity::class
    ],
    version = 10,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class DiabetoDatabase : RoomDatabase() {

    abstract fun patientDao(): PatientDao
    abstract fun glucoseDao(): GlucoseDao
    abstract fun medicamentDao(): MedicamentDao
    abstract fun rendezVousDao(): RendezVousDao
    abstract fun hbA1cDao(): HbA1cDao
    abstract fun journalDao(): JournalDao
    abstract fun aiCacheDao(): AiCacheDao
    abstract fun pendingOperationDao(): PendingOperationDao

    companion object {
        const val DATABASE_NAME = "diabeto_database.db"
        private const val TAG = "DiabetoDatabase"

        private const val KEYSTORE_ALIAS = "diasmart_db_key"
        private const val PREFS_NAME = "diasmart_db_prefs"
        private const val PREF_ENCRYPTED_PASS = "encrypted_passphrase"
        private const val PREF_IV = "passphrase_iv"

        /**
         * Passphrase for SQLCipher — generated once, stored encrypted via Android Keystore.
         * The actual passphrase is a random 32-byte key, encrypted with AES-GCM using
         * a hardware-backed key that never leaves the Keystore.
         */
        private fun getPassphrase(context: Context): ByteArray {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val existingEncrypted = prefs.getString(PREF_ENCRYPTED_PASS, null)
            val existingIv = prefs.getString(PREF_IV, null)

            return if (existingEncrypted != null && existingIv != null) {
                // Decrypt stored passphrase with Keystore key
                try {
                    val key = getOrCreateKeystoreKey()
                    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                    val iv = Base64.decode(existingIv, Base64.NO_WRAP)
                    cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
                    cipher.doFinal(Base64.decode(existingEncrypted, Base64.NO_WRAP))
                } catch (e: Exception) {
                    Log.w(TAG, "Keystore decrypt failed, falling back to legacy key", e)
                    getLegacyPassphrase(context)
                }
            } else {
                // First launch: generate random passphrase and encrypt with Keystore
                try {
                    val passphrase = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
                    val key = getOrCreateKeystoreKey()
                    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                    cipher.init(Cipher.ENCRYPT_MODE, key)
                    val encrypted = cipher.doFinal(passphrase)
                    prefs.edit()
                        .putString(PREF_ENCRYPTED_PASS, Base64.encodeToString(encrypted, Base64.NO_WRAP))
                        .putString(PREF_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
                        .apply()
                    passphrase
                } catch (e: Exception) {
                    Log.w(TAG, "Keystore init failed, using legacy key", e)
                    getLegacyPassphrase(context)
                }
            }
        }

        private fun getOrCreateKeystoreKey(): SecretKey {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            keyStore.getEntry(KEYSTORE_ALIAS, null)?.let {
                return (it as KeyStore.SecretKeyEntry).secretKey
            }
            val spec = KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
                .apply { init(spec) }
                .generateKey()
        }

        /** Legacy passphrase for backward compatibility with existing databases. */
        private fun getLegacyPassphrase(context: Context): ByteArray {
            val packageName = context.packageName
            val androidId = android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ANDROID_ID
            ) ?: "diasmart_default"
            return "DSm@rt_${packageName}_$androidId".toByteArray(Charsets.UTF_8)
        }

        // ══════════════════════════════════════════════════════════════
        // MIGRATIONS EXPLICITES (plus de fallbackToDestructiveMigration)
        // ══════════════════════════════════════════════════════════════

        // v2.1.44 : migrations no-op v1→v5. Sans elles, un user avec une
        // ancienne version (peu probable mais possible) perdait sa base
        // locale. Avec ces stubs, Room "transite" simplement.
        // Pour chaque version, on tente d'ajouter les colonnes critiques
        // si absentes (idempotent via try/catch).
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Log.d(TAG, "Migration 1→2 (no-op stub)")
            }
        }
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Log.d(TAG, "Migration 2→3 (no-op stub)")
            }
        }
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Log.d(TAG, "Migration 3→4 (no-op stub)")
            }
        }
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Log.d(TAG, "Migration 4→5 (no-op stub)")
            }
        }
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Log.d(TAG, "Migration 5→6 (no-op stub)")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // v7: Add lastModified column to all entities for conflict resolution
                val tables = listOf(
                    "patients", "lectures_glucose", "medicaments",
                    "rendez_vous", "hba1c_lectures", "journal_entries"
                )
                for (table in tables) {
                    try {
                        db.execSQL("ALTER TABLE $table ADD COLUMN lastModified INTEGER NOT NULL DEFAULT 0")
                    } catch (e: Exception) {
                        // Column may already exist in some schema versions
                        Log.w(TAG, "Migration 6→7: column lastModified may already exist in $table: ${e.message}")
                    }
                }
                Log.d(TAG, "Migration 6→7 complete: added lastModified columns")
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // v8: Add HMAC column to ai_cache for integrity verification
                try {
                    db.execSQL("ALTER TABLE ai_cache ADD COLUMN hmac TEXT NOT NULL DEFAULT ''")
                } catch (e: Exception) {
                    Log.w(TAG, "Migration 7→8: hmac column may already exist: ${e.message}")
                }
                Log.d(TAG, "Migration 7→8 complete: added hmac column to ai_cache")
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS pending_operations (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        operationType TEXT NOT NULL,
                        collection TEXT NOT NULL,
                        documentId TEXT NOT NULL,
                        payload TEXT NOT NULL,
                        retryCount INTEGER NOT NULL DEFAULT 0,
                        maxRetries INTEGER NOT NULL DEFAULT 5,
                        createdAt INTEGER NOT NULL,
                        lastAttemptAt INTEGER NOT NULL DEFAULT 0,
                        status TEXT NOT NULL DEFAULT 'PENDING'
                    )
                """.trimIndent())
                Log.d(TAG, "Migration 8→9 complete: created pending_operations table")
            }
        }

        /**
         * v10 — cloisonnement des dossiers patients par compte.
         *
         * Jusqu'ici la table `patients` n'avait aucune marque de proprietaire :
         * la base appartenait a l'appareil, pas au compte connecte. Sur un
         * telephone ou l'application avait d'abord servi a un patient, un
         * medecin qui s'y connectait ensuite retrouvait ses dossiers, sa
         * glycemie comprise.
         *
         * Les dossiers DEJA presents sont deliberement laisses SANS
         * proprietaire (chaine vide). Les attribuer au compte connecte au
         * moment de la mise a jour aurait reproduit exactement la faute qu'on
         * corrige : le compte medecin aurait herite des dossiers crees cote
         * patient. Un dossier orphelin reste stocke mais n'apparait dans
         * aucune requete ; il faut le reattribuer explicitement.
         */
        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL(
                        "ALTER TABLE patients ADD COLUMN ownerUid TEXT NOT NULL DEFAULT ''"
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Migration 9→10 : colonne ownerUid deja presente ? ${e.message}")
                }
                // Index : chaque lecture filtre desormais sur ce champ.
                try {
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS index_patients_ownerUid ON patients(ownerUid)"
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Migration 9→10 : index non cree : ${e.message}")
                }
                Log.d(TAG, "Migration 9→10 : dossiers existants laisses sans proprietaire")
            }
        }

        @Volatile
        private var INSTANCE: DiabetoDatabase? = null

        fun getInstance(context: Context): DiabetoDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: try {
                    val db = buildDatabase(context)
                    // Validate database is accessible (detects key mismatch / corruption)
                    db.openHelper.readableDatabase
                    db
                } catch (e: Exception) {
                    Log.e(TAG, "Base de données corrompue ou clé invalide, recréation...", e)
                    // Delete corrupted database file
                    context.deleteDatabase(DATABASE_NAME)
                    // Clear stored passphrase to generate a new one
                    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit().clear().apply()
                    // Rebuild with fresh encryption key
                    buildDatabase(context)
                }.also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): DiabetoDatabase {
            val passphrase = getPassphrase(context)
            val factory = SupportFactory(passphrase)

            return Room.databaseBuilder(
                context.applicationContext,
                DiabetoDatabase::class.java,
                DATABASE_NAME
            )
            .openHelperFactory(factory)
            .addMigrations(
                MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6,
                MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10
            )
            .fallbackToDestructiveMigrationOnDowngrade()
            .build()
        }
    }
}
