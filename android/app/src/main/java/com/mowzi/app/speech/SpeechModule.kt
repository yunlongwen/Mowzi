package com.mowzi.app.speech

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SpeechModule {

    @Provides
    @Singleton
    fun provideXfyunSpeechService(
        @ApplicationContext context: Context
    ): XfyunSpeechService {
        return XfyunSpeechService(
            context = context,
            appId = "1b20cd0f",
            engineType = "local",
            defaultVoiceName = "xiaoyan"
        )
    }
}
