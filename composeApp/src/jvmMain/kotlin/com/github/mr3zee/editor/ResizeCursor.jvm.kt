package com.github.mr3zee.editor

import androidx.compose.ui.input.pointer.PointerIcon
import java.awt.Cursor

internal actual fun resizeEdgeCursor(edge: ResizeEdge): PointerIcon = when (edge) {
    ResizeEdge.Top -> PointerIcon(Cursor(Cursor.N_RESIZE_CURSOR))
    ResizeEdge.Bottom -> PointerIcon(Cursor(Cursor.S_RESIZE_CURSOR))
    ResizeEdge.Left -> PointerIcon(Cursor(Cursor.W_RESIZE_CURSOR))
    ResizeEdge.Right -> PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR))
    ResizeEdge.TopLeft -> PointerIcon(Cursor(Cursor.NW_RESIZE_CURSOR))
    ResizeEdge.TopRight -> PointerIcon(Cursor(Cursor.NE_RESIZE_CURSOR))
    ResizeEdge.BottomLeft -> PointerIcon(Cursor(Cursor.SW_RESIZE_CURSOR))
    ResizeEdge.BottomRight -> PointerIcon(Cursor(Cursor.SE_RESIZE_CURSOR))
}
