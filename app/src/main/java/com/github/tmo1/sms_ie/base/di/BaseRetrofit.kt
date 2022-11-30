package com.github.tmo1.sms_ie.base.di

import com.github.tmo1.sms_ie.BuildConfig.*
import com.google.gson.Gson
import com.jakewharton.retrofit2.adapter.kotlin.coroutines.CoroutineCallAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Created by Anonymous on 9/29/2022 AD.
 */
@Singleton
class BaseRetrofit @Inject constructor(okHttpClient: OkHttpClient, gson: Gson) {
    val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(if (ENVIRONMENT.equals("development")) DEV_URL else BASE_URL)
        .addCallAdapterFactory(CoroutineCallAdapterFactory.invoke())
        .addConverterFactory(GsonConverterFactory.create(gson))
        .client(okHttpClient)
        .build()
}