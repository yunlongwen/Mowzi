package com.mowzi.app.di

import com.mowzi.app.audio.AudioPlayer
import com.mowzi.app.audio.AudioRecorder
import com.mowzi.app.data.repository.ConversationRepository
import com.mowzi.app.data.repository.ConversationRepositoryImpl
import com.mowzi.app.data.repository.MessageRepository
import com.mowzi.app.data.repository.MessageRepositoryImpl
import com.mowzi.app.data.repository.ParentRepository
import com.mowzi.app.data.repository.ParentRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindConversationRepository(
        impl: ConversationRepositoryImpl
    ): ConversationRepository

    @Binds
    @Singleton
    abstract fun bindMessageRepository(
        impl: MessageRepositoryImpl
    ): MessageRepository

    @Binds
    @Singleton
    abstract fun bindParentRepository(
        impl: ParentRepositoryImpl
    ): ParentRepository

    companion object {
        @Provides
        @Singleton
        fun provideAudioRecorder(): AudioRecorder = AudioRecorder()

        @Provides
        @Singleton
        fun provideAudioPlayer(): AudioPlayer = AudioPlayer()
    }
}
