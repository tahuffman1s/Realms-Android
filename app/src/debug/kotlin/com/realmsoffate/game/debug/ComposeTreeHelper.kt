@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.realmsoffate.game.debug

import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.node.RootForTest
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsOwner
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull

object ComposeTreeHelper {

    /** Walk the view tree to find the SemanticsOwner from the first Compose view. */
    fun findSemanticsOwner(root: View): SemanticsOwner? {
        if (root is AbstractComposeView) {
            for (i in 0 until root.childCount) {
                val child = root.getChildAt(i)
                if (child is RootForTest) {
                    return child.semanticsOwner
                }
            }
        }
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                val found = findSemanticsOwner(root.getChildAt(i))
                if (found != null) return found
            }
        }
        return null
    }

    /** Find the first AbstractComposeView in the view hierarchy. */
    fun findAbstractComposeView(root: View): AbstractComposeView? {
        if (root is AbstractComposeView) return root
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                val found = findAbstractComposeView(root.getChildAt(i))
                if (found != null) return found
            }
        }
        return null
    }

    /** Get ALL semantics nodes in the Compose tree (merged, skip deactivated). */
    fun getAllNodes(owner: SemanticsOwner): List<SemanticsNode> {
        return owner.getAllSemanticsNodes(mergingEnabled = true, skipDeactivatedNodes = true)
    }

    /** Get the text content of a semantics node. */
    fun nodeText(node: SemanticsNode): String? {
        val texts = node.config.getOrNull(SemanticsProperties.Text)
        if (texts != null && texts.isNotEmpty()) return texts.joinToString(" ") { it.text }
        return null
    }

    /** Get the content description of a semantics node. */
    fun nodeContentDesc(node: SemanticsNode): String? {
        val descs = node.config.getOrNull(SemanticsProperties.ContentDescription)
        return descs?.firstOrNull()
    }

    /** Get a human-readable label for the node (text, contentDescription, or synthetic id). */
    fun nodeLabel(node: SemanticsNode): String {
        return nodeText(node)
            ?: nodeContentDesc(node)
            ?: "node-${node.id}"
    }

    /** Check if the node is clickable. */
    fun isClickable(node: SemanticsNode): Boolean {
        return SemanticsActions.OnClick in node.config
    }

    /** Check if the node is visible (not marked InvisibleToUser). */
    fun isVisible(node: SemanticsNode): Boolean {
        return SemanticsProperties.InvisibleToUser !in node.config
    }

    /** Get bounds in window coordinates as an Android Rect. */
    fun boundsInWindow(node: SemanticsNode): android.graphics.Rect {
        val r = node.boundsInWindow
        return android.graphics.Rect(r.left.toInt(), r.top.toInt(), r.right.toInt(), r.bottom.toInt())
    }
}
