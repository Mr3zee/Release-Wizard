package com.github.mr3zee

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onKeyEvent
import com.github.mr3zee.keyboard.KeyboardShortcutsOverlay
import com.github.mr3zee.keyboard.LocalShortcutActionsSetter
import com.github.mr3zee.keyboard.ShortcutActions
import com.github.mr3zee.keyboard.handleGlobalKeyEvent
import com.github.mr3zee.i18n.packStringResource
import com.github.mr3zee.api.AuthApiClient
import releasewizard.composeapp.generated.resources.*
import com.github.mr3zee.api.ConnectionApiClient
import com.github.mr3zee.api.MavenTriggerApiClient
import com.github.mr3zee.api.ProjectApiClient
import com.github.mr3zee.api.ReleaseApiClient
import com.github.mr3zee.api.ScheduleApiClient
import com.github.mr3zee.api.TeamApiClient
import com.github.mr3zee.api.WebhookTriggerApiClient
import com.github.mr3zee.api.createHttpClient
import com.github.mr3zee.auth.AuthEventBus
import com.github.mr3zee.auth.AuthEvent
import com.github.mr3zee.auth.AuthViewModel
import com.github.mr3zee.auth.LoginScreen
import com.github.mr3zee.auth.PendingApprovalScreen
import com.github.mr3zee.util.RuntimeContext
import com.github.mr3zee.util.UiMessage
import com.github.mr3zee.util.currentRuntimeContext
import com.github.mr3zee.connections.ConnectionsViewModel
import com.github.mr3zee.profile.ProfileViewModel
import com.github.mr3zee.profile.ResetPasswordScreen
import com.github.mr3zee.profile.ResetPasswordViewModel
import com.github.mr3zee.navigation.AppNavigation
import com.github.mr3zee.navigation.AppShell
import com.github.mr3zee.navigation.NavigationController
import com.github.mr3zee.navigation.Screen
import com.github.mr3zee.navigation.SidebarSettingsContent
import com.github.mr3zee.navigation.SidebarTeamSwitcher
import com.github.mr3zee.navigation.createPlatformRouter
import com.github.mr3zee.navigation.isTopLevel
import com.github.mr3zee.navigation.parseUrlPath
import com.github.mr3zee.navigation.toNavSection
import com.github.mr3zee.navigation.toUrlPath
import com.github.mr3zee.projects.ProjectListViewModel
import com.github.mr3zee.model.TeamId
import com.github.mr3zee.releases.ReleaseListViewModel
import com.github.mr3zee.theme.AppTheme
import com.github.mr3zee.theme.ThemePreference
import com.github.mr3zee.theme.loadLanguagePack
import com.github.mr3zee.theme.loadThemePreference
import com.github.mr3zee.theme.saveLanguagePack
import com.github.mr3zee.theme.saveThemePreference
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * CompositionLocal for the server-driven password policy hint string.
 * Screens read this instead of receiving it via prop drilling.
 */
val LocalPasswordPolicyHint = compositionLocalOf<String?> { null }

