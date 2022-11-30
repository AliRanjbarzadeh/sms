/*
 * SMS Import / Export: a simple Android app for importing and exporting SMS messages from and to JSON files.
 * Copyright (c) 2021-2022 Thomas More
 *
 * This file is part of SMS Import / Export.
 *
 * SMS Import / Export is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMS Import / Export is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMS Import / Export.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.github.tmo1.sms_ie.base.di

import android.app.Application
import com.github.tmo1.sms_ie.base.DispatchersProvider
import com.github.tmo1.sms_ie.base.DispatchersProviderImpl
import com.github.tmo1.sms_ie.exceptions.NetworkExceptionHandler
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.Cache
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import javax.inject.Singleton

/**
 * Created by Anonymous on 11/30/2022 AD.
 */

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun providesGson(): Gson = GsonBuilder().create()

    @Provides
    @Singleton
    fun providesOkHttpClient(baseHttpClient: BaseHttpClient): OkHttpClient =
        baseHttpClient.okHttpClient

    @Provides
    @Singleton
    fun providesRetrofit(baseRetrofit: BaseRetrofit): Retrofit = baseRetrofit.retrofit

    @Provides
    @Singleton
    fun providesDispatcher(dispatcherProvider: DispatchersProviderImpl): DispatchersProvider =
        dispatcherProvider.dispatcher

    @Provides
    @Singleton
    fun providesApiExceptionHandler(gson: Gson): NetworkExceptionHandler =
        NetworkExceptionHandler(gson)

    @Provides
    @Singleton
    fun providesCache(application: Application): Cache =
        Cache(application.cacheDir, 30 * 1024 * 1024)

}