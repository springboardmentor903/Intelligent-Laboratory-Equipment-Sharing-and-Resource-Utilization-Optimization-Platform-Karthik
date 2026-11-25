ALTER TABLE equipment
ADD COLUMN IF NOT EXISTS purchase_date DATE,
ADD COLUMN IF NOT EXISTS purchase_cost NUMERIC(14, 2);

-- Populate realistic default purchase dates and costs for existing equipment if null
UPDATE equipment
SET purchase_date = COALESCE(purchase_date, DATE(created_at - INTERVAL '365 days')),
    purchase_cost = COALESCE(purchase_cost, CASE
        WHEN hourly_rate IS NOT NULL AND hourly_rate > 0 THEN hourly_rate * 500
        ELSE 150000.00
    END)
WHERE purchase_date IS NULL OR purchase_cost IS NULL;

CREATE INDEX IF NOT EXISTS idx_equipment_institution_status ON equipment (institution_id, status);
CREATE INDEX IF NOT EXISTS idx_equipment_department_status ON equipment (department_id, status);
