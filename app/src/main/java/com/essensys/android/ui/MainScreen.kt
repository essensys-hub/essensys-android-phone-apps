package com.essensys.android.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

// This logic is now in MainActivity.kt or can be kept here if preferred.
// To resolve the duplicate 'HomeView' call in MainActivity, we renamed the internal one there to MainScreen.
// If this file is unused, it can be deleted, but we'll keep it as a dedicated file for the MainScreen composable 
// if we want to move it out of MainActivity.

// For now, let's make sure MainActivity uses the definition here if we want to keep it clean.
// However, I already wrote MainScreen into MainActivity.kt to solve the immediate issue.
// So this file might be redundant or causing conflicts if it declares the same function name.

// Let's repurpose this file to hold the HomeView *Scaffold* if needed, but HomeView is in HomeView.kt.

// The error "Conflicting overloads: fun HomeView..." suggests HomeView is defined twice.
// One in HomeView.kt (the main one we want) and one likely in the old version of this file or MainActivity.
// I see I wrote MainScreen in MainActivity.kt.

// I will clear this file content to avoid "Redeclaration" errors if MainScreen was defined here too,
// OR better, I will move the MainScreen definition from MainActivity to here properly.

@Composable
fun MainScreen() {
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
