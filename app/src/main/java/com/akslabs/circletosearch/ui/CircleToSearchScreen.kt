/*
 *
 *  * Copyright (C) 2025 AKS-Labs (original author)
 *  *
 *  * This program is free software: you can redistribute it and/or modify
 *  * it under the terms of the GNU General Public License as published by
 *  * the Free Software Foundation, either version 3 of the License, or
 *  * (at your option) any later version.
 *  *
 *  * This program is distributed in the hope that it will be useful,
 *  * but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  * GNU General Public License for more details.
 *  *
 *  * You should have received a copy of the GNU General Public License
 *  * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

package com.akslabs.circletosearch.ui

import android.graphics.Bitmap
import android.graphics.Rect
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.akslabs.circletosearch.data.SearchEngine
import com.akslabs.circletosearch.data.TextNode
import com.akslabs.circletosearch.data.TextRepository
import com.akslabs.circletosearch.data.isDirectUpload
import com.akslabs.circletosearch.ui.components.FriendlyMessageBubble
import com.akslabs.circletosearch.ui.components.searchWithGoogleLens
import com.akslabs.circletosearch.ui.theme.OverlayGradientColors
import com.akslabs.circletosearch.utils.FriendlyMessageManager
import com.akslabs.circletosearch.utils.ImageSearchUploader
import com.akslabs.circletosearch.utils.ImageUtils
import com.akslabs.circletosearch.utils.UIPreferences
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CircleToSearchScreen(
    screenshot: Bitmap?,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // Initialize preferences
    val uiPreferences = remember { UIPreferences(context) }

    // Text Selection State
    var isTextSelectionMode by remember { mutableStateOf(false) }
    val textNodes = remember { mutableStateListOf<TextNode>() }
    val clipboardManager = LocalClipboardManager.current
    var showTextDialog by remember { mutableStateOf<String?>(null) }

    // Load text nodes once on start
    LaunchedEffect(Unit) {
        val nodes = TextRepository.getTextNodes()
        textNodes.addAll(nodes)
        android.util.Log.d("CircleToSearch", "UI loaded ${nodes.size} text nodes")
    }
    
    // Search Engines Order Logic
    val preferredOrder = remember(uiPreferences.getSearchEngineOrder()) {
        val allEngines = SearchEngine.values()
        val orderString = uiPreferences.getSearchEngineOrder()
        if (orderString == null) allEngines
        else {
            val preferredNames = orderString.split(",")
            val ordered = mutableListOf<SearchEngine>()
            preferredNames.forEach { name ->
                allEngines.find { it.name == name }?.let { ordered.add(it) }
            }
            allEngines.forEach { if (!ordered.contains(it)) ordered.add(it) }
            ordered
        }
    }
    val searchEngines = preferredOrder

    // Support Settings Sheet
    var showSettingsScreen by remember { mutableStateOf(false) }

    // Friendly Message State
    var friendlyMessage by remember { mutableStateOf("") }
    var isMessageVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (uiPreferences.isShowFriendlyMessages()) {
            val manager = FriendlyMessageManager(context)
            friendlyMessage = manager.getNextMessage()
            delay(500) // Small delay for smooth entrance
            isMessageVisible = true
            delay(4000) // Show for 4 seconds
            isMessageVisible = false
        }
    }

    // Search State
    var selectedEngine by remember(searchEngines) { mutableStateOf<SearchEngine>(searchEngines.first()) }
    var searchUrl by remember { mutableStateOf<String?>(null) }
    var hostedImageUrl by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    
    // Desktop Mode - Per Tab
    val initialDesktopMode = uiPreferences.isDesktopMode() // Global default
    var desktopModeEngines by remember { mutableStateOf<Set<SearchEngine>>(if(initialDesktopMode) searchEngines.toSet() else emptySet()) }
    
    var isDarkMode by remember { mutableStateOf(uiPreferences.isDarkMode()) }
    var showGradientBorder by remember { mutableStateOf(uiPreferences.isShowGradientBorder()) }
    
    // Track initialized engines for Smart Loading
    val initializedEngines = remember { mutableStateListOf<SearchEngine>() }
    
    fun isDesktop(engine: SearchEngine) = desktopModeEngines.contains(engine)
    
    LaunchedEffect(isDarkMode) {
        uiPreferences.setDarkMode(isDarkMode)
    }
    
    LaunchedEffect(showGradientBorder) {
        uiPreferences.setShowGradientBorder(showGradientBorder)
    }
    
    // Cache for preloaded URLs
    val preloadedUrls = remember { mutableMapOf<SearchEngine, String>() }
    
    // WebView Cache
    val webViews = remember { mutableMapOf<SearchEngine, WebView>() }
    
    LaunchedEffect(desktopModeEngines) {
        webViews.forEach { (engine, wv) ->
             val isDesktop = desktopModeEngines.contains(engine)
             val newUserAgent = if (isDesktop) {
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            } else {
                "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
            }
            
            if (wv.settings.userAgentString != newUserAgent) {
                wv.settings.userAgentString = newUserAgent
                wv.reload()
            }
        }
    }
    
    LaunchedEffect(isDarkMode) {
        webViews.values.forEach { wv ->
            try {
                if (isDarkMode) {
                    wv.webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            val darkModeCSS = """
                                javascript:(function() {
                                    var style = document.createElement('style');
                                    style.innerHTML = `
                                        html { filter: invert(1) hue-rotate(180deg) !important; background: #000 !important; }
                                        img, video, [style*="background-image"] { filter: invert(1) hue-rotate(180deg) !important; }
                                    `;
                                    document.head.appendChild(style);
                                })()
                            """.trimIndent()
                            view?.loadUrl(darkModeCSS)
                        }
                    }
                } else {
                    wv.webViewClient = WebViewClient()
                }
                wv.reload()
            } catch (e: Exception) {
                android.util.Log.e("CircleToSearch", "Error updating dark mode", e)
            }
        }
    }
    
    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberStandardBottomSheetState(
            initialValue = SheetValue.Hidden,
            skipHiddenState = false
        )
    )

    // Selection State
    var selectedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isSearching by remember { mutableStateOf(false) }
    
    // Helper to create and configure WebView
    fun createWebView(ctx: android.content.Context, engine: SearchEngine): WebView {
        return WebView(ctx).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            
            setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
            
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                allowFileAccess = true
                allowContentAccess = true
                allowFileAccessFromFileURLs = true
                allowUniversalAccessFromFileURLs = true
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                setRenderPriority(WebSettings.RenderPriority.HIGH)
                cacheMode = WebSettings.LOAD_DEFAULT
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
                useWideViewPort = true
                loadWithOverviewMode = true
                
                userAgentString = if (isDesktop(engine)) {
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                } else {
                    "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                }
            }
            
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    if (isDarkMode) {
                        val darkModeCSS = """
                            javascript:(function() {
                                var style = document.createElement('style');
                                style.innerHTML = `
                                    html { filter: invert(1) hue-rotate(180deg) !important; background: #000 !important; }
                                    img, video, [style*="background-image"] { filter: invert(1) hue-rotate(180deg) !important; }
                                `;
                                document.head.appendChild(style);
                            })()
                        """.trimIndent()
                        view?.loadUrl(darkModeCSS)
                    }
                }
            }
            isNestedScrollingEnabled = true
            setOnTouchListener { v, event ->
                when (event.action) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        v.parent.requestDisallowInterceptTouchEvent(true)
                    }
                    android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                        v.parent.requestDisallowInterceptTouchEvent(false)
                    }
                }
                false
            }
        }
    }

    // Back Handler Logic
    BackHandler(enabled = true) {
        val currentWebView = webViews[selectedEngine]
        if (currentWebView != null && currentWebView.canGoBack()) {
            currentWebView.goBack()
        } else if (scaffoldState.bottomSheetState.currentValue == SheetValue.Expanded) {
             scope.launch { scaffoldState.bottomSheetState.partialExpand() }
        } else if (scaffoldState.bottomSheetState.currentValue == SheetValue.PartiallyExpanded) {
             scope.launch { scaffoldState.bottomSheetState.hide() }
        } else {
            onClose()
        }
    }

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp * 0.55f),
        sheetContainerColor = Color(0xFF1F1F1F),
        sheetContentColor = MaterialTheme.colorScheme.onSurface,
        sheetDragHandle = { 
            BottomSheetDefaults.DragHandle(
                color = Color.White.copy(alpha = 0.3f),
                width = 32.dp,
                height = 3.dp
            )
        },
        sheetSwipeEnabled = true,
        sheetContent = {
            SearchResultsSheet(
                searchEngines = searchEngines,
                selectedEngine = selectedEngine,
                initializedEngines = initializedEngines,
                preloadedUrls = preloadedUrls,
                webViews = webViews,
                isLoading = isLoading,
                isDesktop = ::isDesktop,
                isDarkMode = isDarkMode,
                onEngineSelected = { engine ->
                    selectedEngine = engine
                    if (!initializedEngines.contains(engine)) {
                        initializedEngines.add(engine)
                    }
                },
                createWebView = ::createWebView
            )
        }
    ) { _ ->
        // Root Box
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            // Friendly Message Overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset(y = 100.dp)
                    .zIndex(100f),
                contentAlignment = Alignment.TopCenter
            ) {
                FriendlyMessageBubble(
                    message = friendlyMessage,
                    visible = isMessageVisible
                )
            }

            // Screenshot and Tint
            if (screenshot != null) {
                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    Image(
                        bitmap = screenshot.asImageBitmap(),
                        contentDescription = "Screenshot",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = OverlayGradientColors.map { it.copy(alpha = 0.3f) }
                                )
                            )
                    )
                }
            }

            // Gradient Border
            if (showGradientBorder) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(
                            width = 8.dp,
                            brush = Brush.verticalGradient(colors = OverlayGradientColors),
                            shape = RoundedCornerShape(24.dp)
                        )
                        .clip(RoundedCornerShape(24.dp))
                )
            }

            // Search Overlay (Canvas)
            SearchOverlay(
                isTextSelectionMode = isTextSelectionMode,
                textNodes = textNodes,
                onTextSelected = { text ->
                    showTextDialog = text
                },
                onSelectionComplete = { rect ->
                    selectedBitmap = ImageUtils.cropBitmap(screenshot!!, rect)
                    isSearching = true
                },
                onResetSelection = {
                    selectedBitmap = null
                    isSearching = false
                }
            )

            // Top Control Bar
            TopControlBar(
                selectedEngine = selectedEngine,
                desktopModeEngines = desktopModeEngines,
                isDarkMode = isDarkMode,
                showGradientBorder = showGradientBorder,
                searchUrl = searchUrl,
                currentUrl = webViews[selectedEngine]?.url,
                onClose = onClose,
                onToggleDesktopMode = {
                    val newSet = desktopModeEngines.toMutableSet()
                    if (newSet.contains(selectedEngine)) newSet.remove(selectedEngine) else newSet.add(selectedEngine)
                    desktopModeEngines = newSet
                },
                onToggleDarkMode = { isDarkMode = !isDarkMode },
                onToggleGradientBorder = { showGradientBorder = !showGradientBorder },
                onRefresh = { webViews[selectedEngine]?.reload() },
                onCopyUrl = {
                    if (searchUrl != null) {
                        val clip = android.content.ClipData.newPlainText("Search URL", searchUrl)
                        clipboardManager.setText(AnnotatedString(searchUrl!!))
                    }
                },
                onOpenInBrowser = {
                    val currentUrl = webViews[selectedEngine]?.url ?: searchUrl
                    if (currentUrl != null) {
                        try {
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(currentUrl))
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            android.util.Log.e("CircleToSearch", "Failed to open browser", e)
                        }
                    }
                },
                onOpenSettings = { showSettingsScreen = true },
                context = context
            )

            // Bottom Control Bar
            Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                BottomControlBar(
                    selectedBitmap = selectedBitmap,
                    isTextSelectionMode = isTextSelectionMode,
                    isLensOnlyMode = uiPreferences.isUseGoogleLensOnly(),
                    onExpandSheet = { scope.launch { scaffoldState.bottomSheetState.expand() } },
                    onToggleTextSelection = {
                        isTextSelectionMode = !isTextSelectionMode
                        if (isTextSelectionMode) {
                            Toast.makeText(context, "Text Selection Mode On", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Text Selection Mode Off", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onGoogleLensClick = {
                        if (screenshot != null) {
                            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                val path = ImageUtils.saveBitmap(context, screenshot)
                                val uri = android.net.Uri.fromFile(File(path))
                                searchWithGoogleLens(uri, context)
                            }
                            onClose()
                        }
                    }
                )
            }

            // Logic Effects
            
            // Reset everything when bitmap changes (new area selected)
            LaunchedEffect(selectedBitmap) {
                hostedImageUrl = null
                searchUrl = null
                preloadedUrls.clear()
                initializedEngines.clear()
                webViews.values.forEach { it.destroy() }
                webViews.clear()
            }

            LaunchedEffect(selectedBitmap, hostedImageUrl) {
                if (selectedBitmap != null) {
                    isLoading = true
                    
                    if (uiPreferences.isUseGoogleLensOnly()) {
                        val path = ImageUtils.saveBitmap(context, selectedBitmap!!)
                        val uri = android.net.Uri.fromFile(File(path))
                        val success = searchWithGoogleLens(uri, context)
                        if (success) {
                            onClose()
                            return@LaunchedEffect
                        }
                    }

                    scope.launch { scaffoldState.bottomSheetState.expand() }

                    if (hostedImageUrl == null) {
                        val url = ImageSearchUploader.uploadToImageHost(selectedBitmap!!)
                        if (url != null) {
                            hostedImageUrl = url
                        } else {
                            isLoading = false
                            return@LaunchedEffect
                        }
                    }

                    searchEngines.forEach { engine ->
                        if (!preloadedUrls.containsKey(engine)) {
                            val url = if (engine.isDirectUpload) {
                                 when (engine) {
                                    SearchEngine.Perplexity -> ImageSearchUploader.getPerplexityUrl(hostedImageUrl!!)
                                    SearchEngine.ChatGPT -> ImageSearchUploader.getChatGPTUrl(hostedImageUrl!!)
                                    else -> null
                                }
                            } else {
                                 when (engine) {
                                    SearchEngine.Google -> ImageSearchUploader.getGoogleLensUrl(hostedImageUrl!!)
                                    SearchEngine.Bing -> ImageSearchUploader.getBingUrl(hostedImageUrl!!)
                                    SearchEngine.Yandex -> ImageSearchUploader.getYandexUrl(hostedImageUrl!!)
                                    SearchEngine.TinEye -> ImageSearchUploader.getTinEyeUrl(hostedImageUrl!!)
                                    else -> null
                                }
                            }
                            if (url != null) preloadedUrls[engine] = url
                        }
                    }

                    if (preloadedUrls.containsKey(selectedEngine)) {
                         searchUrl = preloadedUrls[selectedEngine]
                    }
                    
                    if (!initializedEngines.contains(selectedEngine)) {
                        initializedEngines.add(selectedEngine)
                    }
                    
                    isLoading = false
                }
            }
            
            LaunchedEffect(selectedEngine, preloadedUrls) {
                if (preloadedUrls.containsKey(selectedEngine)) {
                    searchUrl = preloadedUrls[selectedEngine]
                }
            }

            // Dialogs
            if (showSettingsScreen) {
                SettingsScreen(
                    uiPreferences = uiPreferences,
                    onDismissRequest = { showSettingsScreen = false }
                )
            }
            
            if (showTextDialog != null) {
                AlertDialog(
                    onDismissRequest = { showTextDialog = null },
                    icon = { Icon(Icons.Default.TextFormat, contentDescription = null) },
                    title = { Text("Selected Text") },
                    text = {
                        SelectionContainer {
                            Text(showTextDialog!!)
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            clipboardManager.setText(AnnotatedString(showTextDialog!!))
                            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                            showTextDialog = null
                        }) {
                            Text("Copy")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showTextDialog = null }) {
                            Text("Close")
                        }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@androidx.compose.ui.tooling.preview.Preview
@Composable
fun ContainedLoadingIndicatorSample() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        androidx.compose.material3.ContainedLoadingIndicator()
    }
}

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
