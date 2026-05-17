package com.mowzi.app.di

import com.mowzi.app.util.TokenManager
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

class AuthInterceptor @Inject constructor(
    private val tokenManager: TokenManager
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        // Skip auth for device register and parent auth endpoints
        val path = originalRequest.url.encodedPath
        if (path.contains("/device/register") || path.contains("/parent/auth")) {
            return chain.proceed(originalRequest)
        }

        // Get device token synchronously
        val token = runBlocking { tokenManager.getDeviceToken() }

        return if (token != null) {
            val authenticatedRequest = originalRequest.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
            chain.proceed(authenticatedRequest)
        } else {
            chain.proceed(originalRequest)
        }
    }
}