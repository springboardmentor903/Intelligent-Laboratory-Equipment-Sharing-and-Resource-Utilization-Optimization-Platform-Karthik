-- Milestone 3: Maintenance Scheduling, Work Orders & Calibration Tracking Enhancements

-- Maintenance records enhancements
ALTER TABLE maintenance_records ADD COLUMN IF NOT EXISTS maintenance_type VARCHAR(30) DEFAULT 'CORRECTIVE';
ALTER TABLE maintenance_records ADD COLUMN IF NOT EXISTS scheduled_date TIMESTAMP;
ALTER TABLE maintenance_records ADD COLUMN IF NOT EXISTS cost NUMERIC(12,2);

-- Calibration records enhancements
ALTER TABLE calibration_records ADD COLUMN IF NOT EXISTS status VARCHAR(30) DEFAULT 'COMPLETED';
ALTER TABLE calibration_records ADD COLUMN IF NOT EXISTS assigned_to BIGINT;
ALTER TABLE calibration_records ADD COLUMN IF NOT EXISTS certificate_number VARCHAR(100);
ALTER TABLE calibration_records ADD COLUMN IF NOT EXISTS scheduled_date DATE;

-- Make calibration_date and result nullable for scheduled upcoming calibration tasks
ALTER TABLE calibration_records ALTER COLUMN calibration_date DROP NOT NULL;
ALTER TABLE calibration_records ALTER COLUMN result DROP NOT NULL;

-- Create indexes for efficient querying
CREATE INDEX IF NOT EXISTS idx_maintenance_assigned_to ON maintenance_records(assigned_to);
CREATE INDEX IF NOT EXISTS idx_maintenance_scheduled_date ON maintenance_records(scheduled_date);
CREATE INDEX IF NOT EXISTS idx_calibration_assigned_to ON calibration_records(assigned_to);
CREATE INDEX IF NOT EXISTS idx_calibration_status ON calibration_records(status);
CREATE INDEX IF NOT EXISTS idx_calibration_next_due ON calibration_records(next_due_date);
