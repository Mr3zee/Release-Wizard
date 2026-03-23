package com.github.mr3zee.editor

import androidx.compose.ui.input.pointer.PointerIcon
import java.awt.Cursor

internal actual fun resizeEdgeCursor(edge: ResizeEdge): PointerIcon = when (edge) {
    ResizeEdge.Top, ResizeEdge.Bottom -> PointerIcon(Cursor(Cursor.N_RESIZE_CURSOR))
    ResizeEdge.Left, ResizeEdge.Right -> PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR))
    ResizeEdge.TopLeft, ResizeEdge.BottomRight -> PointerIcon(Cursor(Cursor.NW_RESIZE_CURSOR))
    ResizeEdge.TopRight, ResizeEdge.BottomLeft -> PointerIcon(Cursor(Cursor.NE_RESIZE_CURSOR))
}
