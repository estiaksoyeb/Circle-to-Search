package com.akslabs.circletosearch.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import com.akslabs.circletosearch.data.SearchEngine

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ContainedLoadingIndicatorSample() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        androidx.compose.material3.ContainedLoadingIndicator()
    }
}

fun getEngineIcon(engine: SearchEngine): ImageVector {
    return when (engine) {
        SearchEngine.Google -> Icons.Default.Search
        SearchEngine.Bing -> Icons.Default.TravelExplore
        SearchEngine.Yandex -> Icons.Default.Language
        SearchEngine.TinEye -> Icons.Default.Visibility
        SearchEngine.Perplexity -> Icons.Default.Psychology
        SearchEngine.ChatGPT -> Icons.Default.Chat
    }
}
