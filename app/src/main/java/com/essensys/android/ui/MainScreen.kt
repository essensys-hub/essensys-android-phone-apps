package com.essensys.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.essensys.android.data.EssensysAPI

@Composable
fun MainScreen() {
    val navController = rememberNavController()
    var isWanMode by remember { mutableStateOf(EssensysAPI.isWanMode) }
    
    val context = androidx.compose.ui.platform.LocalContext.current

    // Helper to sync mode change UI
    fun toggleMode(wan: Boolean) {
        EssensysAPI.isWanMode = wan
        isWanMode = wan
        
        val sharedPref = context.getSharedPreferences("EssensysPrefs", android.content.Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putBoolean("isWanMode", wan)
            apply()
        }
    }

    Scaffold(
        containerColor = Color(0xFFFAFAFA),
        topBar = {
            MainHeader(
                navController = navController, 
                isWanMode = isWanMode, 
                onModeChange = { toggleMode(it) }
            )
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(padding)
        ) {
            composable("home") { HomeView(navController) }
            composable("lighting") { LightingView(navController) }
            composable("shutters") { ShuttersView(navController) }
            composable("alarm") { AlarmView(navController) }
            composable("settings") { SettingsView(navController) }
        }
    }
}

@Composable
fun MainHeader(
    navController: NavController, 
    isWanMode: Boolean, 
    onModeChange: (Boolean) -> Unit
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
    ) {
        // Top Row: Brand + Mode Dropdown
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
             Box {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { expanded = true }
                ) {
                    Icon(
                        imageVector = if (isWanMode) Icons.Default.Language else Icons.Default.Wifi,
                        contentDescription = null,
                        tint = if (isWanMode) Color.Blue else Color(0xFF4CAF50),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Essensys",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF2D3748)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                }
                
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Essensys Local") },
                        leadingIcon = { Icon(Icons.Default.Wifi, contentDescription = null) },
                        onClick = { 
                            onModeChange(false)
                            expanded = false
                        }
                    )
                     DropdownMenuItem(
                        text = { Text("Essensys WAN") },
                        leadingIcon = { Icon(Icons.Default.Language, contentDescription = null) },
                        onClick = { 
                            onModeChange(true)
                            expanded = false 
                        }
                    )
                }
            }
        }

        // Tabs Row
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            HeaderTab("Accueil", selected = currentRoute == "home") {
                navController.navigate("home") {
                    popUpTo("home") { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
            HeaderTab("Éclairage", selected = currentRoute == "lighting") {
                navController.navigate("lighting") {
                    popUpTo("home") { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
            HeaderTab("Volets", selected = currentRoute == "shutters") {
                navController.navigate("shutters") {
                    popUpTo("home") { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
            HeaderTab("Alarme", selected = currentRoute == "alarm") {
                navController.navigate("alarm") {
                    popUpTo("home") { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
            HeaderTab("Configuration", selected = currentRoute == "settings") {
                navController.navigate("settings") {
                    popUpTo("home") { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        }
        Divider(color = Color(0xFFEEEEEE), thickness = 1.dp)
    }
}

@Composable
fun HeaderTab(title: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) Color(0xFF1976D2) else Color.Gray
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .height(2.dp)
                .width(if (selected) 20.dp else 0.dp) // Simple visual indicator
                .background(if (selected) Color(0xFF1976D2) else Color.Transparent)
        )
    }
}
