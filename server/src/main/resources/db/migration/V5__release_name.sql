-- Add customizable name column to releases table
ALTER TABLE releases ADD COLUMN IF NOT EXISTS name VARCHAR(255) NOT NULL DEFAULT '';
