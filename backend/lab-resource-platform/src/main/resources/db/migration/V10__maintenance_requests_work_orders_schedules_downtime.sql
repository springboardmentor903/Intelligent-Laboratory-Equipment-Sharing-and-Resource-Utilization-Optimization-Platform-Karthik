-- Milestone 3: Maintenance Requests, Work Orders, Preventive Schedules & Downtime Tracking

CREATE TABLE IF NOT EXISTS maintenance_requests (
    id BIGSERIAL PRIMARY KEY,
    equipment_id BIGINT NOT NULL REFERENCES equipment(id) ON DELETE CASCADE,
    requester_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    institution_id BIGINT NOT NULL REFERENCES institutions(id) ON DELETE CASCADE,
    title VARCHAR(200) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    priority VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    reviewed_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    reviewed_at TIMESTAMP,
    review_remarks VARCHAR(500),
    work_order_id BIGINT
);

CREATE INDEX IF NOT EXISTS idx_maint_req_equipment ON maintenance_requests(equipment_id);
CREATE INDEX IF NOT EXISTS idx_maint_req_requester ON maintenance_requests(requester_id);
CREATE INDEX IF NOT EXISTS idx_maint_req_institution ON maintenance_requests(institution_id);
CREATE INDEX IF NOT EXISTS idx_maint_req_status ON maintenance_requests(status);

CREATE TABLE IF NOT EXISTS preventive_maintenance_schedules (
    id BIGSERIAL PRIMARY KEY,
    equipment_id BIGINT NOT NULL REFERENCES equipment(id) ON DELETE CASCADE,
    title VARCHAR(200) NOT NULL,
    description VARCHAR(1000),
    repeat_period_days INT NOT NULL,
    last_completed_date DATE,
    next_due_date DATE NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_pms_equipment ON preventive_maintenance_schedules(equipment_id);
CREATE INDEX IF NOT EXISTS idx_pms_next_due ON preventive_maintenance_schedules(next_due_date);
CREATE INDEX IF NOT EXISTS idx_pms_active ON preventive_maintenance_schedules(active);

CREATE TABLE IF NOT EXISTS work_orders (
    id BIGSERIAL PRIMARY KEY,
    work_order_number VARCHAR(50) NOT NULL UNIQUE,
    equipment_id BIGINT NOT NULL REFERENCES equipment(id) ON DELETE CASCADE,
    schedule_id BIGINT REFERENCES preventive_maintenance_schedules(id) ON DELETE SET NULL,
    request_id BIGINT REFERENCES maintenance_requests(id) ON DELETE SET NULL,
    title VARCHAR(200) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    work_order_type VARCHAR(30) NOT NULL DEFAULT 'CORRECTIVE',
    priority VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    status VARCHAR(30) NOT NULL DEFAULT 'OPEN',
    assigned_to BIGINT REFERENCES users(id) ON DELETE SET NULL,
    assigned_at TIMESTAMP,
    scheduled_date TIMESTAMP,
    started_at TIMESTAMP,
    expected_end_time TIMESTAMP,
    completed_at TIMESTAMP,
    cancelled_at TIMESTAMP,
    cancellation_reason VARCHAR(500),
    resolution_notes VARCHAR(1000),
    cost NUMERIC(12, 2),
    downtime_hours DOUBLE PRECISION,
    created_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_wo_number ON work_orders(work_order_number);
CREATE INDEX IF NOT EXISTS idx_wo_equipment ON work_orders(equipment_id);
CREATE INDEX IF NOT EXISTS idx_wo_assigned_to ON work_orders(assigned_to);
CREATE INDEX IF NOT EXISTS idx_wo_status ON work_orders(status);
CREATE INDEX IF NOT EXISTS idx_wo_type ON work_orders(work_order_type);
CREATE INDEX IF NOT EXISTS idx_wo_schedule ON work_orders(schedule_id);

CREATE TABLE IF NOT EXISTS work_order_service_logs (
    id BIGSERIAL PRIMARY KEY,
    work_order_id BIGINT NOT NULL REFERENCES work_orders(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    log_type VARCHAR(50) NOT NULL,
    from_status VARCHAR(30),
    to_status VARCHAR(30),
    notes VARCHAR(1000) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_wosl_work_order ON work_order_service_logs(work_order_id);
CREATE INDEX IF NOT EXISTS idx_wosl_created_at ON work_order_service_logs(created_at);

CREATE TABLE IF NOT EXISTS equipment_downtime_logs (
    id BIGSERIAL PRIMARY KEY,
    equipment_id BIGINT NOT NULL REFERENCES equipment(id) ON DELETE CASCADE,
    work_order_id BIGINT REFERENCES work_orders(id) ON DELETE SET NULL,
    start_time TIMESTAMP NOT NULL,
    expected_end_time TIMESTAMP,
    end_time TIMESTAMP,
    total_downtime_minutes INT,
    operating_downtime_minutes INT,
    reason VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_edl_equipment ON equipment_downtime_logs(equipment_id);
CREATE INDEX IF NOT EXISTS idx_edl_work_order ON equipment_downtime_logs(work_order_id);
CREATE INDEX IF NOT EXISTS idx_edl_start_time ON equipment_downtime_logs(start_time);
