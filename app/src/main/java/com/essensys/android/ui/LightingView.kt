package com.essensys.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.essensys.android.data.EssensysAPI
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Data Models
data class LightingItem(
    val id: String,
    val name: String,
    val onIndex: Int,
    val offIndex: Int,
    val value: String,
    val isIndirect: Bool = false
)

data class RoomLighting(
    val name: String,
    val lights: List<LightingItem>
)

// Hardcoded Data (Sample - similar to iOS)
val lightingData = listOf(
    RoomLighting("Salon", listOf(
        LightingItem("ls1", "Salon Direct", 612, 606, "128"),
        LightingItem("ls2", "Indirect 1", 611, 605, "2", true),
        LightingItem("ls3", "Indirect 2", 611, 605, "4", true)
    )),
    RoomLighting("Cuisine", listOf(
        LightingItem("lc1", "Cuisine", 615, 609, "1")
    )),
    RoomLighting("Salle à Manger", listOf(
        LightingItem("lsm1", "S.A.M", 612, 606, "64")
    ))
    // Add more rooms as needed
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LightingView(navController: NavController) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Éclairage") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Retour")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(lightingData) { room ->
                RoomCard(room, onError = { msg ->
                    scope.launch { snackbarHostState.showSnackbar(msg) }
                })
            }
        }
    }
}

@Composable
fun RoomCard(room: RoomLighting, onError: (String) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = room.name,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                room.lights.forEach { light ->
                    LightRow(light, onError)
                }
            }
        }
    }
}

@Composable
fun LightRow(light: LightingItem, onError: (String) -> Unit) {
    var isCooldown by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.LightGray.copy(alpha = 0.1f), MaterialTheme.shapes.small)
            .padding(8.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Lightbulb,
            contentDescription = null,
            tint = if (light.isIndirect) Color(0xFFFFB74D) else Color(0xFFFFD54F) // Orange or Yellow
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = light.name, modifier = Modifier.weight(1f))

        // Buttons
        Button(
            onClick = {
                sendAction(light.onIndex, light.value, onError)
                scope.launch {
                    isCooldown = true
                    delay(3000) // 3 seconds cooldown
                    isCooldown = false
                }
            },
            enabled = !isCooldown,
            modifier = Modifier.padding(end = 8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF66BB6A)) // Green
        ) {
            Text("ON")
        }

        Button(
            onClick = {
                sendAction(light.offIndex, light.value, onError)
                scope.launch {
                    isCooldown = true
                    delay(3000) // 3 seconds cooldown
                    isCooldown = false
                }
            },
            enabled = !isCooldown,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF5350)) // Red
        ) {
            Text("OFF")
        }
    }
}

fun sendAction(k: Int, v: String, onError: (String) -> Unit) {
    EssensysAPI.sendInjection(k, v, object : EssensysAPI.ResultCallback {
        override fun onSuccess() {} // No feedback needed for success usually
        override fun onError(error: String) {
            onError(error)
        }
    })
}
