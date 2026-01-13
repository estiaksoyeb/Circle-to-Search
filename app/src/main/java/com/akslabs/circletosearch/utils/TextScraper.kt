package com.akslabs.circletosearch.utils

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.akslabs.circletosearch.data.TextNode

class TextScraper(private val service: AccessibilityService) {

    fun scrapeScreenText(): List<TextNode> {
        val nodes = mutableListOf<TextNode>()
        val windows = service.windows
        
        for (window in windows) {
            val root = window.root
            if (root != null) {
                collectText(root, nodes)
            }
        }
        return nodes
    }

    private fun collectText(node: AccessibilityNodeInfo, list: MutableList<TextNode>) {
        if (node.text != null && node.text.isNotEmpty()) {
            val rect = Rect()
            node.getBoundsInScreen(rect)
            if (rect.width() > 0 && rect.height() > 0) {
                list.add(TextNode(node.text.toString(), rect))
            }
        }
        
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                collectText(child, list)
                child.recycle()
            }
        }
    }
}
