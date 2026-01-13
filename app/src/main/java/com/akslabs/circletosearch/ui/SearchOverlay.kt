package com.akslabs.circletosearch.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Rect as ComposeRect
import android.graphics.Rect as AndroidRect
import com.akslabs.circletosearch.data.TextNode
import com.akslabs.circletosearch.ui.theme.OverlayGradientColors
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

@Composable
fun SearchOverlay(
    modifier: Modifier = Modifier,
    isTextSelectionMode: Boolean,
    textNodes: List<TextNode>,
    onTextSelected: (String) -> Unit,
    onSelectionComplete: (AndroidRect) -> Unit,
    onResetSelection: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val currentPathPoints = remember { mutableStateListOf<Offset>() }
    var selectionRect by remember { mutableStateOf<AndroidRect?>(null) }
    val selectionAnim = remember { androidx.compose.animation.core.Animatable(0f) }

    // Reset internal state when external mode changes
    LaunchedEffect(isTextSelectionMode) {
        if (isTextSelectionMode) {
            currentPathPoints.clear()
            selectionRect = null
            selectionAnim.snapTo(0f)
            onResetSelection()
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(isTextSelectionMode) {
                if (isTextSelectionMode) {
                    detectTapGestures { offset ->
                        val tappedNode = textNodes.find { node ->
                            node.bounds.contains(offset.x.toInt(), offset.y.toInt())
                        }
                        if (tappedNode != null) {
                            onTextSelected(tappedNode.text)
                        }
                    }
                } else {
                    detectDragGestures(
                        onDragStart = { offset ->
                            currentPathPoints.clear()
                            currentPathPoints.add(offset)
                            selectionRect = null
                            scope.launch { selectionAnim.snapTo(0f) }
                            onResetSelection()
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

                                val rect = AndroidRect(minX.toInt(), minY.toInt(), maxX.toInt(), maxY.toInt())
                                selectionRect = rect
                                currentPathPoints.clear()

                                scope.launch {
                                    selectionAnim.animateTo(1f, animationSpec = tween(600))
                                    onSelectionComplete(rect)
                                }
                            }
                        }
                    )
                }
            }
    ) {
        // Draw Text Selection Boxes
        if (isTextSelectionMode) {
            textNodes.forEach { node ->
                drawRect(
                    color = Color.White.copy(alpha = 0.3f),
                    topLeft = Offset(node.bounds.left.toFloat(), node.bounds.top.toFloat()),
                    size = Size(node.bounds.width().toFloat(), node.bounds.height().toFloat()),
                    style = Fill
                )
                drawRect(
                    color = Color.White,
                    topLeft = Offset(node.bounds.left.toFloat(), node.bounds.top.toFloat()),
                    size = Size(node.bounds.width().toFloat(), node.bounds.height().toFloat()),
                    style = Stroke(width = 2f)
                )
            }
        }

        // Draw current path (Real-time)
        if (!isTextSelectionMode && currentPathPoints.size > 1) {
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

        // Draw Lens Animation
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

            // ... (Drawing logic identical to previous file, can be refactored further but keeping it here for now)
            val tlPath = Path().apply {
                moveTo(left, top + armLength)
                lineTo(left, top + cornerRadius)
                arcTo(ComposeRect(left, top, left + 2 * cornerRadius, top + 2 * cornerRadius), 180f, 90f, false)
                lineTo(left + armLength, top)
            }
            val trPath = Path().apply {
                moveTo(right - armLength, top)
                lineTo(right - cornerRadius, top)
                arcTo(ComposeRect(right - 2 * cornerRadius, top, right, top + 2 * cornerRadius), 270f, 90f, false)
                lineTo(right, top + armLength)
            }
            val brPath = Path().apply {
                moveTo(right, bottom - armLength)
                lineTo(right, bottom - cornerRadius)
                arcTo(ComposeRect(right - 2 * cornerRadius, bottom - 2 * cornerRadius, right, bottom), 0f, 90f, false)
                lineTo(right - armLength, bottom)
            }
            val blPath = Path().apply {
                moveTo(left + armLength, bottom)
                lineTo(left + cornerRadius, bottom)
                arcTo(ComposeRect(left, bottom - 2 * cornerRadius, left + 2 * cornerRadius, bottom), 90f, 90f, false)
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
}
