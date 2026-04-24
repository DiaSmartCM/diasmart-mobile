package com.diabeto.voip

import android.content.Context
import com.diabeto.data.repository.DoctorReviewRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object VoipModule {

    @Provides
    @Singleton
    fun provideWebRTCManager(
        @ApplicationContext context: Context
    ): WebRTCManager = WebRTCManager(context)

    @Provides
    @Singleton
    fun provideCallManager(
        @ApplicationContext context: Context,
        webRTCManager: WebRTCManager,
        doctorReviewRepository: DoctorReviewRepository
    ): CallManager = CallManager(context, webRTCManager, doctorReviewRepository).also {
        CallManagerProvider.callManager = it
    }
}
