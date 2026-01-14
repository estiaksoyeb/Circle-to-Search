package com.akslabs.circletosearch.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.TextFormat
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.graphics.Bitmap

@Composable
fun BottomControlBar(
    selectedBitmap: Bitmap?,
    isTextSelectionMode: Boolean,
    isLensOnlyMode: Boolean,
    onExpandSheet: () -> Unit,
    onToggleTextSelection: () -> Unit,
    onGoogleLensClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .navigationBarsPadding()
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 24.dp)
            .shadow(8.dp, CircleShape)
            .background(Color(0xFF1F1F1F), CircleShape)
            .height(64.dp)
            .padding(horizontal = 20.dp)
            .pointerInput(Unit) {
                detectTapGestures {
                    if (!isLensOnlyMode && selectedBitmap != null) {
                        onExpandSheet()
                    }
                }
            }
    ) {
        Row(
            modifier = Modifier.align(Alignment.CenterStart),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectedBitmap != null) {
                Image(
                    bitmap = selectedBitmap.asImageBitmap(),
                    contentDescription = "Selected",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, Color.Gray, RoundedCornerShape(12.dp))
                )
            } else {
                // G Logo
                Row {
                    Text("G", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold, color = Color(0xFF4285F4)))
                    Text("o", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold, color = Color(0xFFEA4335)))
                    Text("o", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold, color = Color(0xFFFBBC05)))
                    Text("g", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold, color = Color(0xFF4285F4)))
                    Text("l", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold, color = Color(0xFF34A853)))
                    Text("e", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold, color = Color(0xFFEA4335)))
                }
            }
        }
        
        // Action Buttons (Right)
        Row(
            modifier = Modifier.align(Alignment.CenterEnd),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Select Text Button
            IconButton(
                onClick = onToggleTextSelection,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(androidx.compose.ui.graphics.Brush.linearGradient(com.akslabs.circletosearch.ui.theme.OverlayGradientColors))
            ) {
                Icon(
                    imageVector = Icons.Default.TextFormat,
                    contentDescription = "Select Text",
                    tint = if (isTextSelectionMode) Color.Black else Color.White
                )
            }
            
            Spacer(modifier = Modifier.width(8.dp))

            // Google Lens Button (Full Screenshot)
            IconButton(
                onClick = onGoogleLensClick,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(androidx.compose.ui.graphics.Brush.linearGradient(com.akslabs.circletosearch.ui.theme.OverlayGradientColors))
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = "Google Lens",
                    tint = Color.White
                )
            }
        }
    }
}