package com.github.mr3zee.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class AppColors(
    // Canvas
    val canvasBackground: Color,
    val canvasGridDots: Color,

    // Edges
    val edgeDefault: Color,
    val edgeSelected: Color,

    // Block selection
    val blockSelectionHighlight: Color,

    // Port colors
    val portDefault: Color,
    val portHover: Color,

    // Draft edge
    val draftEdge: Color,

    // Block type colors
    val teamcityBuild: Color,
    val githubAction: Color,
    val githubPublication: Color,
    val mavenCentral: Color,
    val slackMessage: Color,
    val userAction: Color,
    val containerBlock: Color,

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
)

val LightAppColors = AppColors(
    canvasBackground = Color(0xFFF8F9FA),
    canvasGridDots = Color(0xFFD1D5DB),
    edgeDefault = Color(0xFF6B7280),
    edgeSelected = Color(0xFF3B82F6),
    blockSelectionHighlight = Color(0xFF3B82F6),
    portDefault = Color(0xFF9CA3AF),
    portHover = Color(0xFF3B82F6),
    draftEdge = Color(0xFF3B82F6),
    teamcityBuild = Color(0xFF4A90D9),
    githubAction = Color(0xFF8B5CF6),
    githubPublication = Color(0xFF059669),
    mavenCentral = Color(0xFFF59E0B),
    slackMessage = Color(0xFFE11D48),
    userAction = Color(0xFF0D9488),
    containerBlock = Color(0xFF6B7280),
    blockShadow = Color(0x20000000),
    blockText = Color.White,
    blockTextSecondary = Color(0xCCFFFFFF),
    statusPending = Color(0xFF9CA3AF),
    statusRunning = Color(0xFF3B82F6),
    statusSuccess = Color(0xFF22C55E),
    statusFailed = Color(0xFFEF4444),
    statusCancelled = Color(0xFFF97316),
    statusArchived = Color(0xFF6B7280),
    blockStatusSucceeded = Color(0xFF22C55E),
    blockStatusRunning = Color(0xFF3B82F6),
    blockStatusFailed = Color(0xFFEF4444),
    blockStatusWaiting = Color(0xFF9CA3AF),
    blockStatusWaitingForInput = Color(0xFFF59E0B),
)

val DarkAppColors = AppColors(
    canvasBackground = Color(0xFF1A1A2E),
    canvasGridDots = Color(0xFF374151),
    edgeDefault = Color(0xFF9CA3AF),
    edgeSelected = Color(0xFF60A5FA),
    blockSelectionHighlight = Color(0xFF60A5FA),
    portDefault = Color(0xFF6B7280),
    portHover = Color(0xFF60A5FA),
    draftEdge = Color(0xFF60A5FA),
    teamcityBuild = Color(0xFFA78BFA),
    githubAction = Color(0xFF60A5FA),
    githubPublication = Color(0xFF34D399),
    mavenCentral = Color(0xFFFBBF24),
    slackMessage = Color(0xFFFB7185),
    userAction = Color(0xFF22D3EE),
    containerBlock = Color(0xFF94A3B8),
    blockShadow = Color(0x30FFFFFF),
    blockText = Color.White,
    blockTextSecondary = Color(0xCCFFFFFF),
    statusPending = Color(0xFF6B7280),
    statusRunning = Color(0xFF60A5FA),
    statusSuccess = Color(0xFF4ADE80),
    statusFailed = Color(0xFFF87171),
    statusCancelled = Color(0xFFFBBF24),
    statusArchived = Color(0xFF4B5563),
    blockStatusSucceeded = Color(0xFF4ADE80),
    blockStatusRunning = Color(0xFF60A5FA),
    blockStatusFailed = Color(0xFFF87171),
    blockStatusWaiting = Color(0xFF6B7280),
    blockStatusWaitingForInput = Color(0xFFFDE68A),
)

val LocalAppColors = staticCompositionLocalOf { LightAppColors }