@Composable
fun App() {
    val httpClient = remember { createHttpClient() }
    DisposableEffect(Unit) {
        onDispose { httpClient.close() }
    }
    val authApiClient = remember { AuthApiClient(httpClient) }
    val projectApiClient = remember { ProjectApiClient(httpClient) }
    val connectionApiClient = remember { ConnectionApiClient(httpClient) }
    val releaseApiClient = remember { ReleaseApiClient(httpClient) }
    val teamApiClient = remember { TeamApiClient(httpClient) }
    val scheduleApiClient = remember { ScheduleApiClient(httpClient) }
    val webhookTriggerApiClient = remember { WebhookTriggerApiClient(httpClient) }
    val mavenTriggerApiClient = remember { MavenTriggerApiClient(httpClient) }

    val activeTeamId = remember { MutableStateFlow<TeamId?>(null) }

    val authViewModel = remember { AuthViewModel(authApiClient) }
    val projectListViewModel = remember { ProjectListViewModel(projectApiClient, activeTeamId) }
    val connectionsViewModel = remember { ConnectionsViewModel(connectionApiClient, activeTeamId) }
    val releaseListViewModel = remember { ReleaseListViewModel(releaseApiClient, projectApiClient, activeTeamId) }

    // Fetch password policy from server, build localized hint in composable context
    var passwordPolicy by remember { mutableStateOf<com.github.mr3zee.api.PasswordPolicyResponse?>(null) }
    LaunchedEffect(Unit) {
        try {
            passwordPolicy = authApiClient.getPasswordPolicy()
        } catch (_: Exception) {
            // Fallback to static string resource if server unreachable
            passwordPolicy = null
        }
    }
    val passwordPolicyHint = passwordPolicy?.let { policy ->
        val parts = mutableListOf(packStringResource(Res.string.auth_policy_min_length, policy.minLength))
        val requirements = mutableListOf<String>()
        if (policy.requireUppercase) requirements += packStringResource(Res.string.auth_policy_require_uppercase)
        if (policy.requireDigit) requirements += packStringResource(Res.string.auth_policy_require_digit)
        if (policy.requireSpecial) requirements += packStringResource(Res.string.auth_policy_require_special)
        val reqText = when (requirements.size) {
            0 -> ""
            1 -> requirements[0]
            else -> requirements.dropLast(1).joinToString(", ") + ", and " + requirements.last()
        }
        if (reqText.isNotEmpty()) parts += packStringResource(Res.string.auth_policy_including, reqText)
        parts.joinToString(", ")
    }

    val profileViewModel = remember { ProfileViewModel(authApiClient) }
    LaunchedEffect(profileViewModel) {
        profileViewModel.onUsernameChanged = { updatedUserInfo ->
            authViewModel.updateUser(updatedUserInfo)
        }
    }

    val user by authViewModel.user.collectAsState()
    val isCheckingSession by authViewModel.isCheckingSession.collectAsState()
    val currentTeamId by activeTeamId.collectAsState()

    var themePreference by remember { mutableStateOf(loadThemePreference()) }
    var languagePack by remember { mutableStateOf(loadLanguagePack()) }

    val router = remember { createPlatformRouter() }
    val navController = remember { NavigationController() }
    val currentScreen = navController.currentScreen

    // URL restoration — gated behind auth with one-shot flag to avoid race with checkSession
    var hasRestoredUrl by remember { mutableStateOf(false) }

    // URL sync via snapshotFlow — fires AFTER snapshot is applied, safe for DOM side-effects.
    // Gated behind hasRestoredUrl so we don't push /projects before deep-link restoration completes.
    LaunchedEffect(navController) {
        snapshotFlow { navController.currentScreen }
            .collect { screen ->
                if (!navController.suppressUrlSync && hasRestoredUrl) {
                    router.pushPath(screen.toUrlPath())
                }
                navController.suppressUrlSync = false
            }
    }

    // Auto-select team when user info changes, or redirect to team list if no teams
    LaunchedEffect(user) {
        val currentUser = user
        if (currentUser != null) {
            // Restore URL-based navigation on first auth (deep linking)
            if (!hasRestoredUrl) {
                hasRestoredUrl = true
                val initialScreen = parseUrlPath(router.currentPath())
                if (initialScreen != null && initialScreen != Screen.ProjectList) {
                    navController.navigateFromExternal(initialScreen)
                }
            }
            if (currentUser.teams.isNotEmpty()) {
                if (activeTeamId.value == null) {
                    activeTeamId.value = currentUser.teams.first().teamId
                }
            } else {
                navController.resetTo(Screen.TeamList)
            }
        }
    }

    // Ungated URL check — detect /reset-password/{token} before auth completes
    LaunchedEffect(Unit) {
        val initialPath = router.currentPath()
        val initialScreen = parseUrlPath(initialPath)
        if (initialScreen is Screen.ResetPassword) {
            navController.navigateFromExternal(initialScreen)
        }
    }

    LaunchedEffect(Unit) {
        authViewModel.checkSession()
    }

    // Parse OAuth redirect error from query string (e.g., /?error=google_auth_failed)
    LaunchedEffect(Unit) {
        val query = router.currentQuery()
        val errorParam = query.split("&")
            .firstOrNull { it.startsWith("error=") }
            ?.removePrefix("error=")
        when (errorParam) {
            "google_auth_failed" -> authViewModel.setError(UiMessage.GoogleAuthFailed)
            "google_auth_cancelled" -> authViewModel.setError(UiMessage.GoogleAuthCancelled)
        }
        // Clean the error param from the URL so it doesn't persist on refresh
        if (errorParam != null) {
            router.replacePath(router.currentPath())
        }
    }

    // Handle browser back/forward button
    DisposableEffect(router) {
        val dispose = router.onPopState { path ->
            parseUrlPath(path)?.let { navController.navigateFromExternal(it) }
        }
        onDispose { dispose() }
    }

    LaunchedEffect(Unit) {
        AuthEventBus.events.collect { event ->
            when (event) {
                is AuthEvent.SessionExpired -> {
                    authViewModel.onSessionExpired()
                    activeTeamId.value = null
                    navController.resetTo(Screen.ProjectList)
                    router.replacePath("/projects")
                }
            }
        }
    }

    var showShortcutsOverlay by remember { mutableStateOf(false) }

    // App-level mutable state for shortcut actions — screens push their actions
    // via ProvideShortcutActions + LocalShortcutActionsSetter callback.
    val shortcutActionsState = remember { mutableStateOf(ShortcutActions()) }

    val toggleTheme = {
        val next = when (themePreference) {
            ThemePreference.SYSTEM -> ThemePreference.LIGHT
            ThemePreference.LIGHT -> ThemePreference.DARK
            ThemePreference.DARK -> ThemePreference.SYSTEM
        }
        themePreference = next
        saveThemePreference(next)
    }

    val logout = {
        authViewModel.logout()
        activeTeamId.value = null
        navController.resetTo(Screen.ProjectList)
        router.replacePath("/projects")
    }

    val accountDeleted = {
        authViewModel.onAccountDeleted()
        activeTeamId.value = null
        navController.resetTo(Screen.ProjectList)
        router.replacePath("/projects")
    }

    AppTheme(themePreference = themePreference, languagePack = languagePack) {
        CompositionLocalProvider(LocalPasswordPolicyHint provides passwordPolicyHint) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .onKeyEvent { event ->
                    // Read shortcutActionsState.value inside the lambda (not captured at composition)
                    // so it always reflects the latest screen-provided actions.
                    handleGlobalKeyEvent(
                        event = event,
                        shortcutActions = shortcutActionsState.value,
                        onNavigateToSection = { navController.navigateToSection(it) },
                        onGoBack = { navController.goBack() },
                        onToggleTheme = toggleTheme,
                        onToggleShortcutsOverlay = { showShortcutsOverlay = !showShortcutsOverlay },
                        isShortcutsOverlayOpen = showShortcutsOverlay,
                    )
                },
            color = MaterialTheme.colorScheme.background,
        ) {
            // Provide the setter callback so screens can push their ShortcutActions
            // up to the app-level key handler via ProvideShortcutActions().
            CompositionLocalProvider(
                LocalShortcutActionsSetter provides { shortcutActionsState.value = it },
            ) {

                when {
                    currentScreen is Screen.ResetPassword -> {
                        val token = currentScreen.token
                        val resetViewModel = remember(token) { ResetPasswordViewModel(token, authApiClient) }
                        ResetPasswordScreen(
                            viewModel = resetViewModel,
                            onGoToLogin = {
                                navController.resetTo(Screen.ProjectList)
                                router.replacePath("/projects")
                            },
                        )
                    }
                    isCheckingSession -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                    user == null -> {
                        val isGoogleAvailable = passwordPolicy?.oauthProviders
                            ?.contains(com.github.mr3zee.api.OAuthProvider.GOOGLE) == true
                        LoginScreen(
                            viewModel = authViewModel,
                            showGoogleLogin = currentRuntimeContext() == RuntimeContext.BROWSER && isGoogleAvailable,
                        )
                    }
                    user?.approved == false -> {
                        PendingApprovalScreen(
                            onCheckStatus = { authViewModel.checkSession() },
                            onSignOut = logout,
                        )
                    }
                    user != null && user?.teams?.isNotEmpty() == true && currentTeamId == null -> {
                        // Waiting for team auto-selection from LaunchedEffect(user)
                        LaunchedEffect(Unit) {
                            kotlinx.coroutines.delay(3000L)
                            // If still no team selected after 3s, something went wrong — show teams list
                            if (activeTeamId.value == null) {
                                navController.resetTo(Screen.TeamList)
                            }
                        }
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                    else -> {
                        val currentUserTeams = user?.teams ?: emptyList()
                        AppShell(
                            sidebarVisible = currentScreen.isTopLevel(),
                            currentSection = currentScreen.toNavSection(),
                            isProfileActive = currentScreen is Screen.Profile || currentScreen is Screen.AdminUsers,
                            onSectionClick = { section ->
                                if (!shortcutActionsState.value.hasDialogOpen) {
                                    navController.navigateToSection(section)
                                }
                            },
                            teamSwitcher = { collapsed ->
                                SidebarTeamSwitcher(
                                    userTeams = currentUserTeams,
                                    activeTeamId = activeTeamId,
                                    onTeamChanged = { teamId -> activeTeamId.value = teamId },
                                    collapsed = collapsed,
                                )
                            },
                            settingsContent = { collapsed ->
                                SidebarSettingsContent(
                                    collapsed = collapsed,
                                    themePreference = themePreference,
                                    onThemeChange = {
                                        themePreference = it
                                        saveThemePreference(it)
                                    },
                                    languagePack = languagePack,
                                    onLanguagePackChange = {
                                        languagePack = it
                                        saveLanguagePack(it)
                                    },
                                    onShowShortcuts = { showShortcutsOverlay = true },
                                )
                            },
                            username = user?.username,
                            onProfileClick = { navController.navigate(Screen.Profile) },
                            onSignOut = logout,
                        ) {
                            AppNavigation(
                                currentScreen = currentScreen,
                                onNavigate = { navController.navigate(it) },
                                onGoBack = { navController.goBack() },
                                projectListViewModel = projectListViewModel,
                                projectApiClient = projectApiClient,
                                releaseApiClient = releaseApiClient,
                                releaseListViewModel = releaseListViewModel,
                                connectionsViewModel = connectionsViewModel,
                                connectionApiClient = connectionApiClient,
                                teamApiClient = teamApiClient,
                                scheduleApiClient = scheduleApiClient,
                                webhookTriggerApiClient = webhookTriggerApiClient,
                                mavenTriggerApiClient = mavenTriggerApiClient,
                                userTeams = currentUserTeams,
                                currentUserId = user?.id,
                                currentUserRole = user?.role,
                                onLogout = logout,
                                onAccountDeleted = accountDeleted,
                                onTeamChanged = { teamId ->
                                    activeTeamId.value = teamId
                                },
                                onRefreshUser = { authViewModel.checkSession() },
                                profileViewModel = profileViewModel,
                                authApiClient = authApiClient,
                            )
                        }
                    }
                }
            }

            // Shortcuts help overlay — rendered above all content
            KeyboardShortcutsOverlay(
                visible = showShortcutsOverlay,
                onDismiss = { showShortcutsOverlay = false },
            )
        }
        }
    }
}
