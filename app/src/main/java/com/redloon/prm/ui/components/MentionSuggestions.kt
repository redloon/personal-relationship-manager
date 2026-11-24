package com.redloon.prm.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

/** Keyboard-safe layout shared by person and tag suggestions. Trigger/link state stays in the editor. */
@Composable
internal fun MentionSuggestions(
    anchor: Rect,
    maxWidth: Dp,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val density = LocalDensity.current
    val windowSize = LocalWindowInfo.current.containerSize
    val safeTop = WindowInsets.systemBars.getTop(density)
    val safeBottom =
        windowSize.height -
            maxOf(WindowInsets.ime.getBottom(density), WindowInsets.systemBars.getBottom(density))
    val height =
        with(density) {
            maxOf(
                anchor.top - safeTop,
                safeBottom - anchor.bottom
            ).coerceIn(0f, 220.dp.toPx()).toDp()
        }
    if (height > 0.dp) {
        Popup(
            popupPositionProvider = remember(safeTop, safeBottom) {
                MentionMenuPosition(safeTop, safeBottom)
            },
            onDismissRequest = onDismiss,
            properties = PopupProperties(focusable = false, dismissOnClickOutside = false)
        ) {
            Surface(
                shape = MaterialTheme.shapes.extraSmall,
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 3.dp,
                shadowElevation = 3.dp
            ) {
                Column(
                    Modifier.widthIn(
                        min = minOf(112.dp, maxWidth),
                        max = maxWidth
                    ).heightIn(max = height)
                        .verticalScroll(
                            rememberScrollState()
                        ).padding(vertical = 8.dp).exposeTestTags().testTag("mention_suggestions"),
                    content = content
                )
            }
        }
    }
}

internal class MentionMenuPosition(private val safeTop: Int, private val safeBottom: Int) :
    PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val below = safeBottom - anchorBounds.bottom
        val y = if (popupContentSize.height <=
            below
        ) {
            anchorBounds.bottom
        } else {
            anchorBounds.top - popupContentSize.height
        }
        val preferredX = if (layoutDirection ==
            LayoutDirection.Ltr
        ) {
            anchorBounds.left
        } else {
            anchorBounds.right - popupContentSize.width
        }
        return IntOffset(
            preferredX.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0)),
            y.coerceAtLeast(safeTop)
        )
    }
}
