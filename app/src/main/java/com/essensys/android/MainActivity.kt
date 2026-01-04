package com.essensys.android

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import com.essensys.android.data.EssensysAPI
import com.essensys.android.ui.MainScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Load configuration
        val sharedPref = getSharedPreferences("EssensysPrefs", Context.MODE_PRIVATE)
        EssensysAPI.serverUrl = sharedPref.getString("serverUrl", "http://192.168.1.35") ?: "http://192.168.1.35"
        EssensysAPI.username = sharedPref.getString("username", "") ?: ""
        EssensysAPI.password = sharedPref.getString("password", "") ?: ""
        EssensysAPI.isWanMode = sharedPref.getBoolean("isWanMode", false)

        setContent {
            MaterialTheme {
                MainScreen()
            }
        }
    }
}
