package com.github.mr3zee.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class AppColors(
    // ── Theme variant ─────────────────────────────────────────────
    val isDark: Boolean,

    // ── Chrome (UI shell) ──────────────────────────────────────────
    val chromeBackground: Color,
    val chromeSurface: Color,
    val chromeSurfaceSecondary: Color,
    val chromeSurfaceElevated: Color,
    val chromeBorder: Color,
    val chromeBorderFocused: Color,
    val chromeTextPrimary: Color,
    val chromeTextSecondary: Color,
    val chromeTextTertiary: Color,
    val chromeTextTimestamp: Color,  // temporal info: "Started 5m ago", durations, dates
    val chromeTextMetadata: Color,  // structural labels: type badges, counts, phase context

    // ── Interactive: buttons ────────────────────────────────────────
    val buttonPrimaryBg: Color,
    val buttonPrimaryHover: Color,
    val buttonPrimaryPress: Color,
    val buttonPrimaryText: Color,
    val buttonSecondaryBg: Color,
    val buttonSecondaryHover: Color,
    val buttonSecondaryPress: Color,
    val buttonSecondaryBorder: Color,
    val buttonGhostHover: Color,
    val buttonGhostPress: Color,
    val buttonDangerBg: Color,
    val buttonDangerHover: Color,
    val buttonDangerPress: Color,

    // ── Interactive: inputs ─────────────────────────────────────────
    val inputBg: Color,
    val inputBorder: Color,
    val inputBorderFocused: Color,
    val inputPlaceholder: Color,

    // ── Interactive: chips ──────────────────────────────────────────
    val chipBg: Color,
    val chipBgSelected: Color,
    val chipBorder: Color,
    val chipText: Color,
    val chipTextSelected: Color,

    // ── Canvas ──────────────────────────────────────────────────────
    val canvasBackground: Color,
    val canvasGridDots: Color, // legacy — used by current dot grid; Phase 2 switches to major/minor
    val canvasGridMajor: Color,
    val canvasGridMinor: Color,
    val blockInnerBorder: Color,
    val blockInnerHighlight: Color,

    // Edges
    val edgeDefault: Color,
    val edgeSelected: Color,
    val edgeGlow: Color,

    // Block selection
    val blockSelectionHighlight: Color,

    // Port colors
    val portDefault: Color,
    val portHover: Color,
    val portHoverRing: Color,

    // Draft edge
    val draftEdge: Color,

    // Block type colors
    val teamcityBuild: Color,
    val githubAction: Color,
    val githubPublication: Color,
    val slackMessage: Color,
    val gateIndicator: Color,
    val containerBlock: Color,
    val containerBorder: Color,
    val containerHeaderBg: Color,
    val containerDropHighlight: Color,
    val containerDetachHighlight: Color,

    // Status colors (release-level)
    val statusPending: Color,
    val statusRunning: Color,
    val statusSuccess: Color,
    val statusFailed: Color,
    val statusCancelled: Color,
    val statusArchived: Color,

    // Block text & shadow
    val blockShadow: Color,
    val blockText: Color,
    val blockTextSecondary: Color,

    // Block execution status colors
    val blockStatusSucceeded: Color,
    val blockStatusRunning: Color,
    val blockStatusFailed: Color,
    val blockStatusWaiting: Color,
    val blockStatusWaitingForInput: Color,
    val blockStatusStopped: Color,

    // Release stopped status
    val statusStopped: Color,

    // ── Sidebar ────────────────────────────────────────────────────────
    val sidebarActiveBg: Color,
    val sidebarActiveText: Color,
    val sidebarActiveHoverBg: Color,

    // ── Tooltip ──────────────────────────────────────────────────────
    val tooltipBg: Color,
    val tooltipText: Color,

    // ── Focus ring ────────────────────────────────────────────────────
    val focusRing: Color,
    val focusRingOnColor: Color,
)

