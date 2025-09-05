package com.example.pushapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Intent
import com.example.pushapp.utils.AppLogger
import com.example.pushapp.utils.LogExporter
import com.example.pushapp.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogViewerScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val logExporter = remember { LogExporter(context) }
    var selectedLevel by remember { mutableStateOf<AppLogger.Level?>(null) }
    var selectedTag by remember { mutableStateOf<String?>(null) }
    var logs by remember { mutableStateOf("") }
    var showExportDialog by remember { mutableStateOf(false) }
    var showCopySuccess by remember { mutableStateOf(false) }
    
    // Get available tags from logs
    val availableTags = remember {
        AppLogger.getLogs().map { it.tag }.distinct().sorted()
    }
    
    // Update logs when filters change
    LaunchedEffect(selectedLevel, selectedTag) {
        logs = AppLogger.getLogsAsString(selectedLevel, selectedTag)
    }
    
    // Initial load
    LaunchedEffect(Unit) {
        logs = AppLogger.getLogsAsString()
    }
    
    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // Top bar
        TopAppBar(
            title = { Text("App Logs") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, "Back")
                }
            },
            actions = {
                IconButton(
                    onClick = {
                        showExportDialog = true
                    }
                ) {
                    Icon(Icons.Default.Share, "Export Logs")
                }
                
                IconButton(
                    onClick = {
                        AppLogger.clearLogs()
                        logs = ""
                    }
                ) {
                    Icon(Icons.Default.Clear, "Clear Logs")
                }
            }
        )
        
        // Filter controls
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = LightGrey)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Filters",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Level filter
                Text(
                    text = "Log Level:",
                    style = MaterialTheme.typography.bodyMedium
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedLevel == null,
                        onClick = { selectedLevel = null },
                        label = { Text("All") }
                    )
                    
                    AppLogger.Level.values().forEach { level ->
                        FilterChip(
                            selected = selectedLevel == level,
                            onClick = { selectedLevel = level },
                            label = { Text(level.name) }
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Tag filter
                Text(
                    text = "Tag:",
                    style = MaterialTheme.typography.bodyMedium
                )
                
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = selectedTag == null,
                            onClick = { selectedTag = null },
                            label = { Text("All") }
                        )
                    }
                    
                    items(availableTags) { tag ->
                        FilterChip(
                            selected = selectedTag == tag,
                            onClick = { selectedTag = tag },
                            label = { Text(tag) }
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Log count
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Total Logs: ${AppLogger.getLogCount()}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    
                    Button(
                        onClick = {
                            logs = AppLogger.getLogsAsString(selectedLevel, selectedTag)
                        }
                    ) {
                        Icon(Icons.Default.Refresh, "Refresh")
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Refresh")
                    }
                }
            }
        }
        
        // Logs display
        Card(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Logs header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(PrimaryRed.copy(alpha = 0.1f))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Log Entries",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (logs.isNotEmpty()) {
                            Text(
                                text = "${logs.lines().size} lines",
                                style = MaterialTheme.typography.bodySmall
                            )
                            
                            IconButton(
                                onClick = {
                                    val success = logExporter.copyLogsToClipboard(logs)
                                    showCopySuccess = success
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Copy logs",
                                    tint = PrimaryRed
                                )
                            }
                        }
                    }
                }
                
                // Logs content
                if (logs.isNotEmpty()) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp)
                    ) {
                        item {
                            Text(
                                text = logs,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                lineHeight = 14.sp,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "No Logs",
                                modifier = Modifier.size(48.dp),
                                tint = Color.Gray
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No logs available",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }
        }
    }
    
    // Export Dialog
    if (showExportDialog) {
        ExportLogsDialog(
            onDismiss = { showExportDialog = false },
            onCopyToClipboard = {
                val success = logExporter.copyLogsToClipboard(logs)
                showCopySuccess = success
                showExportDialog = false
            },
            onShareLogs = {
                val shareIntent = logExporter.shareLogs(logs)
                context.startActivity(Intent.createChooser(shareIntent, "Share Logs"))
                showExportDialog = false
            },
            onExportToFile = {
                val uri = logExporter.exportLogsToFile(logs)
                if (uri != null) {
                    val shareIntent = logExporter.shareLogs(logs)
                    context.startActivity(Intent.createChooser(shareIntent, "Save Logs"))
                }
                showExportDialog = false
            }
        )
    }
    
    // Copy Success Snackbar
    if (showCopySuccess) {
        LaunchedEffect(showCopySuccess) {
            kotlinx.coroutines.delay(2000)
            showCopySuccess = false
        }
        
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            Card(
                modifier = Modifier.padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF4CAF50))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Logs copied to clipboard!",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun ExportLogsDialog(
    onDismiss: () -> Unit,
    onCopyToClipboard: () -> Unit,
    onShareLogs: () -> Unit,
    onExportToFile: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Export Logs")
        },
        text = {
            Text("Choose how you want to export the logs:")
        },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onCopyToClipboard
                ) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Copy")
                }
                

            
                OutlinedButton(
                    onClick = onShareLogs
                ) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Share")
                }
                
                Button(
                    onClick = onExportToFile
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Save")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { 
                Text("Cancel")
            }
        }
    )
}
