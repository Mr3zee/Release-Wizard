# APP_WRITING_ORPHAN_STRINGS — Defined but Unused String Resources

String resources that exist in `strings.xml` and all 6 language packs but are never referenced
in Kotlin UI code. These inflate translation burden and may indicate missing features.

## Issues

### EDITOR_09 — Editor dialog titles (MEDIUM)
Three strings never referenced:
- `editor_dialog_lock_expired_title` ("Editing Lock Expired")
- `editor_dialog_force_unlock_title` ("Force Unlock")
- `editor_template_picker_title` ("Insert Template")

Likely leftover from dialog-based UI migrated to inline confirmations.

### RELDETAIL_01 — Release detail screen titles and labels (HIGH)
Seven strings never referenced:
- `releases_cancel_title` ("Cancel Release")
- `releases_keep_running` ("Keep Running")
- `releases_stop_title` ("Stop Release?")
- `releases_stop_block_title` ("Stop Block?")
- `releases_approve_title` ("Approve: %1$s")
- `releases_approve_fallback_name` ("Step")
- `releases_webhook_status_label` ("Build Status")

### AUTOMATION_01/02 — Automation screen titles and parameters (MEDIUM)
Five strings never referenced:
- `automation_delete_confirm_title` ("Delete?")
- `schedule_parameters_label` ("Parameters (optional)")
- `webhook_create_title` ("New Webhook Trigger")
- `webhook_parameters_label` ("Parameters Template (optional)")
- `maven_created_message` ("Only newly published versions will trigger releases.")

The last one contains important behavioral information that should be surfaced to users.

### CONNLIST_01 — Connection delete title (HIGH)
- `connections_delete_title` ("Delete Connection") — never passed to `RwInlineConfirmation`.

### CONNLIST_02 — Connection webhook display (HIGH)
- `connections_webhook_display` ("Webhook: %1$s") — never used; webhook URL displayed raw.

### PROJLIST_06 — Project delete title (LOW)
- `projects_delete_title` ("Delete Project") — never referenced.

### RELLIST_07 — Release archive/delete titles (MEDIUM)
- `releases_archive_title` ("Archive Release") — never passed to confirmation.
- `releases_delete_title` ("Delete Release") — never passed to confirmation.

### TEAMDETAIL_04 — Team leave title and fallback (MEDIUM)
- `teams_leave_title` ("Leave Team") — never used; toolbar shows bare "Leave".
- `teams_leave_fallback` ("this team") — never referenced.

### TEAMMGMT_13 — Short-form promote/demote (LOW)
- `teams_promote` ("Promote") — only long-form `teams_promote_to_lead` used.
- `teams_demote` ("Demote") — only long-form `teams_demote_to_collaborator` used.

### TEAMMGMT_11 — teams_keep (LOW)
- `teams_keep` ("Keep") — defined but never used as dismiss label.

### CONNFORM_06 — Duplicate polling hint (MEDIUM)
- `connections_polling_default_hint` ("Default: 30 seconds (range: 5-300)") — only
  `connections_polling_range_hint` is used.

### AUDIT_09 — Load more (LOW)
- `teams_load_more` ("Load more") — auto-scroll triggers load; text never displayed.

### GLOBAL_11 — Duplicate close strings (MEDIUM)
- `shortcuts_close` ("Close") duplicates `common_close` ("Close").

### GLOBAL_18 — Duplicate sign-out strings (LOW)
- `sidebar_sign_out` ("Sign Out") duplicates `auth_sign_out` ("Sign Out").

**Fix for all:** Wire each string to its intended location, or remove from `strings.xml`
and all 6 language packs. For strings that contain useful information (like
`maven_created_message`), prioritize wiring over removal.

## Resolution

**Status:** RESOLVED

| Issue | Status | Notes |
|-------|--------|-------|
| EDITOR_09 | Fixed | 3 orphan dialog titles removed from strings.xml and all 6 language packs |
| RELDETAIL_01 | Fixed | 7 orphan release detail strings removed |
| AUTOMATION_01/02 | Fixed | 5 orphan automation strings removed |
| CONNLIST_01 | Fixed | `connections_delete_title` removed |
| CONNLIST_02 | Fixed | `connections_webhook_display` removed |
| PROJLIST_06 | Fixed | `projects_delete_title` removed |
| RELLIST_07 | Fixed | 2 orphan release archive/delete titles removed |
| TEAMDETAIL_04 | Fixed | 2 orphan team leave strings removed |
| TEAMMGMT_13 | Fixed | 2 short-form promote/demote strings removed |
| TEAMMGMT_11 | Fixed | `teams_keep` removed |
| CONNFORM_06 | Fixed | `connections_polling_default_hint` removed |
| AUDIT_09 | Fixed | `teams_load_more` removed |
| GLOBAL_11 | Fixed | `shortcuts_close` duplicate removed |
| GLOBAL_18 | Fixed | `sidebar_sign_out` duplicate removed |

18 strings removed total from strings.xml and all 6 language packs.
