package com.github.tmo1.sms_ie

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.TextView

class AboutActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)
        val aboutText: TextView = findViewById(R.id.aboutText)
        aboutText.text = getString(R.string.app_about_text, BuildConfig.VERSION_NAME)
    }
}