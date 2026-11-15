@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.aowink.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mikepenz.markdown.m3.Markdown

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(background = Color.Black, surface = Color(0xFF171717), primary = Color(0xFF3B82F6))) {
                AowinkScreen()
            }
        }
    }
}

@Composable
fun AowinkScreen(viewModel: AowinkViewModel = viewModel()) {
    val context = LocalContext.current
    var input by remember { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(false) }
    
    val messages by viewModel.messages.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    
    // Simpan API Key di memori HP
    var apiKey by remember { mutableStateOf(context.getSharedPreferences("aowink_pref", 0).getString("api_key", "") ?: "") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, messages.lastOrNull()?.content) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    if (showSettings) {
        var tempKey by remember { mutableStateOf(apiKey) }
        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text("Pengaturan") },
            text = {
                Column {
                    Text("Masukkan API Key OpenRouter (sk-or-v1-...)")
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = tempKey, onValueChange = { tempKey = it },
                        modifier = Modifier.fillMaxWidth(), label = { Text("API Key") }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { 
                    apiKey = tempKey
                    context.getSharedPreferences("aowink_pref", 0).edit().putString("api_key", tempKey).apply()
                    showSettings = false 
                }) { Text("Simpan") }
            },
            dismissButton = { TextButton(onClick = { showSettings = false }) { Text("Batal") } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Aowink AI", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { viewModel.clearChat() }) { Text("🗑️", fontSize = 20.sp) }
                    IconButton(onClick = { showSettings = true }) { Icon(Icons.Default.Settings, contentDescription = "Settings") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black, titleContentColor = Color.White)
            )
        },
        bottomBar = {
            Row(modifier = Modifier.fillMaxWidth().background(Color.Black).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = input, onValueChange = { input = it }, modifier = Modifier.weight(1f),
                    placeholder = { Text("Tanya Aowink...") }, shape = RoundedCornerShape(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                FloatingActionButton(
                    onClick = {
                        if (!isLoading && input.isNotBlank()) {
                            viewModel.sendMessage(input.trim(), apiKey)
                            input = ""
                        }
                    },
                    shape = CircleShape, containerColor = if (isLoading) Color.Gray else MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.Send, contentDescription = "Kirim", tint = Color.White)
                }
            }
        }
    ) { padding ->
        if (messages.isEmpty()) {
            Column(modifier = Modifier.fillMaxSize().padding(padding), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Halo! Saya Aowink.", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("Buka Pengaturan (kanan atas) untuk memasukkan API Key.", fontSize = 12.sp, color = Color.Gray)
            }
        } else {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
                itemsIndexed(messages) { _, msg ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = if (msg.role == "user") Arrangement.End else Arrangement.Start) {
                        Box(modifier = Modifier.widthIn(max = 300.dp).background(if (msg.role == "user") Color(0xFF2F2F2F) else Color.Transparent, RoundedCornerShape(16.dp)).padding(if (msg.role == "user") 12.dp else 4.dp)) {
                            if (msg.role == "user") {
                                Text(msg.content, color = Color.White, fontSize = 15.sp)
                            } else {
                                if (msg.content.isNotBlank()) Markdown(content = msg.content)
                            }
                        }
                    }
                }
            }
        }
    }
}
