package com.github.mr3zee.editor

import androidx.compose.ui.input.pointer.PointerIcon

internal actual fun resizeEdgeCursor(edge: ResizeEdge): PointerIcon = when (edge) {
    ResizeEdge.Top -> PointerIcon("n-resize")
    ResizeEdge.Bottom -> PointerIcon("s-resize")
    ResizeEdge.Left -> PointerIcon("w-resize")
    ResizeEdge.Right -> PointerIcon("e-resize")
    ResizeEdge.TopLeft -> PointerIcon("nw-resize")
    ResizeEdge.TopRight -> PointerIcon("ne-resize")
    ResizeEdge.BottomLeft -> PointerIcon("sw-resize")
    ResizeEdge.BottomRight -> PointerIcon("se-resize")
}
