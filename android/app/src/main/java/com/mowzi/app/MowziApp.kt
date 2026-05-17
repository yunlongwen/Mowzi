package com.mowzi.app

import android.app.Application
import com.iflytek.cloud.SpeechConstant
import com.iflytek.cloud.SpeechUtility
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MowziApp : Application() {
    override fun onCreate() {
        super.onCreate()
        initXfyunSdk()
    }

    private fun initXfyunSdk() {
        val param = StringBuilder()
            .append("appid=${getString(R.string.app_id)}")
            .append(",")
            .append("${SpeechConstant.ENGINE_MODE}=${SpeechConstant.MODE_MSC}")
            .toString()
        SpeechUtility.createUtility(this, param)
    }
}