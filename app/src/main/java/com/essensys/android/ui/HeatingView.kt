package com.essensys.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.essensys.android.data.EssensysAPI

// ---- Models ----

enum class HeatingMode(val label: String, val color: Color, val iconEmoji: String) {
    CONFORT("Confort", Color(0xFFFF9800), "🔥"),
    ECO("Éco", Color(0xFF4CAF50), "🍃"),
    HORS_GEL("Hors Gel", Color(0xFF9E9E9E), "❄️"),
    ARRET("Arrêt", Color(0xFF607D8B), "⏻")
}

data class HeatingZone(
    val id: String,
    val name: String,
    var currentMode: HeatingMode = HeatingMode.CONFORT
)

enum class CumulusMode(val label: String, val icon: ImageVector) {
    ON_AUTONOME("ON (autonome)", Icons.Default.Bolt),
    SUIVI_HPHC("Suivi HP/HC", Icons.Default.Schedule),
    OFF("OFF", Icons.Default.PowerSettingsNew)
}

// ---- View ----

@Composable
fun HeatingView(navController: NavController) {
    val scrollState = rememberScrollState()
    
    // Mock Data
    var zones by remember { mutableStateOf(listOf(
        HeatingZone("jour", "Chauffage Zone Jour", HeatingMode.CONFORT),
        HeatingZone("nuit", "Chauffage Zone Nuit", HeatingMode.ECO),
        HeatingZone("sdb1", "Chauffage Salle de bain 1", HeatingMode.CONFORT),
        HeatingZone("sdb2", "Chauffage Salle de bain 2", HeatingMode.CONFORT)
    )) }
    
    var cumulusMode by remember { mutableStateOf(CumulusMode.ON_AUTONOME) }
    
    // Purple color for Cumulus
    val purpleColor = Color(0xFF805AD5)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        
        // Info Banner
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFE3F2FD), RoundedCornerShape(8.dp))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF2196F3))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Essensys fonctionne en boucle ouverte : les équipements ne remontent pas leur état.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF1565C0)
            )
        }
        
        Text(
            text = "Chauffage",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF2D3748)
        )

        // Heating Zones
        zones.forEachIndexed { index, zone ->
            HeatingZoneCard(
                zone = zone,
                onModeSelected = { newMode ->
                    val updatedZones = zones.toMutableList()
                    updatedZones[index] = zone.copy(currentMode = newMode)
                    zones = updatedZones
                    // TODO: API Call
                }
            )
        }
        
        // Cumulus Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            shape = RoundedCornerShape(12.dp),
             modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.WaterDrop,
                        contentDescription = null,
                        tint = purpleColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Cumulus",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    
                     Text(
                        text = cumulusMode.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (cumulusMode == CumulusMode.OFF) Color.Gray else purpleColor,
                        modifier = Modifier
                            .background(
                                if (cumulusMode == CumulusMode.OFF) Color.Gray.copy(alpha = 0.1f) else purpleColor.copy(alpha = 0.1f), 
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                
                Divider(modifier = Modifier.padding(vertical = 12.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CumulusMode.values().forEach { mode ->
                         val isSelected = cumulusMode == mode
                         val activeColor = if (mode == CumulusMode.OFF) Color.Gray else purpleColor
                         
                         Button(
                             onClick = { cumulusMode = mode },
                             colors = ButtonDefaults.buttonColors(
                                 containerColor = if (isSelected) activeColor.copy(alpha = 0.15f) else Color.White,
                                 contentColor = if (isSelected) activeColor else Color.Gray
                             ),
                             shape = RoundedCornerShape(8.dp),
                             modifier = Modifier
                                 .weight(1f)
                                 .height(70.dp) // Fixed height for alignment
                                 .border(1.dp, if (isSelected) activeColor else Color.Gray.copy(alpha = 0.2f), RoundedCornerShape(8.dp)),
                             contentPadding = PaddingValues(4.dp)
                         ) {
                             Column(
                                 horizontalAlignment = Alignment.CenterHorizontally,
                                 verticalArrangement = Arrangement.Center
                             ) {
                                 Icon(imageVector = mode.icon, contentDescription = null)
                                 Spacer(modifier = Modifier.height(4.dp))
                                 Text(
                                     text = mode.label, 
                                     style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                     textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                     maxLines = 2,
                                     lineHeight = 12.sp
                                 )
                             }
                         }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(30.dp))
    }
}

@Composable
fun HeatingZoneCard(zone: HeatingZone, onModeSelected: (HeatingMode) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "🌡️", // Using emoji as simple icon similar to iOS
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = zone.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                 Spacer(modifier = Modifier.weight(1f))
                 Text(
                    text = zone.currentMode.label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = zone.currentMode.color,
                    modifier = Modifier
                        .background(zone.currentMode.color.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            
            Divider(modifier = Modifier.padding(vertical = 12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HeatingMode.values().forEach { mode ->
                    val isSelected = zone.currentMode == mode
                    
                    Button(
                        onClick = { onModeSelected(mode) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSelected) mode.color.copy(alpha = 0.15f) else Color.White,
                            contentColor = if (isSelected) mode.color else Color.Gray
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(80.dp)
                            .border(1.dp, if (isSelected) mode.color else Color.Gray.copy(alpha = 0.2f), RoundedCornerShape(8.dp)),
                        contentPadding = PaddingValues(4.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = mode.iconEmoji, style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = mode.label, 
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}
