package com.diabeto.di

import android.content.Context
import com.diabeto.data.dao.*
import com.diabeto.data.database.DiabetoDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Module Hilt pour l'injection de la base de données
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): DiabetoDatabase {
        return DiabetoDatabase.getInstance(context)
    }
    
    @Provides
    fun providePatientDao(database: DiabetoDatabase): PatientDao {
        return database.patientDao()
    }
    
    @Provides
    fun provideGlucoseDao(database: DiabetoDatabase): GlucoseDao {
        return database.glucoseDao()
    }
    
    @Provides
    fun provideMedicamentDao(database: DiabetoDatabase): MedicamentDao {
        return database.medicamentDao()
    }
    
    @Provides
    fun provideRendezVousDao(database: DiabetoDatabase): RendezVousDao {
        return database.rendezVousDao()
    }

    @Provides
    fun provideHbA1cDao(database: DiabetoDatabase): HbA1cDao {
        return database.hbA1cDao()
    }

    @Provides
    fun provideTensionDao(database: DiabetoDatabase): TensionDao {
        return database.tensionDao()
    }

    @Provides
    fun provideJournalDao(database: DiabetoDatabase): JournalDao {
        return database.journalDao()
    }

    @Provides
    fun provideAiCacheDao(database: DiabetoDatabase): AiCacheDao {
        return database.aiCacheDao()
    }

    @Provides
    fun providePendingOperationDao(database: DiabetoDatabase): PendingOperationDao {
        return database.pendingOperationDao()
    }
}
