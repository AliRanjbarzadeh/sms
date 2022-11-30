package com.github.tmo1.sms_ie.base.di

import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

/**
 * Created by Anonymous on 9/29/2022 AD.
 */
class ModifyHeadersInterceptor @Inject constructor() : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val builder = request.newBuilder()

//		try {
//			var authorization = loadFromSp(BuildConfig.SESSION_TOKEN, "")
//			if (authorization.isEmpty()) {
//				authorization = loadFromSp(BuildConfig.REGISTER_TOKEN, BuildConfig.DEFAULT_TOKEN)
//			}
//			val language = loadFromSp(BuildConfig.SESSION_LANGUAGE, BuildConfig.DEFAULT_LANGUAGE)
//
//			val pushToken = loadFromSp(BuildConfig.SESSION_PUSH_TOKEN, "")
//
//			builder.header("Cache-Control", "no-cache")
//			builder.header("User-Agent", BuildConfig.USER_AGENT)
//			builder.header("AppVersion", BuildConfig.APP_VERSION)
//			builder.header("AppLanguage", language)
//			builder.header("DeviceType", "1")
//			builder.header("DeviceVersion", Build.VERSION.RELEASE)
//			builder.header("DeviceModel", Build.MODEL)
//			builder.header("DeviceManufacture", Build.MANUFACTURER)
//			builder.header("DevicePushToken", pushToken)
//			builder.header(BuildConfig.AUTHORIZATION, BuildConfig.TOKEN_PREFIX + authorization)
//		} catch (ex: Exception) {
////			e(ex.message, "HeaderErrors")
//		}

        val builderResponse = chain.proceed(builder.build())
        return builderResponse
    }
}