val LightAppColors = AppColors(
    isDark = false,
    // Chrome
    chromeBackground = Color(0xFFF5F6F8),
    chromeSurface = Color(0xFFFFFFFF),
    chromeSurfaceSecondary = Color(0xFFF7F8FA),
    chromeSurfaceElevated = Color(0xFFFFFFFF),
    chromeBorder = Color(0xFFE5E7EB),
    chromeBorderFocused = Color(0xFF93C5FD),
    chromeTextPrimary = Color(0xFF1F2937),
    chromeTextSecondary = Color(0xFF6B7280),
    chromeTextTertiary = Color(0xFF737B8A),
    chromeTextTimestamp = Color(0xFF7C8598),  // blue-gray, ~4.7:1 on white — temporal info
    chromeTextMetadata = Color(0xFF566275),   // darker blue-gray, ~6.2:1 on white — structural labels

    // Buttons
    buttonPrimaryBg = Color(0xFF3B82F6),
    buttonPrimaryHover = Color(0xFF2563EB),
    buttonPrimaryPress = Color(0xFF1D4ED8),
    buttonPrimaryText = Color.White,
    buttonSecondaryBg = Color.White,
    buttonSecondaryHover = Color(0xFFF5F6F8),
    buttonSecondaryPress = Color(0xFFEEEFF2),
    buttonSecondaryBorder = Color(0xFFE5E7EB),
    buttonGhostHover = Color(0xFFF5F6F8),
    buttonGhostPress = Color(0xFFEEEFF2),
    buttonDangerBg = Color(0xFFEF4444),
    buttonDangerHover = Color(0xFFDC2626),
    buttonDangerPress = Color(0xFFB91C1C),

    // Inputs
    inputBg = Color.White,
    inputBorder = Color(0xFFE5E7EB),
    inputBorderFocused = Color(0xFF93C5FD),
    inputPlaceholder = Color(0xFF9CA3AF),

    // Chips
    chipBg = Color(0xFFF5F6F8),
    chipBgSelected = Color(0xFFEFF6FF),
    chipBorder = Color(0xFFE5E7EB),
    chipText = Color(0xFF6B7280),
    chipTextSelected = Color(0xFF3B82F6),

    // Canvas
    canvasBackground = Color(0xFFF8F9FA),
    canvasGridDots = Color(0xFFD1D5DB),
    canvasGridMajor = Color(0xFFD1D5DB),
    canvasGridMinor = Color(0xFFE5E7EB),
    blockInnerBorder = Color(0x30000000),
    blockInnerHighlight = Color(0x20FFFFFF),
    edgeDefault = Color(0xFF6B7280),
    edgeSelected = Color(0xFF3B82F6),
    edgeGlow = Color(0x4D3B82F6),
    blockSelectionHighlight = Color(0xFF3B82F6),
    portDefault = Color(0xFF9CA3AF),
    portHover = Color(0xFF3B82F6),
    portHoverRing = Color(0x403B82F6),
    draftEdge = Color(0xFF3B82F6),

    // Block types
    teamcityBuild = Color(0xFF4A90D9),
    githubAction = Color(0xFF8B5CF6),
    githubPublication = Color(0xFF059669),
    slackMessage = Color(0xFFE11D48),
    gateIndicator = Color(0xFF0891B2),
    containerBlock = Color(0xFF6B7280),
    containerBorder = Color(0xFF9CA3AF),
    containerHeaderBg = Color(0xFFF3F4F6),
    containerDropHighlight = Color(0xFF3B82F6),
    containerDetachHighlight = Color(0xFFF59E0B),

    // Block text & shadow
    blockShadow = Color(0x20000000),
    blockText = Color.White,
    blockTextSecondary = Color(0xCCFFFFFF),

    // Status
    statusPending = Color(0xFF9CA3AF),
    statusRunning = Color(0xFF3B82F6),
    statusSuccess = Color(0xFF22C55E),
    statusFailed = Color(0xFFEF4444),
    statusCancelled = Color(0xFFF97316),
    statusArchived = Color(0xFF6B7280),

    // Block execution status
    blockStatusSucceeded = Color(0xFF22C55E),
    blockStatusRunning = Color(0xFF3B82F6),
    blockStatusFailed = Color(0xFFEF4444),
    blockStatusWaiting = Color(0xFF9CA3AF),
    blockStatusWaitingForInput = Color(0xFFF59E0B),
    blockStatusStopped = Color(0xFF0D9488),
    statusStopped = Color(0xFF0D9488),

    // Sidebar — opaque tokens tuned for WCAG AA 4.5:1
    // Tooltip
    tooltipBg = Color(0xFF1F2937),
    tooltipText = Color(0xFFFFFFFF),

    sidebarActiveBg = Color(0xFFEFF6FF),      // matches chipBgSelected
    sidebarActiveText = Color(0xFF2563EB),     // buttonPrimaryHover for AA contrast
    sidebarActiveHoverBg = Color(0xFFDCEAFE),  // slightly saturated

    // Focus ring
    focusRing = Color(0xFF3B82F6),
    focusRingOnColor = Color.White,
)

