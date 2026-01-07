package com.essensys.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AvTimer
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.essensys.android.data.EssensysAPI

enum class WateringMode(val id: String, val label: String, val icon: ImageVector) {
    AUTO("auto", "Auto", Icons.Default.CalendarMonth),
    OFF("off", "OFF", Icons.Default.PowerSettingsNew),
    FORCE_15("force15", "15 min", Icons.Default.WaterDrop),
    FORCE_30("force30", "30 min", Icons.Default.WaterDrop), // Could use a variation
    FORCE_60("force60", "1h", Icons.Default.Cloud)
}

@Composable
fun WateringView(navController: NavController) {
     val scrollState = rememberScrollState()
     var selectedMode by remember { mutableStateOf(WateringMode.OFF) }
     
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
            text = "Arrosage",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF2D3748)
        )
        
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
             Column(modifier = Modifier.padding(16.dp)) {
                 Text(
                    text = "Arrosage",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Divider(modifier = Modifier.padding(vertical = 12.dp))
                
                // Row 1: Auto / OFF
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    WateringButton(mode = WateringMode.AUTO, currentMode = selectedMode, modifier = Modifier.weight(1f)) { selectedMode = it }
                    WateringButton(mode = WateringMode.OFF, currentMode = selectedMode, modifier = Modifier.weight(1f)) { selectedMode = it }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                Divider()
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Forçage",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                 // Row 2: Forcings
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    WateringButton(mode = WateringMode.FORCE_15, currentMode = selectedMode, modifier = Modifier.weight(1f)) { selectedMode = it }
                    WateringButton(mode = WateringMode.FORCE_30, currentMode = selectedMode, modifier = Modifier.weight(1f)) { selectedMode = it }
                    WateringButton(mode = WateringMode.FORCE_60, currentMode = selectedMode, modifier = Modifier.weight(1f)) { selectedMode = it }
                }
             }
        }
    }
}

@Composable
fun WateringButton(
    mode: WateringMode, 
    currentMode: WateringMode, 
    modifier: Modifier = Modifier,
    onClick: (WateringMode) -> Unit
) {
    val isSelected = currentMode == mode
    
    Button(
        onClick = { onClick(mode) },
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) Color(0xFFE3F2FD) else Color.White,
            contentColor = if (isSelected) Color(0xFF1976D2) else Color.Gray
        ),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
            .height(80.dp)
            .border(1.dp, if (isSelected) Color(0xFF1976D2) else Color.Gray.copy(alpha = 0.2f), RoundedCornerShape(8.dp)),
        contentPadding = PaddingValues(4.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(imageVector = mode.icon, contentDescription = null, modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = mode.label, 
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
