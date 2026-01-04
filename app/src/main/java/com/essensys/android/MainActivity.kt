package com.essensys.android

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.essensys.android.data.EssensysAPI
import com.essensys.android.ui.HomeView
import com.essensys.android.ui.LightingView
import com.essensys.android.ui.SettingsView
import com.essensys.android.ui.ShuttersView

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
            EssensysApp()
        }
    }
}

@Composable
fun EssensysApp() {
    val navController = rememberNavController()

    Scaffold { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("home") {
                HomeView(navController)
            }
            composable("lighting") {
                LightingView(navController)
            }
            composable("shutters") {
                ShuttersView(navController)
            }
            composable("settings") {
                SettingsView(navController)
            }
        }
    }
}
