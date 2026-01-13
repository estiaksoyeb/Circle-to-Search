package com.akslabs.circletosearch.ui

import android.content.Context
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.akslabs.circletosearch.data.SearchEngine
import com.akslabs.circletosearch.data.isDirectUpload

@Composable
fun SearchResultsSheet(
    searchEngines: List<SearchEngine>,
    selectedEngine: SearchEngine,
    initializedEngines: List<SearchEngine>,
    preloadedUrls: Map<SearchEngine, String>,
    webViews: Map<SearchEngine, WebView>,
    isLoading: Boolean,
    isDesktop: (SearchEngine) -> Boolean,
    isDarkMode: Boolean,
    onEngineSelected: (SearchEngine) -> Unit,
    createWebView: (Context, SearchEngine) -> WebView,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
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
                val transition = updateTransition(targetState = selected, label = "TabSelect")
                val scale by transition.animateFloat(label = "Scale") { if (it) 1.02f else 1f }
                val alpha by transition.animateFloat(label = "Alpha") { if (it) 1f else 0.7f }

                Tab(
                    selected = selected,
                    onClick = { onEngineSelected(engine) },
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
                                    fontWeight = FontWeight.Medium
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

        Box(modifier = Modifier.fillMaxSize()) {
            if (isLoading || (preloadedUrls.containsKey(selectedEngine) && !webViews.containsKey(selectedEngine))) {
                 Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ContainedLoadingIndicatorSample()
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
