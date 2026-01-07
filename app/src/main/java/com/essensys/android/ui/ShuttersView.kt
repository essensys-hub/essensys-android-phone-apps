package com.essensys.android.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Data Models
data class ShutterItem(
    val name: String,
    val label: String,
    val upIndex: Int,
    val downIndex: Int,
    val value: String = "1"
)

data class RoomShutters(
    val name: String,
    val shutters: List<ShutterItem>
)

// Grouped Data
val shuttersData = listOf(
    RoomShutters("Salon", listOf(
        ShutterItem("volet1salon", "Volet 1", 617, 620, "1"),
        ShutterItem("volet2salon", "Volet 2", 617, 620, "2"),
        ShutterItem("volet3salon", "Volet 3", 617, 620, "4")
    )),
    RoomShutters("Salle à Manger", listOf(
        ShutterItem("volet1salleamanger", "Volet 1", 617, 620, "8"),
        ShutterItem("volet2salleamanger", "Volet 2", 617, 620, "16")
    )),
    RoomShutters("Cuisine", listOf(
        ShutterItem("volet1cuisine", "Volet 1", 619, 622, "1"),
        ShutterItem("volet2cuisine", "Volet 2", 619, 622, "2")
    )),
    RoomShutters("Salle de Bain", listOf(
        ShutterItem("volet1sdb", "Salle de Bain 1", 619, 622, "4")
    )),
    RoomShutters("Grande Chambre", listOf(
        ShutterItem("volet1gdchamb", "Volet 1", 618, 621, "1"),
        ShutterItem("volet2gdchamb", "Volet 2", 618, 621, "2")
    )),
    RoomShutters("Petites Chambres", listOf(
        ShutterItem("volet1ptchamb", "Chambre 1", 618, 621, "4"),
        ShutterItem("volet2ptchamb", "Chambre 2", 618, 621, "8"),
        ShutterItem("volet3ptchamb", "Chambre 3", 618, 621, "16")
    )),
    RoomShutters("Bureau", listOf(
        ShutterItem("voletbureau", "Bureau", 617, 620, "32")
    ))
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShuttersView(navController: NavController) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val expandedStates = remember { mutableStateMapOf<String, Boolean>() }

    Scaffold(
        containerColor = Color(0xFFFAFAFA),
        topBar = {
             CenterAlignedTopAppBar(
                title = { 
                     Text("Volets", fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Retour")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.White)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
             // Info Banner
            item {
                InfoBanner()
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
                        onClick = { 
                            shuttersData.forEach { room -> 
                                room.shutters.forEach { s ->
                                    sendShutterAction(s.upIndex, s.value) {}
                                }
                            }
                        }
                    )
                    Spacer(modifier = Modifier.width(24.dp))
                    GlobalShutterButton(
                        text = "Tout Fermer",
                        icon = Icons.Default.ArrowDownward,
                        color = Color(0xFF1976D2), // Blue
                        onClick = { 
                            shuttersData.forEach { room -> 
                                room.shutters.forEach { s ->
                                    sendShutterAction(s.downIndex, s.value) {}
                                }
                            }
                        }
                    )
                }
            }

            items(shuttersData) { room ->
                val isExpanded = expandedStates[room.name] ?: false
                ExpandableRoomShutterCard(
                    room = room,
                    isExpanded = isExpanded,
                    onExpandChange = { expandedStates[room.name] = it },
                    onError = { msg ->
                        scope.launch { snackbarHostState.showSnackbar(msg) }
                    }
                )
            }
        }
    }
}

@Composable
fun ExpandableRoomShutterCard(
    room: RoomShutters, 
    isExpanded: Boolean,
    onExpandChange: (Boolean) -> Unit,
    onError: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5)), 
        shape = RoundedCornerShape(12.dp)
    ) {
        Column {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onExpandChange(!isExpanded) }
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
                        text = "${room.shutters.size} volets",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }

                // Quick Actions (Group)
                Surface(
                    onClick = { room.shutters.forEach { sendShutterAction(it.upIndex, it.value, onError) } },
                    shape = CircleShape,
                    color = Color(0xFF4CAF50),
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                         Icon(Icons.Default.ArrowUpward, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                 Surface(
                    onClick = { room.shutters.forEach { sendShutterAction(it.downIndex, it.value, onError) } },
                    shape = CircleShape,
                    color = Color(0xFF1976D2),
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                         Icon(Icons.Default.ArrowDownward, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                
                // Expand Icon
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = Color.Gray
                )
            }

            // Expanded Content
            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                    room.shutters.forEach { shutter ->
                        ShutterItemRow(shutter, onError)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun ShutterItemRow(shutter: ShutterItem, onError: (String) -> Unit) {
    var isCooldown by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Surface(
        color = Color.White,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp)
        ) {
            Icon( // Simple shutter icon
                imageVector = Icons.Default.TableRows, // Placeholder
                contentDescription = null,
                tint = Color.Gray,
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Text(
                text = shutter.label,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )

            // Buttons
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // UP
                Surface(
                    onClick = {
                        if (!isCooldown) {
                            sendShutterAction(shutter.upIndex, shutter.value, onError)
                            // Cooldown
                            scope.launch { isCooldown = true; delay(3000); isCooldown = false }
                        }
                    },
                    shape = RoundedCornerShape(50),
                    color = if (isCooldown) Color.Gray.copy(alpha=0.3f) else Color(0xFF4CAF50),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.ArrowUpward, null, tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                }
                
                // DOWN
                Surface(
                    onClick = {
                        if (!isCooldown) {
                            sendShutterAction(shutter.downIndex, shutter.value, onError)
                            // Cooldown
                            scope.launch { isCooldown = true; delay(3000); isCooldown = false }
                        }
                    },
                    shape = RoundedCornerShape(50),
                    color = if (isCooldown) Color.Gray.copy(alpha=0.3f) else Color(0xFF1976D2),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                         Icon(Icons.Default.ArrowDownward, null, tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun GlobalShutterButton(text: String, icon: ImageVector, color: Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = color),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.size(width = 100.dp, height = 80.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
             Icon(imageVector = icon, contentDescription = null)
             Spacer(modifier = Modifier.height(4.dp))
             Text(text, style = MaterialTheme.typography.labelSmall)
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
