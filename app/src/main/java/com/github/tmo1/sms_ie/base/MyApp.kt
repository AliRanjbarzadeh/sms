package com.github.tmo1.sms_ie.base

import android.app.Application
import com.github.tmo1.sms_ie.R
import com.github.tmo1.sms_ie.setLanguage
import com.orhanobut.hawk.Hawk
import dagger.hilt.android.HiltAndroidApp
import io.github.inflationx.calligraphy3.CalligraphyConfig
import io.github.inflationx.calligraphy3.CalligraphyInterceptor
import io.github.inflationx.viewpump.ViewPump

/**
 * Created by Anonymous on 11/30/2022 AD.
 */

@HiltAndroidApp
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        initHawk()
        initLanguage()
        initFont()
    }

    private fun initHawk() {
        Hawk.init(this).build()
    }

    private fun initLanguage() {
        setLanguage("fa")
    }

    private fun initFont() {
        ViewPump.init(
            ViewPump.builder()
                .addInterceptor(
                    CalligraphyInterceptor(
                        CalligraphyConfig.Builder()
                            .setDefaultFontPath(getString(R.string.font_regular))
                            .setFontAttrId(io.github.inflationx.calligraphy3.R.attr.fontPath)
                            .build()
                    )
                )
                .build()
        )
    }
}