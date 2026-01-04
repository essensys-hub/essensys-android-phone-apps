package com.essensys.android.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.essensys.android.data.EssensysAPI

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsView(navController: NavController) {
    val context = LocalContext.current
    val sharedPref = remember { context.getSharedPreferences("EssensysPrefs", Context.MODE_PRIVATE) }

    var serverUrl by remember { mutableStateOf(EssensysAPI.serverUrl) }
    var username by remember { mutableStateOf(EssensysAPI.username) }
    var password by remember { mutableStateOf(EssensysAPI.password) }
    var isWanMode by remember { mutableStateOf(EssensysAPI.isWanMode) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Réglages") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Retour")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = serverUrl,
                onValueChange = { serverUrl = it },
                label = { Text("URL Serveur") },
                modifier = Modifier.fillMaxWidth()
            )

            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(
                    checked = isWanMode,
                    onCheckedChange = { isWanMode = it }
                )
                Text("Mode WAN (Distant)")
            }
            
            if (isWanMode) {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Nom d'utilisateur") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Mot de passe") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Button(
                onClick = {
                    // Save to Prefs
                    with(sharedPref.edit()) {
                        putString("serverUrl", serverUrl)
                        putString("username", username)
                        putString("password", password)
                        putBoolean("isWanMode", isWanMode)
                        apply()
                    }
                    // Update API Singleton
                    EssensysAPI.serverUrl = serverUrl
                    EssensysAPI.username = username
                    EssensysAPI.password = password
                    EssensysAPI.isWanMode = isWanMode
                    
                    navController.popBackStack()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Enregistrer")
            }
        }
    }
}
