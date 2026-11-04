-- Milestone 2 Phase 1: Operating hours schedules, waitlist queue, and daily snapshots

CREATE TABLE IF NOT EXISTS equipment_operating_hours (
    id BIGSERIAL PRIMARY KEY,
    equipment_id BIGINT REFERENCES equipment(id) ON DELETE CASCADE,
    institution_id BIGINT REFERENCES institutions(id) ON DELETE CASCADE,
    day_of_week SMALLINT NOT NULL CHECK (day_of_week BETWEEN 1 AND 7), -- 1=Monday, 7=Sunday
    open_time TIME NOT NULL DEFAULT '09:00:00',
    close_time TIME NOT NULL DEFAULT '17:00:00',
    is_closed BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT chk_operating_hours_times CHECK (is_closed OR open_time < close_time)
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_equipment_operating_hours_eq_day
    ON equipment_operating_hours (equipment_id, day_of_week)
    WHERE equipment_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS ux_equipment_operating_hours_inst_day
    ON equipment_operating_hours (institution_id, day_of_week)
    WHERE equipment_id IS NULL AND institution_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS equipment_waitlist (
    id BIGSERIAL PRIMARY KEY,
    equipment_id BIGINT NOT NULL REFERENCES equipment(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    requested_start_time TIMESTAMP NOT NULL,
    requested_end_time TIMESTAMP NOT NULL,
    priority INT NOT NULL DEFAULT 1,
    status VARCHAR(30) NOT NULL DEFAULT 'WAITING',
    notes VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    notified_at TIMESTAMP,
    expires_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS ix_waitlist_equipment_status
    ON equipment_waitlist (equipment_id, status);

CREATE INDEX IF NOT EXISTS ix_waitlist_user_status
    ON equipment_waitlist (user_id, status);

CREATE TABLE IF NOT EXISTS daily_utilization_snapshots (
    id BIGSERIAL PRIMARY KEY,
    snapshot_date DATE NOT NULL,
    equipment_id BIGINT NOT NULL REFERENCES equipment(id) ON DELETE CASCADE,
    institution_id BIGINT,
    department_id BIGINT,
    operating_minutes INT NOT NULL DEFAULT 0,
    used_minutes INT NOT NULL DEFAULT 0,
    utilization_rate NUMERIC(5,2),
    session_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT ux_snapshot_date_equipment UNIQUE (snapshot_date, equipment_id)
);
