package com.akslabs.circletosearch.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.akslabs.circletosearch.data.SearchEngine

@Composable
fun TopControlBar(
    selectedEngine: SearchEngine,
    desktopModeEngines: Set<SearchEngine>,
    isDarkMode: Boolean,
    showGradientBorder: Boolean,
    onClose: () -> Unit,
    onToggleDesktopMode: () -> Unit,
    onToggleDarkMode: () -> Unit,
    onToggleGradientBorder: () -> Unit,
    onRefresh: () -> Unit,
    onCopyUrl: () -> Unit,
    onOpenInBrowser: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = 16.dp, start = 16.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .background(Color.Gray.copy(alpha = 0.5f), CircleShape)
                .size(40.dp)
        ) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
        }
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = selectedEngine.name,
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        )
        Spacer(modifier = Modifier.weight(1f))
        
        Box(
            modifier = Modifier
                .background(Color.Gray.copy(alpha = 0.5f), CircleShape)
                .size(40.dp),
            contentAlignment = Alignment.Center
        ) {
            var showMenu by remember { mutableStateOf(false) }
            IconButton(onClick = { showMenu = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "Menu", tint = Color.White)
            }
        
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                val isDesktop = desktopModeEngines.contains(selectedEngine)
                DropdownMenuItem(
                    text = { Text(if (isDesktop) "Mobile Mode" else "Desktop Mode") },
                    leadingIcon = { Icon(if (isDesktop) Icons.Default.Smartphone else Icons.Default.DesktopWindows, null) },
                    onClick = { onToggleDesktopMode(); showMenu = false }
                )
                DropdownMenuItem(
                    text = { Text(if (isDarkMode) "Light Mode" else "Dark Mode") },
                    leadingIcon = { Icon(if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode, null) },
                    onClick = { onToggleDarkMode(); showMenu = false }
                )
                DropdownMenuItem(
                    text = { Text(if (showGradientBorder) "Hide Border" else "Show Border") },
                    leadingIcon = { Icon(Icons.Default.BorderOuter, null) },
                    onClick = { onToggleGradientBorder(); showMenu = false }
                )
                DropdownMenuItem(
                    text = { Text("Refresh") },
                    leadingIcon = { Icon(Icons.Default.Refresh, null) },
                    onClick = { onRefresh(); showMenu = false }
                )
                DropdownMenuItem(
                    text = { Text("Copy URL") },
                    leadingIcon = { Icon(Icons.Default.ContentCopy, null) },
                    onClick = { onCopyUrl(); showMenu = false }
                )
                DropdownMenuItem(
                    text = { Text("Open in Browser") },
                    leadingIcon = { Icon(if (true) Icons.Default.OpenInNew else Icons.Default.OpenInNew, null) }, // Fixed redundant check
                    onClick = { onOpenInBrowser(); showMenu = false }
                )
                DropdownMenuItem(
                    text = { Text("Settings") },
                    leadingIcon = { Icon(Icons.Default.Settings, null) },
                    onClick = { onOpenSettings(); showMenu = false }
                )
            }
        }
    }
}