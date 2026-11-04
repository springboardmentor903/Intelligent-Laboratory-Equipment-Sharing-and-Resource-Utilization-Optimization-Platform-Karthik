-- Milestone 2 live equipment usage and monitoring support.
-- Existing equipment remains valid; older inventory may not have an asset tag.
ALTER TABLE equipment
    ADD COLUMN IF NOT EXISTS asset_tag VARCHAR(100);

CREATE UNIQUE INDEX IF NOT EXISTS ux_equipment_asset_tag_ci
    ON equipment (LOWER(asset_tag))
    WHERE asset_tag IS NOT NULL AND TRIM(asset_tag) <> '';

ALTER TABLE utilization_records
    ADD COLUMN IF NOT EXISTS usage_type VARCHAR(30) NOT NULL DEFAULT 'BOOKING';

CREATE INDEX IF NOT EXISTS ix_utilization_active_equipment
    ON utilization_records (equipment_id, end_time)
    WHERE end_time IS NULL;

-- A booking has at most one actual usage session. Existing non-booking records
-- may still have a null booking_id and are unaffected by this constraint.
CREATE UNIQUE INDEX IF NOT EXISTS ux_utilization_booking_id
    ON utilization_records (booking_id)
    WHERE booking_id IS NOT NULL AND end_time IS NULL;
