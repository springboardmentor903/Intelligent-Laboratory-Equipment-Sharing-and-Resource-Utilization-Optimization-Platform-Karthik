-- Migration V4: Add approval reviewer, decision timestamp, and rejection reason metadata

ALTER TABLE users ADD COLUMN IF NOT EXISTS reviewed_by BIGINT NULL;
ALTER TABLE users ADD COLUMN IF NOT EXISTS reviewed_at TIMESTAMP NULL;
ALTER TABLE users ADD COLUMN IF NOT EXISTS rejection_reason VARCHAR(500) NULL;

ALTER TABLE institutions ADD COLUMN IF NOT EXISTS reviewed_by BIGINT NULL;
ALTER TABLE institutions ADD COLUMN IF NOT EXISTS reviewed_at TIMESTAMP NULL;
ALTER TABLE institutions ADD COLUMN IF NOT EXISTS rejection_reason VARCHAR(500) NULL;

CREATE INDEX IF NOT EXISTS ix_users_requested_role ON users(requested_role);
