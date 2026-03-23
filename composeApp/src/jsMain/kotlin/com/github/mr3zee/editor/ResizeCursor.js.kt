package com.github.mr3zee.editor

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.fromKeyword

@OptIn(ExperimentalComposeUiApi::class)
internal actual fun resizeEdgeCursor(edge: ResizeEdge): PointerIcon = when (edge) {
    ResizeEdge.Top -> PointerIcon.fromKeyword("n-resize")
    ResizeEdge.Bottom -> PointerIcon.fromKeyword("s-resize")
    ResizeEdge.Left -> PointerIcon.fromKeyword("w-resize")
    ResizeEdge.Right -> PointerIcon.fromKeyword("e-resize")
    ResizeEdge.TopLeft -> PointerIcon.fromKeyword("nw-resize")
    ResizeEdge.TopRight -> PointerIcon.fromKeyword("ne-resize")
    ResizeEdge.BottomLeft -> PointerIcon.fromKeyword("sw-resize")
    ResizeEdge.BottomRight -> PointerIcon.fromKeyword("se-resize")
}
