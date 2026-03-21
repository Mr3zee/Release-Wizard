package com.github.mr3zee.navigation

/**
 * Top-level navigation sections shown in the sidebar.
 * Each section maps to a top-level screen and a keyboard shortcut (Alt+[shortcutKey]).
 */
enum class NavSection(val shortcutKey: Int) {
    PROJECTS(1),
    RELEASES(2),
    CONNECTIONS(3),
    TEAMS(4),
}

fun NavSection.toScreen(): Screen = when (this) {
    NavSection.PROJECTS -> Screen.ProjectList
    NavSection.RELEASES -> Screen.ReleaseList
    NavSection.CONNECTIONS -> Screen.ConnectionList
    NavSection.TEAMS -> Screen.TeamList
}

/** Returns the [NavSection] for top-level screens, null for detail screens. */
fun Screen.toNavSection(): NavSection? = when (this) {
    is Screen.ProjectList -> NavSection.PROJECTS
    is Screen.ReleaseList -> NavSection.RELEASES
    is Screen.ConnectionList -> NavSection.CONNECTIONS
    is Screen.TeamList -> NavSection.TEAMS
    else -> null
}

/** True if this screen is a top-level section (sidebar visible). */
fun Screen.isTopLevel(): Boolean = toNavSection() != null

/**
 * Returns the parent [NavSection] for any screen.
 * Top-level screens return their own section; detail screens return the section they belong to.
 * Used by [NavigationController.navigateFromExternal] to reconstruct the back stack.
 */
// todo claude: unused
fun Screen.parentSection(): NavSection? = when (this) {
    is Screen.ProjectList -> NavSection.PROJECTS
    is Screen.ProjectEditor, is Screen.ProjectAutomation -> NavSection.PROJECTS
    is Screen.ReleaseList -> NavSection.RELEASES
    is Screen.ReleaseView -> NavSection.RELEASES
    is Screen.ConnectionList -> NavSection.CONNECTIONS
    is Screen.ConnectionForm -> NavSection.CONNECTIONS
    is Screen.TeamList -> NavSection.TEAMS
    is Screen.TeamDetail, is Screen.TeamManage, is Screen.AuditLog, Screen.MyInvites -> NavSection.TEAMS
    is Screen.Profile -> null
    is Screen.ResetPassword -> null
    is Screen.AdminUsers -> null
}
