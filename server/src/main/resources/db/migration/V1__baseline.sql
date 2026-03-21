-- V1: Baseline schema for Release Wizard
-- Generated from Exposed Table definitions (Exposed 1.1.1, PostgreSQL)

-- ============================================================
-- Independent tables (no FK dependencies)
-- ============================================================

CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY,
    username VARCHAR(64) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    password_changed_at TIMESTAMP WITH TIME ZONE NULL,
    CONSTRAINT users_username_unique UNIQUE (username)
);

CREATE TABLE IF NOT EXISTS teams (
    id UUID PRIMARY KEY,
    "name" VARCHAR(255) NOT NULL,
    description TEXT DEFAULT '' NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT teams_name_unique UNIQUE ("name")
);

CREATE TABLE IF NOT EXISTS account_lockouts (
    username VARCHAR(255) NOT NULL,
    attempts INT DEFAULT 0 NOT NULL,
    locked_until TIMESTAMP WITH TIME ZONE NULL,
    last_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_account_lockouts PRIMARY KEY (username)
);

-- ============================================================
-- Tables depending on users / teams
-- ============================================================

CREATE TABLE IF NOT EXISTS team_memberships (
    team_id UUID NOT NULL,
    user_id UUID NOT NULL,
    role VARCHAR(32) NOT NULL,
    joined_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_team_memberships PRIMARY KEY (team_id, user_id),
    CONSTRAINT fk_team_memberships_team_id__id FOREIGN KEY (team_id) REFERENCES teams(id) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_team_memberships_user_id__id FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE ON UPDATE RESTRICT
);
CREATE INDEX IF NOT EXISTS team_memberships_user_id ON team_memberships (user_id);

CREATE TABLE IF NOT EXISTS team_invites (
    id UUID PRIMARY KEY,
    team_id UUID NOT NULL,
    invited_user_id UUID NOT NULL,
    invited_by_user_id UUID NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NULL,
    CONSTRAINT fk_team_invites_team_id__id FOREIGN KEY (team_id) REFERENCES teams(id) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_team_invites_invited_user_id__id FOREIGN KEY (invited_user_id) REFERENCES users(id) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_team_invites_invited_by_user_id__id FOREIGN KEY (invited_by_user_id) REFERENCES users(id) ON DELETE CASCADE ON UPDATE RESTRICT
);
CREATE INDEX IF NOT EXISTS team_invites_invited_user_id ON team_invites (invited_user_id);

CREATE TABLE IF NOT EXISTS join_requests (
    id UUID PRIMARY KEY,
    team_id UUID NOT NULL,
    user_id UUID NOT NULL,
    status VARCHAR(32) NOT NULL,
    reviewed_by_user_id UUID NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    reviewed_at TIMESTAMP WITH TIME ZONE NULL,
    CONSTRAINT fk_join_requests_team_id__id FOREIGN KEY (team_id) REFERENCES teams(id) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_join_requests_user_id__id FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_join_requests_reviewed_by_user_id__id FOREIGN KEY (reviewed_by_user_id) REFERENCES users(id) ON DELETE SET NULL ON UPDATE RESTRICT
);

CREATE TABLE IF NOT EXISTS audit_events (
    id UUID PRIMARY KEY,
    team_id VARCHAR(36) NULL,
    actor_user_id UUID NULL,
    actor_username VARCHAR(255) NOT NULL,
    action VARCHAR(64) NOT NULL,
    target_type VARCHAR(32) NOT NULL,
    target_id VARCHAR(255) NOT NULL,
    details TEXT DEFAULT '' NOT NULL,
    "timestamp" TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_audit_events_actor_user_id__id FOREIGN KEY (actor_user_id) REFERENCES users(id) ON DELETE SET NULL ON UPDATE RESTRICT
);
CREATE INDEX IF NOT EXISTS audit_events_team_id_timestamp ON audit_events (team_id, "timestamp");
CREATE INDEX IF NOT EXISTS audit_events_actor_user_id ON audit_events (actor_user_id);

CREATE TABLE IF NOT EXISTS password_reset_tokens (
    id UUID PRIMARY KEY,
    token_hash VARCHAR(64) NOT NULL,
    user_id UUID NOT NULL,
    created_by_user_id UUID NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    used_at TIMESTAMP WITH TIME ZONE NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT password_reset_tokens_token_hash_unique UNIQUE (token_hash),
    CONSTRAINT fk_password_reset_tokens_user_id__id FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_password_reset_tokens_created_by_user_id__id FOREIGN KEY (created_by_user_id) REFERENCES users(id) ON DELETE SET NULL ON UPDATE RESTRICT
);
CREATE INDEX IF NOT EXISTS password_reset_tokens_user_id ON password_reset_tokens (user_id);

