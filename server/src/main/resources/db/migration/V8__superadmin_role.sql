-- Promote the earliest-created ADMIN to SUPERADMIN.
-- New deployments will assign SUPERADMIN directly at first-user registration.
UPDATE users
SET role = 'SUPERADMIN'
WHERE id = (
    SELECT id FROM users
    WHERE role = 'ADMIN'
    ORDER BY created_at ASC
    LIMIT 1
);
