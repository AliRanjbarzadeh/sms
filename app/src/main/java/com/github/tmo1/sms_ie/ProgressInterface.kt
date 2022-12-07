package com.github.tmo1.sms_ie

import androidx.annotation.RawRes

interface ProgressInterface {
    fun onProgress(currentProgress: Int, progressText: String)

    fun onChangeType(@RawRes animationFile: Int, title: String)

    fun onFinish()
}