-- ============================================================
-- Tables depending on teams (project-level)
-- ============================================================

CREATE TABLE IF NOT EXISTS project_templates (
    id UUID PRIMARY KEY,
    "name" VARCHAR(255) NOT NULL,
    description TEXT DEFAULT '' NOT NULL,
    dag_graph JSONB NOT NULL,
    parameters JSONB NOT NULL,
    default_tags JSONB NOT NULL,
    team_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_project_templates_team_id__id FOREIGN KEY (team_id) REFERENCES teams(id) ON DELETE RESTRICT ON UPDATE RESTRICT
);
CREATE INDEX IF NOT EXISTS project_templates_team_id ON project_templates (team_id);

CREATE TABLE IF NOT EXISTS connections (
    id UUID PRIMARY KEY,
    "name" VARCHAR(255) NOT NULL,
    "type" VARCHAR(32) NOT NULL,
    encrypted_config TEXT NOT NULL,
    team_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_connections_team_id__id FOREIGN KEY (team_id) REFERENCES teams(id) ON DELETE CASCADE ON UPDATE RESTRICT
);
CREATE INDEX IF NOT EXISTS connections_team_id ON connections (team_id);

-- ============================================================
-- Tables depending on project_templates
-- ============================================================

CREATE TABLE IF NOT EXISTS releases (
    id UUID PRIMARY KEY,
    project_template_id UUID NOT NULL,
    status VARCHAR(32) NOT NULL,
    dag_snapshot JSONB NOT NULL,
    parameters JSONB NOT NULL,
    team_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NULL,
    started_at TIMESTAMP WITH TIME ZONE NULL,
    finished_at TIMESTAMP WITH TIME ZONE NULL,
    CONSTRAINT fk_releases_project_template_id__id FOREIGN KEY (project_template_id) REFERENCES project_templates(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_releases_team_id__id FOREIGN KEY (team_id) REFERENCES teams(id) ON DELETE RESTRICT ON UPDATE RESTRICT
);
CREATE INDEX IF NOT EXISTS releases_status ON releases (status);
CREATE INDEX IF NOT EXISTS releases_project_template_id ON releases (project_template_id);
CREATE INDEX IF NOT EXISTS releases_team_id ON releases (team_id);

CREATE TABLE IF NOT EXISTS notification_configs (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL,
    user_id UUID NOT NULL,
    "type" VARCHAR(32) NOT NULL,
    config JSONB NOT NULL,
    enabled BOOLEAN DEFAULT TRUE NOT NULL,
    CONSTRAINT fk_notification_configs_project_id__id FOREIGN KEY (project_id) REFERENCES project_templates(id) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_notification_configs_user_id__id FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE ON UPDATE RESTRICT
);
CREATE INDEX IF NOT EXISTS notification_configs_project_id ON notification_configs (project_id);

CREATE TABLE IF NOT EXISTS schedules (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL,
    cron_expression VARCHAR(255) NOT NULL,
    parameters JSONB NOT NULL,
    enabled BOOLEAN DEFAULT TRUE NOT NULL,
    created_by UUID NULL,
    next_run_at TIMESTAMP WITH TIME ZONE NULL,
    last_run_at TIMESTAMP WITH TIME ZONE NULL,
    CONSTRAINT fk_schedules_project_id__id FOREIGN KEY (project_id) REFERENCES project_templates(id) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_schedules_created_by__id FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL ON UPDATE RESTRICT
);
CREATE INDEX IF NOT EXISTS schedules_project_id ON schedules (project_id);
CREATE INDEX IF NOT EXISTS idx_schedule_enabled_next_run ON schedules (enabled, next_run_at);

CREATE TABLE IF NOT EXISTS triggers (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL,
    secret VARCHAR(255) NOT NULL,
    enabled BOOLEAN DEFAULT TRUE NOT NULL,
    parameters_template JSONB NOT NULL,
    CONSTRAINT fk_triggers_project_id__id FOREIGN KEY (project_id) REFERENCES project_templates(id) ON DELETE CASCADE ON UPDATE RESTRICT
);
CREATE INDEX IF NOT EXISTS triggers_project_id ON triggers (project_id);

CREATE TABLE IF NOT EXISTS maven_triggers (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL,
    repo_url VARCHAR(512) NOT NULL,
    group_id VARCHAR(255) NOT NULL,
    artifact_id VARCHAR(255) NOT NULL,
    parameter_key VARCHAR(255) NOT NULL,
    enabled BOOLEAN DEFAULT TRUE NOT NULL,
    include_snapshots BOOLEAN DEFAULT FALSE NOT NULL,
    known_versions JSONB NOT NULL,
    last_checked_at TIMESTAMP WITH TIME ZONE NULL,
    created_by UUID NULL,
    CONSTRAINT fk_maven_triggers_project_id__id FOREIGN KEY (project_id) REFERENCES project_templates(id) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_maven_triggers_created_by__id FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL ON UPDATE RESTRICT
);
CREATE INDEX IF NOT EXISTS maven_triggers_project_id ON maven_triggers (project_id);
CREATE INDEX IF NOT EXISTS idx_maven_trigger_enabled_checked ON maven_triggers (enabled, last_checked_at);

CREATE TABLE IF NOT EXISTS project_locks (
    project_id UUID NOT NULL,
    user_id UUID NOT NULL,
    username VARCHAR(255) NOT NULL,
    acquired_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_project_locks PRIMARY KEY (project_id),
    CONSTRAINT fk_project_locks_project_id__id FOREIGN KEY (project_id) REFERENCES project_templates(id) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_project_locks_user_id__id FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE ON UPDATE RESTRICT
);
CREATE INDEX IF NOT EXISTS project_locks_expires_at ON project_locks (expires_at);

-- ============================================================
-- Tables depending on releases
-- ============================================================

CREATE TABLE IF NOT EXISTS block_executions (
    id UUID PRIMARY KEY,
    release_id UUID NOT NULL,
    block_id VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL,
    outputs JSONB NOT NULL,
    error TEXT NULL,
    started_at TIMESTAMP WITH TIME ZONE NULL,
    finished_at TIMESTAMP WITH TIME ZONE NULL,
    approvals JSONB NOT NULL,
    gate_phase VARCHAR(32) NULL,
    gate_message TEXT NULL,
    webhook_status VARCHAR(200) NULL,
    webhook_status_description TEXT NULL,
    webhook_status_at TIMESTAMP WITH TIME ZONE NULL,
    sub_builds JSONB DEFAULT '[]' NOT NULL,
    CONSTRAINT fk_block_executions_release_id__id FOREIGN KEY (release_id) REFERENCES releases(id) ON DELETE CASCADE ON UPDATE RESTRICT
);
CREATE INDEX IF NOT EXISTS block_executions_release_id ON block_executions (release_id);
CREATE UNIQUE INDEX IF NOT EXISTS block_executions_release_id_block_id_unique ON block_executions (release_id, block_id);

CREATE TABLE IF NOT EXISTS release_tags (
    release_id UUID NOT NULL,
    tag VARCHAR(255) NOT NULL,
    team_id UUID NOT NULL,
    CONSTRAINT pk_release_tags PRIMARY KEY (release_id, tag),
    CONSTRAINT fk_release_tags_release_id__id FOREIGN KEY (release_id) REFERENCES releases(id) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_release_tags_team_id__id FOREIGN KEY (team_id) REFERENCES teams(id) ON DELETE CASCADE ON UPDATE RESTRICT
);
CREATE INDEX IF NOT EXISTS release_tags_tag ON release_tags (tag);
CREATE INDEX IF NOT EXISTS release_tags_team_id ON release_tags (team_id);
CREATE INDEX IF NOT EXISTS idx_release_tags_team_tag ON release_tags (team_id, tag);

CREATE TABLE IF NOT EXISTS status_webhook_tokens (
    id UUID PRIMARY KEY,
    release_id UUID NOT NULL,
    block_id VARCHAR(255) NOT NULL,
    active BOOLEAN DEFAULT TRUE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_status_webhook_tokens_release_id__id FOREIGN KEY (release_id) REFERENCES releases(id) ON DELETE CASCADE ON UPDATE RESTRICT
);
CREATE INDEX IF NOT EXISTS idx_swt_release_block ON status_webhook_tokens (release_id, block_id);
