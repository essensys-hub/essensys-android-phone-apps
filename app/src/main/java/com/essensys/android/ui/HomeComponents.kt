package com.essensys.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chair
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DoorBack
import androidx.compose.material.icons.filled.NightlightRound
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.essensys.android.data.EssensysAPI

// --- Data Models ---
data class LightingScene(
    val id: String,
    val name: String,
    val description: String,
    val icon: ImageVector,
    val iconColor: Color,
    val onIndex: Int,
    val value: String = "1"
)

// Sample Data
val homeScenes = listOf(
    LightingScene("reveil", "Réveil", "Allume : Cuisine, Salon indirect 1", Icons.Default.WbSunny, Color(0xFF2196F3), 600), // Fake IDs
    LightingScene("soiree", "Soirée", "Allume : Salon indirect 1 & 2", Icons.Default.Chair, Color(0xFF2196F3), 601),
    LightingScene("nuit", "Nuit", "Éteint : tout", Icons.Default.NightlightRound, Color(0xFF1565C0), 602),
    LightingScene("depart", "Départ", "Éteint : tout sauf le couloir", Icons.Default.DoorBack, Color(0xFF1565C0), 603)
)

// --- Components ---

@Composable
fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
fun LightingSceneRow(scene: LightingScene, onError: (String) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5)), // Light grey bg
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = scene.icon,
                contentDescription = null,
                tint = scene.iconColor,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = scene.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(text = scene.description, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
            
            Button(
                onClick = { sendAction(scene.onIndex, scene.value, onError) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)), // Green
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                modifier = Modifier.height(36.dp)
            ) {
                Text("Allumer", fontSize = 14.sp)
            }
        }
    }
}

@Composable
fun HeatingCard() {
    Column {
        SectionTitle("Chauffage")
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Zones de Chauffage", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("19", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Normal)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("°", style = MaterialTheme.typography.displaySmall, color = Color.Gray)
                    
                    Spacer(modifier = Modifier.width(20.dp))
                    // Fake Bars
                     Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                         Box(modifier = Modifier.width(30.dp).height(4.dp).background(Color(0xFF90CAF9)))
                         Box(modifier = Modifier.width(30.dp).height(4.dp).background(Color(0xFF90CAF9)))
                         Box(modifier = Modifier.width(30.dp).height(4.dp).background(Color(0xFF90CAF9)))
                         Box(modifier = Modifier.width(30.dp).height(4.dp).background(Color(0xFFE0E0E0)))
                         Box(modifier = Modifier.width(30.dp).height(4.dp).background(Color(0xFFE0E0E0)))
                         Box(modifier = Modifier.width(30.dp).height(4.dp).background(Color(0xFFE0E0E0)))
                     }
                }
                 Spacer(modifier = Modifier.height(24.dp))
                 
                 Text(
                     "Zone Jour : 7h00 - 21h00 - Température cible à maintenir",
                     style = MaterialTheme.typography.bodySmall,
                     color = Color.Gray
                 )
                 
                 Spacer(modifier = Modifier.height(16.dp))
                 
                 Row(
                     modifier = Modifier.fillMaxWidth(),
                     horizontalArrangement = Arrangement.SpaceBetween,
                     verticalAlignment = Alignment.CenterVertically
                 ) {
                     Row(verticalAlignment = Alignment.CenterVertically) {
                         // Thermometer icon placeholder
                         Text("🌡️", fontSize = 20.sp)
                         Spacer(modifier = Modifier.width(8.dp))
                         Text("Pendant la zone Jour :", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                     }
                     
                     Surface(
                         color = Color(0xFF4CAF50),
                         shape = RoundedCornerShape(16.dp)
                     ) {
                         Text(
                             "10.4 ?", // Original screenshot value
                             color = Color.White,
                             fontSize = 12.sp,
                             fontWeight = FontWeight.Bold,
                             modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                         )
                     }
                 }
                 
                 Spacer(modifier = Modifier.height(12.dp))
                 Text(
                     "Précédente : Ordre < Activer Départ - envoyé hier à 21:48",
                     style = MaterialTheme.typography.labelSmall,
                     color = Color.LightGray
                 )
            }
        }
    }
}

@Composable
fun ConfigurationSummaryCard(navController: NavController) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
            .clickable { navController.navigate("settings") }
    ) {
         Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
             horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Configuration Backend", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                 Text(
                     if (EssensysAPI.isWanMode) "Mode: Distant (WAN)" else "Mode: Local (WiFi)",
                     style = MaterialTheme.typography.bodySmall,
                     color = if (EssensysAPI.isWanMode) Color(0xFF1565C0) else Color(0xFF2E7D32)
                 )
            }
             Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Gray)
        }
    }
}
