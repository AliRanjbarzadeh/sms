package com.github.tmo1.sms_ie

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Build
import java.util.*

@Suppress("DEPRECATION")
@SuppressWarnings("DEPRECATION")
fun Application.setLanguage(language: String) {
    val locale = Locale(language)
    Locale.setDefault(locale)

    val resources = applicationContext.resources
    val configuration = resources.configuration

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
        configuration.setLocale(locale)
    else
        configuration.locale = locale

    configuration.setLayoutDirection(locale)
    resources.updateConfiguration(configuration, applicationContext.resources.displayMetrics)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
        this.applicationContext.createConfigurationContext(configuration)
}

@Suppress("DEPRECATION")
fun Activity.setLanguage(language: String) {
    val locale = Locale(language)
    Locale.setDefault(locale)

    val resources = this.applicationContext.resources
    val configuration = resources.configuration
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) configuration.setLocale(locale)
    else configuration.locale = locale

    configuration.setLayoutDirection(locale)
    this.resources.updateConfiguration(configuration, this.applicationContext.resources.displayMetrics)
    resources.updateConfiguration(configuration, this.applicationContext.resources.displayMetrics)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) this.applicationContext.createConfigurationContext(configuration)
}

@Suppress("DEPRECATION")
fun Activity.setLanguage(context: Context, language: String) {
    val locale = Locale(language)
    Locale.setDefault(locale)

    val resources = context.resources
    val configuration = resources.configuration
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) configuration.setLocale(locale)
    else configuration.locale = locale

    configuration.setLayoutDirection(locale)
    resources.updateConfiguration(configuration, resources.displayMetrics)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) context.createConfigurationContext(configuration)
}