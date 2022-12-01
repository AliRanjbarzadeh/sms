package com.github.tmo1.sms_ie

interface ProgressInterface {
    fun onProgress(currentProgress: Int)

    fun onChangeType()
}