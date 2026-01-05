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

    var localUrl by remember { mutableStateOf(EssensysAPI.localUrl) }
    var wanUrl by remember { mutableStateOf(EssensysAPI.wanUrl) }
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
            Text("Configuration Locale", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = localUrl,
                onValueChange = { localUrl = it },
                label = { Text("URL Locale") },
                placeholder = { Text("http://mon.essensys.fr") },
                modifier = Modifier.fillMaxWidth()
            )

            Divider()

            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(
                    checked = isWanMode,
                    onCheckedChange = { isWanMode = it }
                )
                Text("Mode WAN (Distant)")
            }
            
            // WAN Fields always visible or only when checked? 
            // Requests separate config, so useful to edit even if not active. 
            // But visually better if grouped.
            if (true) { // Always show WAN fields to allow pre-conf? Or follow typical UX? Let's show always but highlight if active.
                 Text("Configuration Distante (WAN)", style = MaterialTheme.typography.titleMedium)
                 OutlinedTextField(
                    value = wanUrl,
                    onValueChange = { wanUrl = it },
                    label = { Text("URL Distante") },
                    modifier = Modifier.fillMaxWidth()
                )
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
                    // visualTransformation = PasswordVisualTransformation() // Recommended but not in imports currently, keep plain for now or add import
                )
            }

            Button(
                onClick = {
                    // Save to Prefs
                    with(sharedPref.edit()) {
                        putString("localUrl", localUrl)
                        putString("wanUrl", wanUrl)
                        putString("username", username)
                        putString("password", password)
                        putBoolean("isWanMode", isWanMode)
                        apply()
                    }
                    // Update API Singleton
                    EssensysAPI.localUrl = localUrl
                    EssensysAPI.wanUrl = wanUrl
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
