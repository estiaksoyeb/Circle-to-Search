package com.akslabs.circletosearch.data

import android.graphics.Rect

data class TextNode(
    val text: String,
    val bounds: Rect
)

object TextRepository {
    private var textNodes: List<TextNode> = emptyList()

    fun setTextNodes(nodes: List<TextNode>) {
        textNodes = nodes
    }

    fun getTextNodes(): List<TextNode> {
        return textNodes
    }

    fun clear() {
        textNodes = emptyList()
    }
}
