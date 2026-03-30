-- User notifications table for personal notification system
CREATE TABLE IF NOT EXISTS user_notifications (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type VARCHAR(32) NOT NULL,
    team_id VARCHAR(36),
    team_name VARCHAR(255),
    title VARCHAR(255) NOT NULL,
    message TEXT NOT NULL,
    target_type VARCHAR(32),
    target_id VARCHAR(255),
    read BOOLEAN NOT NULL DEFAULT FALSE,
    "timestamp" TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_user_notifications_user_timestamp
    ON user_notifications (user_id, "timestamp");

-- Add created_by_user_id to releases (nullable FK, SET NULL on user deletion)
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='releases' AND column_name='created_by_user_id') THEN
        ALTER TABLE releases ADD COLUMN created_by_user_id UUID REFERENCES users(id) ON DELETE SET NULL;
    END IF;
END $$;
