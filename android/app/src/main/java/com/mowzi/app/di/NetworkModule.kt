package com.mowzi.app.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.mowzi.app.data.remote.MowziApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.logging.HttpLoggingInterceptor.Level
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import kotlinx.coroutines.flow.map

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private val BASE_URL_KEY = stringPreferencesKey("api_base_url")

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = Level.BODY
        }
        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(
        okHttpClient: OkHttpClient,
        dataStore: DataStore<Preferences>
    ): Retrofit {
        val baseUrl = runBlocking {
            dataStore.data.map { preferences ->
                preferences[BASE_URL_KEY] ?: "https://api.mowzi.example.com"
            }.first()
        }
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(
                kotlinx.serialization.json.Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                }.asConverterFactory("application/json".toMediaType())
            )
            .build()
    }

    @Provides
    @Singleton
    fun provideMowziApi(retrofit: Retrofit): MowziApi {
        return retrofit.create(MowziApi::class.java)
    }
}