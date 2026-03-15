package com.github.mr3zee.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

object AppShapes {
    /** Chips, badges */
    val xs = RoundedCornerShape(6.dp)

    /** Inputs, buttons — matches canvas block corners */
    val sm = RoundedCornerShape(8.dp)

    /** Cards, list items */
    val md = RoundedCornerShape(10.dp)

    /** Dialogs */
    val lg = RoundedCornerShape(14.dp)

    /** Modals, sheets */
    val xl = RoundedCornerShape(20.dp)

    /** Status badges, toggle pills */
    val pill = RoundedCornerShape(50)
}

/**
 * Maps our custom shape system into M3's Shapes so that retained M3 components
 * (Scaffold, AlertDialog, DropdownMenu, etc.) pick up softer corners automatically.
 */
val AppMaterialShapes = Shapes(
    extraSmall = AppShapes.xs,
    small = AppShapes.sm,
    medium = AppShapes.md,
    large = AppShapes.lg,
    extraLarge = AppShapes.xl,
)
