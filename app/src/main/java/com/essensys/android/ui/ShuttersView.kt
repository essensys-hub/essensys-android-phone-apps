package com.essensys.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
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

data class ShutterItem(
    val name: String,
    val openIndex: Int,
    val closeIndex: Int,
    val value: String
)

val shuttersData = listOf(
    ShutterItem("Volet 1 Salon", 617, 620, "1"),
    ShutterItem("Volet 2 Salon", 617, 620, "2"),
    ShutterItem("Volet Cuisine", 619, 622, "1"),
    ShutterItem("Grande Chambre", 618, 621, "1")
    // Add more as needed
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShuttersView(navController: NavController) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Volets") },
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
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(shuttersData) { shutter ->
                ShutterRow(shutter, onError = { msg ->
                    scope.launch { snackbarHostState.showSnackbar(msg) }
                })
                Divider()
            }
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
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = shutter.name,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            IconButton(
                onClick = {
                    sendShutterAction(shutter.openIndex, shutter.value, onError)
                    scope.launch {
                        isCooldown = true
                        delay(1000) // 1 second cooldown
                        isCooldown = false
                    }
                },
                enabled = !isCooldown,
                colors = IconButtonDefaults.iconButtonColors(contentColor = Color(0xFF4CAF50))
            ) {
                Icon(Icons.Default.ArrowUpward, contentDescription = "Ouvrir")
            }

            IconButton(
                onClick = {
                    sendShutterAction(shutter.closeIndex, shutter.value, onError)
                    scope.launch {
                        isCooldown = true
                        delay(1000) // 1 second cooldown
                        isCooldown = false
                    }
                },
                enabled = !isCooldown,
                colors = IconButtonDefaults.iconButtonColors(contentColor = Color(0xFF2196F3))
            ) {
                Icon(Icons.Default.ArrowDownward, contentDescription = "Fermer")
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
