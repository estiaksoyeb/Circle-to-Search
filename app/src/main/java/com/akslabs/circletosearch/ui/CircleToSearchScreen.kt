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
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.akslabs.circletosearch.data.SearchEngine
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
import kotlin.math.max
import kotlin.math.min
import android.widget.Toast

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

    // Drawing State
    val currentPathPoints = remember { mutableStateListOf<Offset>() }
    
    // Selection State
    var selectedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isSearching by remember { mutableStateOf(false) }
    var selectionRect by remember { mutableStateOf<Rect?>(null) }
    val selectionAnim = remember { androidx.compose.animation.core.Animatable(0f) }
    
    // Gradient Animation
    val alphaAnim by animateFloatAsState(
        targetValue = if (screenshot != null) 1f else 0f,
        animationSpec = tween(1000), label = "alpha"
    )

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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(800.dp)
                    .background(Color(0xFF1F1F1F), RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            ) {
                Spacer(modifier = Modifier.height(16.dp))
                
                ScrollableTabRow(
                    selectedTabIndex = searchEngines.indexOf(selectedEngine),
                    edgePadding = 16.dp,
                    containerColor = Color(0xFF1F1F1F),
                    contentColor = Color.White,
                    divider = {},
                    indicator = {}
                ) {
                    searchEngines.forEach { engine ->
                        val selected = selectedEngine == engine
                        val transition = androidx.compose.animation.core.updateTransition(targetState = selected, label = "TabSelect")
                        val scale by transition.animateFloat(label = "Scale") { if (it) 1.02f else 1f }
                        val alpha by transition.animateFloat(label = "Alpha") { if (it) 1f else 0.7f }

                        Tab(
                            selected = selected,
                            onClick = { 
                                selectedEngine = engine
                                if (!initializedEngines.contains(engine)) {
                                    initializedEngines.add(engine)
                                }
                            },
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            modifier = Modifier.graphicsLayer { 
                                scaleX = scale
                                scaleY = scale
                                this.alpha = alpha
                            },
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .background(
                                            if (selected) Color.White.copy(alpha = 0.15f) else Color.Transparent,
                                            RoundedCornerShape(20.dp)
                                        )
                                        .padding(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = getEngineIcon(engine),
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = if (selected) Color.White else Color.White.copy(alpha = 0.7f)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        engine.name,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal
                                        ),
                                        color = if (selected) Color.White else Color.White.copy(alpha = 0.7f)
                                    )
                                }
                            },
                            selectedContentColor = Color.Transparent,
                            unselectedContentColor = Color.Transparent
                        )
                    }
                }

                // Reset everything when bitmap changes
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
                            val uri = android.net.Uri.fromFile(java.io.File(path))
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

                Box(modifier = Modifier.fillMaxSize()) {
                    if (isLoading || (preloadedUrls.containsKey(selectedEngine) && !webViews.containsKey(selectedEngine))) {
                        var showLoader by remember { mutableStateOf(false) }
                        LaunchedEffect(Unit) {
                            delay(100)
                            showLoader = true
                        }
                        if (showLoader || isLoading) {
                             Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                ContainedLoadingIndicatorSample()
                             }
                        }
                    }

                    DisposableEffect(Unit) {
                        onDispose {
                            webViews.values.forEach { it.destroy() }
                            webViews.clear()
                        }
                    }

                    searchEngines.forEach { engine ->
                         if (initializedEngines.contains(engine) && preloadedUrls.containsKey(engine)) {
                             val url = preloadedUrls[engine]!!
                             val isSelected = (engine == selectedEngine)
                             
                             key(engine) {
                                AndroidView(
                                    factory = { ctx ->
                                        if (webViews.containsKey(engine)) {
                                            val v = webViews[engine]!!
                                            (v.parent as? ViewGroup)?.removeView(v)
                                            val swipeRefresh = SwipeRefreshLayout(ctx).apply {
                                                layoutParams = ViewGroup.LayoutParams(
                                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                                    ViewGroup.LayoutParams.MATCH_PARENT
                                                )
                                            }
                                            swipeRefresh.addView(v)
                                            swipeRefresh.setOnRefreshListener {
                                                v.reload()
                                                swipeRefresh.isRefreshing = false
                                            }
                                            swipeRefresh
                                        } else {
                                            val swipeRefresh = SwipeRefreshLayout(ctx).apply {
                                                layoutParams = ViewGroup.LayoutParams(
                                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                                    ViewGroup.LayoutParams.MATCH_PARENT
                                                )
                                            }
                                            val webView = createWebView(ctx, engine)
                                            webViews[engine] = webView
                                            webView.loadUrl(url)
                                            swipeRefresh.addView(webView)
                                            swipeRefresh.setOnRefreshListener {
                                                webView.reload()
                                                swipeRefresh.isRefreshing = false
                                            }
                                            swipeRefresh
                                        }
                                    },
                                    update = { swipeRefresh ->
                                        var webView: WebView? = null
                                        for (i in 0 until swipeRefresh.childCount) {
                                            val child = swipeRefresh.getChildAt(i)
                                            if (child is WebView) {
                                                webView = child
                                                break
                                            }
                                        }
                                        if (webView != null) {
                                            if (webView.url != url && url != webView.originalUrl) {
                                                webView.loadUrl(url)
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .zIndex(if (isSelected) 1f else 0f)
                                        .graphicsLayer { 
                                            alpha = if (isSelected) 1f else 0f 
                                        }
                                )
                             }
                        }
                    }
                }
            }
        }
    ) { _ ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
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

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                currentPathPoints.clear()
                                currentPathPoints.add(offset)
                                selectionRect = null
                                scope.launch { selectionAnim.snapTo(0f) }
                            },
                            onDrag = { change, _ ->
                                val offset = change.position
                                currentPathPoints.add(offset)
                            },
                            onDragEnd = {
                                if (currentPathPoints.isNotEmpty()) {
                                    var minX = Float.MAX_VALUE
                                    var minY = Float.MAX_VALUE
                                    var maxX = Float.MIN_VALUE
                                    var maxY = Float.MIN_VALUE

                                    currentPathPoints.forEach { p ->
                                        minX = min(minX, p.x)
                                        minY = min(minY, p.y)
                                        maxX = max(maxX, p.x)
                                        maxY = max(maxY, p.y)
                                    }
                                    
                                    val rect = Rect(minX.toInt(), minY.toInt(), maxX.toInt(), maxY.toInt())
                                    selectionRect = rect
                                    currentPathPoints.clear() 
                                    
                                    scope.launch {
                                        selectionAnim.animateTo(1f, animationSpec = tween(600))
                                        selectedBitmap = ImageUtils.cropBitmap(screenshot!!, rect)
                                        isSearching = true
                                    }
                                }
                            }
                        )
                    }
            ) {
                if (currentPathPoints.size > 1) {
                    val path = Path().apply {
                        moveTo(currentPathPoints.first().x, currentPathPoints.first().y)
                        for (i in 1 until currentPathPoints.size) {
                            lineTo(currentPathPoints[i].x, currentPathPoints[i].y)
                        }
                    }
                    drawPath(
                        path = path,
                        brush = Brush.linearGradient(OverlayGradientColors),
                        style = Stroke(width = 30f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                        alpha = 0.6f
                    )
                    drawPath(
                        path = path,
                        color = Color.White,
                        style = Stroke(width = 12f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                }

                if (selectionRect != null && selectionAnim.value > 0f) {
                    val rect = selectionRect!!
                    val progress = selectionAnim.value
                    val left = rect.left.toFloat()
                    val top = rect.top.toFloat()
                    val right = rect.right.toFloat()
                    val bottom = rect.bottom.toFloat()
                    val width = right - left
                    val height = bottom - top
                    val cornerRadius = 64f
                    val armLength = min(width, height) * 0.2f

                    val tlPath = Path().apply {
                        moveTo(left, top + armLength)
                        lineTo(left, top + cornerRadius)
                        arcTo(androidx.compose.ui.geometry.Rect(left, top, left + 2 * cornerRadius, top + 2 * cornerRadius), 180f, 90f, false)
                        lineTo(left + armLength, top)
                    }
                    val trPath = Path().apply {
                        moveTo(right - armLength, top)
                        lineTo(right - cornerRadius, top)
                        arcTo(androidx.compose.ui.geometry.Rect(right - 2 * cornerRadius, top, right, top + 2 * cornerRadius), 270f, 90f, false)
                        lineTo(right, top + armLength)
                    }
                    val brPath = Path().apply {
                        moveTo(right, bottom - armLength)
                        lineTo(right, bottom - cornerRadius)
                        arcTo(androidx.compose.ui.geometry.Rect(right - 2 * cornerRadius, bottom - 2 * cornerRadius, right, bottom), 0f, 90f, false)
                        lineTo(right - armLength, bottom)
                    }
                    val blPath = Path().apply {
                        moveTo(left + armLength, bottom)
                        lineTo(left + cornerRadius, bottom)
                        arcTo(androidx.compose.ui.geometry.Rect(left, bottom - 2 * cornerRadius, left + 2 * cornerRadius, bottom), 90f, 90f, false)
                        lineTo(left, bottom - armLength)
                    }

                    val bracketStroke = Stroke(width = 12f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    listOf(tlPath, trPath, brPath, blPath).forEach { p ->
                        drawPath(p, Color.White, style = bracketStroke, alpha = progress)
                        drawPath(p, Brush.linearGradient(OverlayGradientColors), style = Stroke(width = 20f, cap = StrokeCap.Round), alpha = progress * 0.5f)
                    }
                     drawRoundRect(
                        color = Color.White,
                        topLeft = Offset(left, top),
                        size = Size(width, height),
                        cornerRadius = CornerRadius(32f),
                        style = Stroke(width = 4f),
                        alpha = (1f - progress) * 0.5f
                    )
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 24.dp)
                    .shadow(8.dp, CircleShape)
                    .background(Color(0xFF1F1F1F), CircleShape)
                    .height(64.dp)
                    .padding(horizontal = 20.dp)
                    .pointerInput(Unit) {
                        detectTapGestures {
                            if (!uiPreferences.isUseGoogleLensOnly() && selectedBitmap != null) {
                                scope.launch { scaffoldState.bottomSheetState.expand() }
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
                            bitmap = selectedBitmap!!.asImageBitmap(),
                            contentDescription = "Selected",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.dp, Color.Gray, RoundedCornerShape(12.dp))
                        )
                    } else {
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
                    IconButton(onClick = { 
                        Toast.makeText(context, "Select Text: Coming Soon", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(
                            imageVector = Icons.Default.TextFormat,
                            contentDescription = "Select Text",
                            tint = Color.White
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(4.dp))

                    // Google Lens Button (Full Screenshot)
                    IconButton(onClick = {
                        if (screenshot != null) {
                             scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                 val path = ImageUtils.saveBitmap(context, screenshot)
                                 val uri = android.net.Uri.fromFile(java.io.File(path))
                                 searchWithGoogleLens(uri, context)
                             }
                             onClose()
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "Google Lens",
                            tint = Color.White
                        )
                    }
                }
            }

            if (showSettingsScreen) {
                SettingsScreen(
                    uiPreferences = uiPreferences,
                    onDismissRequest = { showSettingsScreen = false }
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
