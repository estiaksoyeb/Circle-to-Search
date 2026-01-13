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
 *  * along with this program.  See <https://www.gnu.org/licenses/>.
 * 
 */

package com.akslabs.circletosearch.ui

import android.graphics.Bitmap
import android.graphics.Rect
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TextFormat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.akslabs.circletosearch.data.SearchEngine
import com.akslabs.circletosearch.data.TextNode
import com.akslabs.circletosearch.data.TextRepository
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
    
    val uiPreferences = remember { UIPreferences(context) }

    var isTextSelectionMode by remember { mutableStateOf(false) }
    val textNodes = remember { mutableStateListOf<TextNode>() }
    val clipboardManager = LocalClipboardManager.current
    var showTextDialog by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val nodes = TextRepository.getTextNodes()
        textNodes.addAll(nodes)
    }
    
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

    var showSettingsScreen by remember { mutableStateOf(false) }
    var friendlyMessage by remember { mutableStateOf("") }
    var isMessageVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (uiPreferences.isShowFriendlyMessages()) {
            val manager = FriendlyMessageManager(context)
            friendlyMessage = manager.getNextMessage()
            delay(500)
            isMessageVisible = true
            delay(4000)
            isMessageVisible = false
        }
    }

    var selectedEngine by remember(searchEngines) { mutableStateOf<SearchEngine>(searchEngines.first()) }
    var searchUrl by remember { mutableStateOf<String?>(null) }
    var hostedImageUrl by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    
    val initialDesktopMode = uiPreferences.isDesktopMode()
    var desktopModeEngines by remember { mutableStateOf<Set<SearchEngine>>(if(initialDesktopMode) searchEngines.toSet() else emptySet()) }
    
    var isDarkMode by remember { mutableStateOf(uiPreferences.isDarkMode()) }
    var showGradientBorder by remember { mutableStateOf(uiPreferences.isShowGradientBorder()) }
    val initializedEngines = remember { mutableStateListOf<SearchEngine>() }
    
    fun isDesktop(engine: SearchEngine) = desktopModeEngines.contains(engine)
    
    LaunchedEffect(isDarkMode) { uiPreferences.setDarkMode(isDarkMode) }
    LaunchedEffect(showGradientBorder) { uiPreferences.setShowGradientBorder(showGradientBorder) }
    
    val preloadedUrls = remember { mutableMapOf<SearchEngine, String>() }
    val webViews = remember { mutableMapOf<SearchEngine, android.webkit.WebView>() }
    
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
                    wv.webViewClient = object : android.webkit.WebViewClient() {
                        override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            val darkModeCSS = "javascript:(function() { var style = document.createElement('style'); style.innerHTML = 'html { filter: invert(1) hue-rotate(180deg) !important; background: #000 !important; } img, video, [style*=\"background-image\"] { filter: invert(1) hue-rotate(180deg) !important; }'; document.head.appendChild(style); })()"
                            view?.loadUrl(darkModeCSS)
                        }
                    }
                } else {
                    wv.webViewClient = android.webkit.WebViewClient()
                }
                wv.reload()
            } catch (e: Exception) {
                android.util.Log.e("CircleToSearch", "Error updating dark mode", e)
            }
        }
    }
    
    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberStandardBottomSheetState(initialValue = SheetValue.Hidden, skipHiddenState = false)
    )

    var selectedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isSearching by remember { mutableStateOf(false) }
    
    fun createWebView(ctx: android.content.Context, engine: SearchEngine): android.webkit.WebView {
        return android.webkit.WebView(ctx).apply {
            layoutParams = android.view.ViewGroup.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT)
            setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                allowFileAccess = true
                allowContentAccess = true
                allowFileAccessFromFileURLs = true
                allowUniversalAccessFromFileURLs = true
                mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                setRenderPriority(android.webkit.WebSettings.RenderPriority.HIGH)
                cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
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
            webViewClient = android.webkit.WebViewClient()
            isNestedScrollingEnabled = true
        }
    }

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
            BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.3f), width = 32.dp, height = 3.dp)
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
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            Box(modifier = Modifier.fillMaxSize().offset(y = 100.dp).zIndex(100f), contentAlignment = Alignment.TopCenter) {
                FriendlyMessageBubble(message = friendlyMessage, visible = isMessageVisible)
            }

            if (screenshot != null) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Image(
                        bitmap = screenshot.asImageBitmap(),
                        contentDescription = "Screenshot",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(modifier = Modifier.fillMaxSize().background(brush = Brush.verticalGradient(colors = OverlayGradientColors.map { it.copy(alpha = 0.3f) }})))
                }
            }

            if (showGradientBorder) {
                Box(modifier = Modifier.fillMaxSize().border(width = 8.dp, brush = Brush.verticalGradient(colors = OverlayGradientColors), shape = RoundedCornerShape(24.dp)).clip(RoundedCornerShape(24.dp)))
            }

            SearchOverlay(
                isTextSelectionMode = isTextSelectionMode,
                textNodes = textNodes,
                onTextSelected = { text -> showTextDialog = text },
                onSelectionComplete = { rect ->
                    selectedBitmap = ImageUtils.cropBitmap(screenshot!!, rect)
                    isSearching = true
                },
                onResetSelection = {
                    selectedBitmap = null
                    isSearching = false
                }
            )

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
                    searchUrl?.let { clipboardManager.setText(AnnotatedString(it)) }
                },
                onOpenInBrowser = {
                    val url = webViews[selectedEngine]?.url ?: searchUrl
                    url?.let {
                        try {
                            context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(it)))
                        } catch (e: Exception) {
                            android.util.Log.e("CircleToSearch", "Failed to open browser", e)
                        }
                    }
                },
                onOpenSettings = { showSettingsScreen = true },
                context = context
            )

            Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                BottomControlBar(
                    selectedBitmap = selectedBitmap,
                    isTextSelectionMode = isTextSelectionMode,
                    isLensOnlyMode = uiPreferences.isUseGoogleLensOnly(),
                    onExpandSheet = { scope.launch { scaffoldState.bottomSheetState.expand() } },
                    onToggleTextSelection = {
                        isTextSelectionMode = !isTextSelectionMode
                        Toast.makeText(context, "Text Selection Mode ${if (isTextSelectionMode) "On" else "Off"}", Toast.LENGTH_SHORT).show()
                    },
                    onGoogleLensClick = {
                        if (screenshot != null) {
                            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                val path = ImageUtils.saveBitmap(context, screenshot)
                                searchWithGoogleLens(android.net.Uri.fromFile(File(path)), context)
                            }
                            onClose()
                        }
                    }
                )
            }

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
                        if (searchWithGoogleLens(android.net.Uri.fromFile(File(path)), context)) {
                            onClose()
                            return@LaunchedEffect
                        }
                    }
                    scope.launch { scaffoldState.bottomSheetState.expand() }
                    if (hostedImageUrl == null) {
                        ImageSearchUploader.uploadToImageHost(selectedBitmap!!)?.let { hostedImageUrl = it } ?: run { isLoading = false; return@LaunchedEffect }
                    }
                    searchEngines.forEach { engine ->
                        if (!preloadedUrls.containsKey(engine)) {
                            val url = if (com.akslabs.circletosearch.data.isDirectUpload(engine)) {
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
                            url?.let { preloadedUrls[engine] = it }
                        }
                    }
                    preloadedUrls[selectedEngine]?.let { searchUrl = it }
                    if (!initializedEngines.contains(selectedEngine)) initializedEngines.add(selectedEngine)
                    isLoading = false
                }
            }
            
            LaunchedEffect(selectedEngine, preloadedUrls) {
                preloadedUrls[selectedEngine]?.let { searchUrl = it }
            }

            if (showSettingsScreen) {
                SettingsScreen(uiPreferences = uiPreferences, onDismissRequest = { showSettingsScreen = false })
            }
            
            if (showTextDialog != null) {
                AlertDialog(
                    onDismissRequest = { showTextDialog = null },
                    icon = { Icon(Icons.Default.TextFormat, null) },
                    title = { Text("Selected Text") },
                    text = { SelectionContainer { Text(showTextDialog!!) } },
                    confirmButton = {
                        TextButton(onClick = {
                            clipboardManager.setText(AnnotatedString(showTextDialog!!))
                            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                            showTextDialog = null
                        }) { Text("Copy") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showTextDialog = null }) { Text("Close") }
                    }
                )
            }
        }
    }
}