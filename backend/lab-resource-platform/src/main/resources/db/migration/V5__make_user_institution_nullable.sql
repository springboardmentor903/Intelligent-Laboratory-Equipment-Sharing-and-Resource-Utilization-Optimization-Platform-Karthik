-- Make institution_id nullable to support platform-wide SYSTEM_ADMIN accounts
-- System administrators are platform-level and institution-independent (institution_id IS NULL).
ALTER TABLE users ALTER COLUMN institution_id DROP NOT NULL;
