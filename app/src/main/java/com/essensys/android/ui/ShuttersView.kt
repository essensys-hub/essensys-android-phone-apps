package com.essensys.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.essensys.android.data.EssensysAPI
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Data Models
data class ShutterItem(
    val name: String,
    val upIndex: Int,
    val downIndex: Int,
    val value: String = "1"
)

// Hardcoded Data
val shutterData = listOf(
    ShutterItem("Volet 1 Salon", 617, 620, "1"),
    ShutterItem("Volet 2 Salon", 617, 620, "2"),
    ShutterItem("Volet 3 Salon", 617, 620, "4"),
    ShutterItem("Volet 1 Salle à Manger", 617, 620, "8"),
    ShutterItem("Volet 2 Salle à Manger", 617, 620, "16"),
    ShutterItem("Volet Bureau", 617, 620, "32"),
    
    ShutterItem("Volet 1 Cuisine", 619, 622, "1"),
    ShutterItem("Volet 2 Cuisine", 619, 622, "2"),
    ShutterItem("Volet Salle de Bain 1", 619, 622, "4"),
    
    ShutterItem("Volet 1 Grande Chambre", 618, 621, "1"),
    ShutterItem("Volet 2 Grande Chambre", 618, 621, "2"),
    ShutterItem("Volet Petite Chambre 1", 618, 621, "4"),
    ShutterItem("Volet Petite Chambre 2", 618, 621, "8"),
    ShutterItem("Volet Petite Chambre 3", 618, 621, "16")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShuttersView(navController: NavController) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                     Text(
                        "Volets",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Retour")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
             LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                 // Info Banner
                item {
                    InfoBanner()
                    Spacer(modifier = Modifier.height(16.dp))
                }
                
                // Global Actions
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        GlobalShutterButton(
                            text = "Tout Ouvrir",
                            icon = Icons.Default.ArrowUpward,
                            color = Color(0xFF4CAF50), // Green
                            onClick = { /* TODO Loop all open */ }
                        )
                        Spacer(modifier = Modifier.width(24.dp))
                        GlobalShutterButton(
                            text = "Tout Fermer",
                            icon = Icons.Default.ArrowDownward,
                            color = Color(0xFF1976D2), // Blue
                            onClick = { /* TODO Loop all close */ }
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                items(shutterData) { shutter ->
                    ShutterRow(shutter, onError = { msg ->
                        scope.launch { snackbarHostState.showSnackbar(msg) }
                    })
                    Divider(color = Color(0xFFEEEEEE))
                }
            }
        }
    }
}

@Composable
fun GlobalShutterButton(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = color),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        modifier = Modifier.size(width = 100.dp, height = 80.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
             Icon(imageVector = icon, contentDescription = null)
             Spacer(modifier = Modifier.height(4.dp))
             Text(text, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun ShutterRow(shutter: ShutterItem, onError: (String) -> Unit) {
    var isCooldown by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        Text(
            text = shutter.name,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // UP Button
            FilledIconButton(
                onClick = {
                    sendShutterAction(shutter.upIndex, shutter.value, onError)
                    scope.launch {
                        isCooldown = true
                        delay(3000)
                        isCooldown = false
                    }
                },
                enabled = !isCooldown,
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFFE8F5E9)) // Light Green
            ) {
                Icon(
                    Icons.Default.ArrowUpward,
                    contentDescription = "Ouvrir",
                    tint = Color(0xFF2E7D32) // Dark Green
                )
            }

            // DOWN Button
            FilledIconButton(
                onClick = {
                    sendShutterAction(shutter.downIndex, shutter.value, onError)
                    scope.launch {
                        isCooldown = true
                        delay(3000)
                        isCooldown = false
                    }
                },
                enabled = !isCooldown,
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFFE3F2FD)) // Light Blue
            ) {
                Icon(
                    Icons.Default.ArrowDownward,
                    contentDescription = "Fermer",
                    tint = Color(0xFF1565C0) // Dark Blue
                )
            }
        }
    }
}

fun sendShutterAction(k: Int, v: String, onError: (String) -> Unit) {
    EssensysAPI.sendInjection(k, v, object : EssensysAPI.ResultCallback {
        override fun onSuccess() {} 
        override fun onError(error: String) {
            onError(error)
        }
    })
}
