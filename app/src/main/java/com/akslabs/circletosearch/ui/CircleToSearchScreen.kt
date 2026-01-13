fun getEngineIcon(engine: SearchEngine): androidx.compose.ui.graphics.vector.ImageVector {
    return when (engine) {
        SearchEngine.Google -> Icons.Default.Search
        SearchEngine.Bing -> Icons.Default.TravelExplore
        SearchEngine.Yandex -> Icons.Default.Language
        SearchEngine.TinEye -> Icons.Default.Visibility
        SearchEngine.Perplexity -> Icons.Default.Psychology
        SearchEngine.ChatGPT -> Icons.Default.Chat
    }
}