val DarkAppColors = AppColors(
    isDark = true,
    // Chrome
    chromeBackground = Color(0xFF121218),
    chromeSurface = Color(0xFF1E2230),
    chromeSurfaceSecondary = Color(0xFF1E2130),
    chromeSurfaceElevated = Color(0xFF22252F),
    chromeBorder = Color(0xFF4A5060),
    chromeBorderFocused = Color(0xFF60A5FA),
    chromeTextPrimary = Color(0xFFE5E7EB),
    chromeTextSecondary = Color(0xFF9CA3AF),
    chromeTextTertiary = Color(0xFF6B7280),
    chromeTextTimestamp = Color(0xFF8A94A6),  // muted blue-gray, ~5.3:1 on #121218 — temporal info
    chromeTextMetadata = Color(0xFFB0B8C4),   // brighter blue-gray, ~8.5:1 on #121218 — structural labels

    // Buttons — darkened for WCAG AA 4.5:1 contrast with white text
    buttonPrimaryBg = Color(0xFF2563EB),
    buttonPrimaryHover = Color(0xFF1D4ED8),
    buttonPrimaryPress = Color(0xFF1E40AF),
    buttonPrimaryText = Color.White,
    buttonSecondaryBg = Color(0xFF1E2230),
    buttonSecondaryHover = Color(0xFF282C3A),
    buttonSecondaryPress = Color(0xFF2D3140),
    buttonSecondaryBorder = Color(0xFF3A3F50),
    buttonGhostHover = Color(0xFF2A2E3C),
    buttonGhostPress = Color(0xFF303546),
    // Darkened for WCAG AA 4.5:1 contrast with white text
    buttonDangerBg = Color(0xFFDC2626),
    buttonDangerHover = Color(0xFFB91C1C),
    buttonDangerPress = Color(0xFF991B1B),

    // Inputs
    inputBg = Color(0xFF151820),
    inputBorder = Color(0xFF3D4455),
    inputBorderFocused = Color(0xFF60A5FA),
    inputPlaceholder = Color(0xFF9CA3AF),

    // Chips
    chipBg = Color(0xFF1E2130),
    chipBgSelected = Color(0xFF264B73),
    chipBorder = Color(0xFF2D3140),
    chipText = Color(0xFF9CA3AF),
    chipTextSelected = Color(0xFF60A5FA),

    // Canvas
    canvasBackground = Color(0xFF1A1A2E),
    canvasGridDots = Color(0xFF374151),
    canvasGridMajor = Color(0xFF424960),
    canvasGridMinor = Color(0xFF353B4E),
    blockInnerBorder = Color(0x30FFFFFF),
    blockInnerHighlight = Color(0x15FFFFFF),
    edgeDefault = Color(0xFFBFC5D0),
    edgeSelected = Color(0xFF60A5FA),
    edgeGlow = Color(0x4D60A5FA),
    blockSelectionHighlight = Color(0xFFFFFFFF),
    portDefault = Color(0xFFB0B8C4),
    portHover = Color(0xFF60A5FA),
    portHoverRing = Color(0x4060A5FA),
    draftEdge = Color(0xFF60A5FA),

    // Block types
    teamcityBuild = Color(0xFFA78BFA),
    githubAction = Color(0xFF60A5FA),
    githubPublication = Color(0xFF10B981),
    slackMessage = Color(0xFFFB7185),
    gateIndicator = Color(0xFF22D3EE),
    containerBlock = Color(0xFF94A3B8),
    containerBorder = Color(0xFF64748B),
    containerHeaderBg = Color(0xFF1E293B),
    containerDropHighlight = Color(0xFF60A5FA),
    containerDetachHighlight = Color(0xFFFBBF24),

    // Block text & shadow
    blockShadow = Color(0x30FFFFFF),
    blockText = Color.White,
    blockTextSecondary = Color(0xCCFFFFFF),

    // Status
    statusPending = Color(0xFF6B7280),
    statusRunning = Color(0xFF60A5FA),
    statusSuccess = Color(0xFF4ADE80),
    statusFailed = Color(0xFFF87171),
    statusCancelled = Color(0xFFFBBF24),
    statusArchived = Color(0xFF4B5563),

    // Block execution status
    blockStatusSucceeded = Color(0xFF4ADE80),
    blockStatusRunning = Color(0xFF60A5FA),
    blockStatusFailed = Color(0xFFF87171),
    blockStatusWaiting = Color(0xFF6B7280),
    blockStatusWaitingForInput = Color(0xFFFDE68A),
    blockStatusStopped = Color(0xFF5EEAD4),
    statusStopped = Color(0xFF5EEAD4),

    // Sidebar — opaque tokens tuned for dark theme, WCAG AA compliant
    // Tooltip — brighter than sidebar bg (#1E2230) for visual distinction
    tooltipBg = Color(0xFF3A3F50),
    tooltipText = Color(0xFFE5E7EB),

    sidebarActiveBg = Color(0xFF1D3A5C),       // darker to increase contrast
    sidebarActiveText = Color(0xFF93C5FD),     // blue-300 for AA 4.5:1 against sidebarActiveBg
    sidebarActiveHoverBg = Color(0xFF264B73),  // slightly brighter on hover

    // Focus ring
    focusRing = Color(0xFF60A5FA),
    focusRingOnColor = Color.White,
)

val LocalAppColors = staticCompositionLocalOf { LightAppColors }
