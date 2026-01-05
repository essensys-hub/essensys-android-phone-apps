package com.essensys.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

@Composable
fun AlarmView(navController: NavController) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFAFAFA)), // Match app bg
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
             Icon(
                 imageVector = Icons.Default.Warning, 
                 contentDescription = "Ah l'alarme...", 
                 tint = Color.Gray,
                 modifier = Modifier.size(48.dp)
             )
             Spacer(modifier = Modifier.height(16.dp))
             Text(
                 text = "Alarme", 
                 style = MaterialTheme.typography.headlineMedium, 
                 fontWeight = FontWeight.Bold,
                 color = Color.Gray
             )
             Spacer(modifier = Modifier.height(8.dp))
             Surface(
                 color = Color.LightGray.copy(alpha=0.3f),
                 shape = RoundedCornerShape(8.dp)
             ) {
                 Text(
                     "En cours de développement", 
                     modifier = Modifier.padding(16.dp),
                     color = Color.Gray
                 )
             }
        }
    }
}
