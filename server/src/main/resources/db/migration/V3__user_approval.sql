-- Admin approval for basic registration: new users require admin approval before accessing the app.
-- Default TRUE grandfathers all existing users as approved.
-- Use DO block for idempotent ADD COLUMN (PostgreSQL doesn't support IF NOT EXISTS for ADD COLUMN < v9.6)
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='users' AND column_name='approved') THEN
        ALTER TABLE users ADD COLUMN approved BOOLEAN DEFAULT TRUE NOT NULL;
    END IF;
END $$;

-- Partial index for efficient admin lookup of pending users is created by
-- createPartialIndexes() in DatabaseFactory.kt (H2 does not support WHERE clauses in indexes).
