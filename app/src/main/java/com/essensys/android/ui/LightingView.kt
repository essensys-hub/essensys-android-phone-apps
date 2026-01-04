package com.essensys.android.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.rounded.Bed
import androidx.compose.material.icons.rounded.Chair
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Dining
import androidx.compose.material.icons.rounded.Kitchen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val isIndirect: Boolean = false
)

data class RoomLighting(
    val name: String,
    val lights: List<LightingItem>
)

// Hardcoded Data
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
    )),
    RoomLighting("Grande Chambre", listOf(
        LightingItem("lgc1", "Plafonnier", 613, 607, "1")
    )),
     RoomLighting("Bureau", listOf(
        LightingItem("lb1", "Bureau", 613, 607, "2")
    ))
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LightingView(navController: NavController) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Title
                item {
                     Text(
                        "Éclairage",
                        style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                         modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                // Info Banner
                item {
                    InfoBanner()
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Global Actions
                item {
                    GlobalActions(
                        onAllOn = { 
                            scope.launch { snackbarHostState.showSnackbar("Tout allumer...") }
                            // Implementation for All On would loop through data
                        },
                        onAllOff = {
                             scope.launch { snackbarHostState.showSnackbar("Tout éteindre...") }
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                items(lightingData) { room ->
                    RoomCard(room, onError = { msg ->
                        scope.launch { snackbarHostState.showSnackbar(msg) }
                    })
                }
            }
        }
    }
}

@Composable
fun InfoBanner() {
    Surface(
        color = Color(0xFFE3F2FD), // Light Blue
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Info,
                contentDescription = "Info",
                tint = Color(0xFF1976D2), // Blue
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Essensys fonctionne en boucle ouverte : les équipements ne remontent pas leur état.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF455A64)
            )
        }
    }
}

@Composable
fun GlobalActions(onAllOn: () -> Unit, onAllOff: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            onClick = onAllOn,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFE0E0E0),
                contentColor = Color(0xFF1976D2)
            ),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            Text("Tout allumer")
        }
        Button(
            onClick = onAllOff,
            colors = ButtonDefaults.buttonColors(
                 containerColor = Color(0xFFE0E0E0),
                 contentColor = Color(0xFF1976D2)
            ),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            Text("Tout éteindre")
        }
    }
}

@Composable
fun RoomCard(room: RoomLighting, onError: (String) -> Unit) {
    var expanded by remember { mutableStateOf(true) }
    
    // Group lights
    val directLights = room.lights.filter { !it.isIndirect }
    val indirectLights = room.lights.filter { it.isIndirect }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5)), // Very light grey/white
        shape = RoundedCornerShape(12.dp)
    ) {
        Column {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Room Icon
                Icon(
                    imageVector = getRoomIcon(room.name),
                    contentDescription = null,
                    tint = Color(0xFF1976D2),
                    modifier = Modifier.size(24.dp)
                )
                
                Spacer(modifier = Modifier.width(16.dp))
                
                // Title and Count
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = room.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "${room.lights.size} lampes",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }

                // Quick Actions in Header
                QuickActionButton(
                    icon = Icons.Default.WbSunny,
                    color = Color(0xFF4CAF50), // Green
                    onClick = { /* Group On */ }
                )
                Spacer(modifier = Modifier.width(8.dp))
                QuickActionButton(
                    icon = Icons.Default.NightlightRound,
                    color = Color(0xFF3F51B5), // Indigo
                    onClick = { /* Group Off */ }
                )
                Spacer(modifier = Modifier.width(8.dp))
                
                // Expand Icon
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = Color.Gray
                )
            }

            // Expanded Content
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                    
                    if (directLights.isNotEmpty()) {
                        if (indirectLights.isNotEmpty()) {
                            SectionHeader("Directes")
                        }
                        directLights.forEach { light ->
                            LightRow(light, onError)
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }

                    if (indirectLights.isNotEmpty()) {
                        if (directLights.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            SectionHeader("Indirectes")
                        }
                        indirectLights.forEach { light ->
                            LightRow(light, onError)
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = Color.Gray,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
fun QuickActionButton(icon: ImageVector, color: Color, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = color,
        modifier = Modifier.size(32.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun LightRow(light: LightingItem, onError: (String) -> Unit) {
    val scope = rememberCoroutineScope()

    Surface(
        color = Color.White, // White background for row inside grey card
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp)
        ) {
            // Icon
            Icon(
                imageVector = if (light.isIndirect) Icons.Outlined.Lightbulb else Icons.Default.Lightbulb,
                contentDescription = null,
                tint = if (light.isIndirect) Color(0xFFFFB74D) else Color(0xFFFFD54F),
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Text(
                text = light.name,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )

            // Buttons
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton(
                    text = "On",
                    icon = Icons.Default.WbSunny,
                    backgroundColor = Color(0xFF4CAF50), // Green
                    onClick = { sendAction(light.onIndex, light.value, onError) }
                )
                
                PillButton(
                    text = "Off",
                    icon = Icons.Default.NightlightRound,
                    backgroundColor = Color(0xFFE0E0E0), // Light Grey
                    contentColor = Color(0xFF3F51B5), // Indigo Text
                    onClick = { sendAction(light.offIndex, light.value, onError) }
                )
            }
        }
    }
}

@Composable
fun PillButton(
    text: String,
    icon: ImageVector,
    backgroundColor: Color,
    contentColor: Color = Color.White,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = backgroundColor,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = contentColor
            )
        }
    }
}


fun getRoomIcon(name: String): ImageVector {
    return when {
        name.contains("Salon", ignoreCase = true) -> Icons.Rounded.Chair // Or Weekend if available
        name.contains("Cuisine", ignoreCase = true) -> Icons.Rounded.Kitchen
        name.contains("Manger", ignoreCase = true) -> Icons.Rounded.Dining
        name.contains("Chambre", ignoreCase = true) -> Icons.Rounded.Bed
        name.contains("Bureau", ignoreCase = true) -> Icons.Rounded.Computer
        else -> Icons.Default.Home
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
