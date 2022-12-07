package com.github.tmo1.sms_ie.base.di

import okhttp3.Cache
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Created by Anonymous on 9/29/2022 AD.
 */
class BaseHttpClient @Inject constructor(
    cache: Cache,
    modifyHeadersInterceptor: ModifyHeadersInterceptor
) {
    val okHttpClient = OkHttpClient()
        .newBuilder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .addNetworkInterceptor(modifyHeadersInterceptor)
        .build()
}