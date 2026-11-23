-- =============================================================================
-- Demo Database Seed Script for Lab Resource Platform
-- Clean, realistic presentation dataset for all 6 roles across 3 institutions
-- =============================================================================

-- Milestone 3 schema alignment for calibration nullable fields
ALTER TABLE calibration_records ALTER COLUMN calibration_date DROP NOT NULL;
ALTER TABLE calibration_records ALTER COLUMN result DROP NOT NULL;

-- 1. Safely truncate all tables and restart identities
TRUNCATE TABLE 
    booking_audit_logs,
    utilization_records,
    equipment_waitlist,
    daily_utilization_snapshots,
    sharing_access_grants,
    sharing_agreements,
    sharing_requests,
    sharing_listings,
    calibration_records,
    maintenance_records,
    certification_records,
    bookings,
    equipment_operating_hours,
    equipment_tags,
    equipment,
    password_reset_tokens,
    user_roles,
    users,
    departments,
    institutions
RESTART IDENTITY CASCADE;

-- 2. Seed / Upsert Baseline Roles
INSERT INTO roles (id, name, description) VALUES
    (1, 'RESEARCHER_STUDENT', 'Searches equipment and makes bookings'),
    (2, 'LAB_TECHNICIAN', 'Performs maintenance and calibration tasks'),
    (3, 'LAB_MANAGER', 'Approves bookings and monitors lab usage'),
    (4, 'DEPARTMENT_HEAD', 'Oversees all labs of a department'),
    (5, 'INSTITUTION_ADMIN', 'Manages the institution, its users and sharing'),
    (6, 'SYSTEM_ADMIN', 'Manages the whole platform')
ON CONFLICT (name) DO UPDATE SET description = EXCLUDED.description;

-- 3. Seed 3 Premier Institutions
INSERT INTO institutions (id, name, location, address, contact_email, phone_number, status, created_at) VALUES
    (1, 'Malla Reddy Engineering College', 'Hyderabad, Telangana', 'Maisammaguda, Dhulapally, Secunderabad, Telangana 500100', 'contact@mrec.ac.in', '+91 9348161225', 'APPROVED', NOW() - INTERVAL '30 days'),
    (2, 'National Institute of Technology Warangal', 'Warangal, Telangana', 'Hanamkonda, Warangal, Telangana 506004', 'registrar@nitw.ac.in', '+91 8702459191', 'APPROVED', NOW() - INTERVAL '30 days'),
    (3, 'Indian Institute of Technology Hyderabad', 'Sangareddy, Telangana', 'Near NH-65, Kandi, Sangareddy, Telangana 502285', 'office@iith.ac.in', '+91 4023016033', 'APPROVED', NOW() - INTERVAL '30 days');

-- 4. Seed Departments
INSERT INTO departments (id, institution_id, name, created_at) VALUES
    (1, 1, 'CSE / IoT Lab', NOW() - INTERVAL '30 days'),
    (2, 1, 'ECE / VLSI & Embedded Lab', NOW() - INTERVAL '30 days'),
    (3, 1, 'Mechanical / Robotics Lab', NOW() - INTERVAL '30 days'),
    (4, 2, 'Computer Science & Engineering', NOW() - INTERVAL '30 days'),
    (5, 2, 'Physics & Advanced Materials', NOW() - INTERVAL '30 days'),
    (6, 3, 'Electrical Engineering', NOW() - INTERVAL '30 days'),
    (7, 3, 'Biomedical Engineering', NOW() - INTERVAL '30 days');

-- 5. Seed Users (Uniform Password: Demo@1234 -> $2a$10$vj6AvdJ63Irc0UAzBGj9.uMWd1A0NndoaWoDE/SiMSop9wuTM3xXi)
INSERT INTO users (id, email, password_hash, full_name, phone_number, institution_id, department_id, requested_role, membership_status, membership_approved, created_at) VALUES
    (1, 'sysadmin@platform.test', '$2a$10$vj6AvdJ63Irc0UAzBGj9.uMWd1A0NndoaWoDE/SiMSop9wuTM3xXi', 'Dr. Rajesh Sharma', '+91 9849011111', NULL, NULL, 'SYSTEM_ADMIN', 'APPROVED', TRUE, NOW() - INTERVAL '20 days'),
    (2, 'bunty@1234.com', '$2a$10$vj6AvdJ63Irc0UAzBGj9.uMWd1A0NndoaWoDE/SiMSop9wuTM3xXi', 'Bunty (System Admin)', '+91 9849022222', NULL, NULL, 'SYSTEM_ADMIN', 'APPROVED', TRUE, NOW() - INTERVAL '20 days'),
    (3, 'admin@mrec.test', '$2a$10$vj6AvdJ63Irc0UAzBGj9.uMWd1A0NndoaWoDE/SiMSop9wuTM3xXi', 'Dr. A. V. Rao', '+91 9849033333', 1, NULL, 'INSTITUTION_ADMIN', 'APPROVED', TRUE, NOW() - INTERVAL '20 days'),
    (4, 'admin@nitw.test', '$2a$10$vj6AvdJ63Irc0UAzBGj9.uMWd1A0NndoaWoDE/SiMSop9wuTM3xXi', 'Prof. K. V. S. Murthy', '+91 9849044444', 2, NULL, 'INSTITUTION_ADMIN', 'APPROVED', TRUE, NOW() - INTERVAL '20 days'),
    (5, 'admin@iith.test', '$2a$10$vj6AvdJ63Irc0UAzBGj9.uMWd1A0NndoaWoDE/SiMSop9wuTM3xXi', 'Prof. B. S. Murty', '+91 9849055555', 3, NULL, 'INSTITUTION_ADMIN', 'APPROVED', TRUE, NOW() - INTERVAL '20 days'),
    (6, 'depthead.cse@mrec.test', '$2a$10$vj6AvdJ63Irc0UAzBGj9.uMWd1A0NndoaWoDE/SiMSop9wuTM3xXi', 'Dr. M. Srinivas', '+91 9849066666', 1, 1, 'DEPARTMENT_HEAD', 'APPROVED', TRUE, NOW() - INTERVAL '20 days'),
    (7, 'manager@mrec.test', '$2a$10$vj6AvdJ63Irc0UAzBGj9.uMWd1A0NndoaWoDE/SiMSop9wuTM3xXi', 'Dr. Suresh Varma', '+91 9849077777', 1, 2, 'LAB_MANAGER', 'APPROVED', TRUE, NOW() - INTERVAL '20 days'),
    (8, 'technician@mrec.test', '$2a$10$vj6AvdJ63Irc0UAzBGj9.uMWd1A0NndoaWoDE/SiMSop9wuTM3xXi', 'K. Ramesh', '+91 9849088888', 1, 2, 'LAB_TECHNICIAN', 'APPROVED', TRUE, NOW() - INTERVAL '20 days'),
    (9, 'researcher@mrec.test', '$2a$10$vj6AvdJ63Irc0UAzBGj9.uMWd1A0NndoaWoDE/SiMSop9wuTM3xXi', 'Dilip Karthik', '+91 9849099999', 1, 1, 'RESEARCHER_STUDENT', 'APPROVED', TRUE, NOW() - INTERVAL '20 days'),
    (10, 'researcher@nitw.test', '$2a$10$vj6AvdJ63Irc0UAzBGj9.uMWd1A0NndoaWoDE/SiMSop9wuTM3xXi', 'Ananya Roy', '+91 9849010101', 2, 4, 'RESEARCHER_STUDENT', 'APPROVED', TRUE, NOW() - INTERVAL '20 days'),
    (11, 'pending.admin@osmania.test', '$2a$10$vj6AvdJ63Irc0UAzBGj9.uMWd1A0NndoaWoDE/SiMSop9wuTM3xXi', 'Dr. P. Ramana', '+91 9849020202', 1, NULL, 'INSTITUTION_ADMIN', 'PENDING', FALSE, NOW() - INTERVAL '2 days');

-- 6. Link User Roles (Strictly 1 role per user)
INSERT INTO user_roles (user_id, role_id) VALUES
    (1, 6), -- sysadmin -> SYSTEM_ADMIN
    (2, 6), -- bunty -> SYSTEM_ADMIN
    (3, 5), -- admin@mrec -> INSTITUTION_ADMIN
    (4, 5), -- admin@nitw -> INSTITUTION_ADMIN
    (5, 5), -- admin@iith -> INSTITUTION_ADMIN
    (6, 4), -- depthead.cse -> DEPARTMENT_HEAD
    (7, 3), -- manager@mrec -> LAB_MANAGER
    (8, 2), -- technician@mrec -> LAB_TECHNICIAN
    (9, 1), -- researcher@mrec -> RESEARCHER_STUDENT
    (10, 1), -- researcher@nitw -> RESEARCHER_STUDENT
    (11, 1); -- pending.admin -> default role RESEARCHER_STUDENT until approved as INSTITUTION_ADMIN

-- 7. Seed Equipment (9 Instruments across MREC, NITW, and IITH)
INSERT INTO equipment (id, name, asset_tag, category, description, institution_id, department_id, lab_location, quantity, status, shareable, specifications, created_at) VALUES
    (1, 'High-Resolution Confocal Laser Microscope', 'EQ-MREC-MIC-01', 'Microscopy & Imaging', 'Multi-channel confocal microscope for advanced fluorescence and live-cell imaging', 1, 2, 'Advanced Microscopy Center, Block B, Room 204', 1, 'AVAILABLE', TRUE, '{"magnification": "100x-1000x", "laser_lines": ["405nm", "488nm", "561nm", "640nm"], "resolution": "120nm"}'::jsonb, NOW() - INTERVAL '25 days'),
    (2, 'Keysight 4-Channel 1GHz Digital Storage Oscilloscope', 'EQ-MREC-OSC-02', 'Electronics & Test', 'High-speed digital storage oscilloscope with protocol decoding and jitter analysis', 1, 2, 'VLSI & Embedded Systems Lab, Block A, Room 102', 2, 'IN_USE', TRUE, '{"bandwidth": "1 GHz", "sample_rate": "5 GSa/s", "channels": 4}'::jsonb, NOW() - INTERVAL '25 days'),
    (3, 'Formlabs Form 3B+ Medical Grade 3D Bioprinter', 'EQ-MREC-BIO-03', 'Additive Manufacturing', 'Precision stereolithography (SLA) 3D printer for biocompatible resins and microfluidics', 1, 3, 'Robotics & Additive Manufacturing Lab, Block D, Room 10', 1, 'MAINTENANCE', FALSE, '{"technology": "SLA", "laser_spot_size": "85 microns", "build_volume": "145x145x185mm"}'::jsonb, NOW() - INTERVAL '25 days'),
    (4, 'Xilinx UltraScale+ FPGA Development Cluster', 'EQ-MREC-FPG-04', 'Embedded Systems & Computing', 'High-density FPGA development boards with PCIe Gen4 and dual 100GbE network interfaces', 1, 1, 'IoT & High-Performance Computing Lab, Tech Park 3F', 4, 'AVAILABLE', TRUE, '{"fpga_device": "Kintex UltraScale+ XCKU5P", "logic_cells": "475K", "ddr4_memory": "16 GB"}'::jsonb, NOW() - INTERVAL '25 days'),
    (5, 'Haas Mini Mill 5-Axis CNC Machining Unit', 'EQ-MREC-CNC-05', 'Machining & Fabrication', 'Compact high-precision 5-axis CNC vertical machining center with rigid tapping', 1, 3, 'Precision Workshop, Heavy Engg Hall 1', 1, 'AVAILABLE', FALSE, '{"spindle_speed": "10000 RPM", "travel_xyz": "406x305x254mm", "tool_changer": "10-tool carousel"}'::jsonb, NOW() - INTERVAL '25 days'),
    (6, 'Shimadzu UV-2600i Double-Beam UV-Vis Spectrophotometer', 'EQ-NITW-UV-01', 'Spectroscopy', 'Research-grade double-beam spectrophotometer with integrating sphere attachment', 2, 5, 'Materials Characterization Lab, Science Complex Room 108', 1, 'AVAILABLE', TRUE, '{"wavelength_range": "185-900 nm", "spectral_bandwidth": "0.1 to 5 nm", "photometric_accuracy": "+/-0.002 Abs"}'::jsonb, NOW() - INTERVAL '25 days'),
    (7, 'NVIDIA DGX Station A100 AI Supercomputer Workstation', 'EQ-NITW-DGX-02', 'High Performance Computing', 'AI supercomputer workstation for large-scale deep learning models and scientific computing', 2, 4, 'AI & Deep Learning Center, CSE Block 2F', 1, 'IN_USE', TRUE, '{"gpus": "4x NVIDIA A100 80GB", "cuda_cores": "27648", "tensor_cores": "1728", "system_ram": "512 GB"}'::jsonb, NOW() - INTERVAL '25 days'),
    (8, 'FLIR T865 Professional Thermal Imaging Camera', 'EQ-IITH-THM-01', 'Thermal & Sensors', 'High-resolution thermal camera for electronic component thermal mapping and analysis', 3, 6, 'Sensors & Microelectronics Lab, Academic Block C, Room 312', 1, 'AVAILABLE', TRUE, '{"ir_resolution": "640x480 pixels", "thermal_sensitivity": "<30 mK", "temp_range": "-40C to 650C"}'::jsonb, NOW() - INTERVAL '25 days'),
    (9, 'Agilent 1260 Infinity II HPLC System', 'EQ-IITH-HPL-02', 'Chromatography & Analytical', 'Quaternary liquid chromatography system with diode array detector and autosampler', 3, 7, 'Analytical Chemistry Wing, BioTech Park Room 15', 1, 'AVAILABLE', TRUE, '{"flow_rate": "0.001-10 mL/min", "pressure_range": "up to 600 bar", "detector": "Diode Array Detector (DAD)"}'::jsonb, NOW() - INTERVAL '25 days');

-- 8. Equipment Tags
INSERT INTO equipment_tags (equipment_id, tag) VALUES
    (1, 'optical'), (1, 'imaging'), (1, 'biology'), (1, 'microscopy'),
    (2, 'electronics'), (2, 'signal-analysis'), (2, 'embedded'), (2, 'testing'),
    (3, '3d-printing'), (3, 'biomedical'), (3, 'rapid-prototyping'), (3, 'robotics'),
    (4, 'fpga'), (4, 'iot'), (4, 'edge-ai'), (4, 'hardware-acceleration'),
    (5, 'cnc'), (5, 'manufacturing'), (5, 'mechanical'), (5, 'milling'),
    (6, 'spectroscopy'), (6, 'optics'), (6, 'materials-science'), (6, 'chemical'),
    (7, 'ai'), (7, 'deep-learning'), (7, 'gpu'), (7, 'supercomputing'),
    (8, 'thermal'), (8, 'sensors'), (8, 'inspection'), (8, 'diagnostics'),
    (9, 'hplc'), (9, 'chromatography'), (9, 'analytical'), (9, 'biomedical');

-- 9. Operating Hours (Monday through Friday 09:00 - 17:00, Saturday 09:00 - 13:00, Sunday Closed)
DO $$
DECLARE
    eq_id BIGINT;
    d INT;
BEGIN
    FOR eq_id IN 1..9 LOOP
        FOR d IN 1..5 LOOP
            INSERT INTO equipment_operating_hours (equipment_id, institution_id, day_of_week, open_time, close_time, is_closed)
            VALUES (eq_id, NULL, d, '09:00:00', '17:00:00', FALSE);
        END LOOP;
        INSERT INTO equipment_operating_hours (equipment_id, institution_id, day_of_week, open_time, close_time, is_closed)
        VALUES (eq_id, NULL, 6, '09:00:00', '13:00:00', FALSE);
        INSERT INTO equipment_operating_hours (equipment_id, institution_id, day_of_week, open_time, close_time, is_closed)
        VALUES (eq_id, NULL, 7, '09:00:00', '17:00:00', TRUE);
    END LOOP;
END $$;

-- 10. Sharing Listings
INSERT INTO sharing_listings (id, equipment_id, fee_per_hour, external_share_percentage, minimum_notice_hours, maximum_booking_hours, terms, active, created_at, updated_at) VALUES
    (1, 1, 450.00, 40.00, 24, 8, 'External researchers must undergo safety walkthrough before first session. Standard immersion oil and slides provided.', TRUE, NOW() - INTERVAL '10 days', NOW() - INTERVAL '10 days'),
    (2, 2, 150.00, 30.00, 12, 6, 'Standard differential probes provided. High-voltage probes require technician assistance.', TRUE, NOW() - INTERVAL '10 days', NOW() - INTERVAL '10 days'),
    (3, 4, 200.00, 50.00, 6, 12, 'Includes remote Vivado Design Suite floating license access and bitstream download server.', TRUE, NOW() - INTERVAL '10 days', NOW() - INTERVAL '10 days'),
    (4, 6, 300.00, 35.00, 24, 4, 'Includes standard quartz cuvettes (10mm path length). Hazardous solvents require departmental clearance.', TRUE, NOW() - INTERVAL '10 days', NOW() - INTERVAL '10 days'),
    (5, 7, 800.00, 25.00, 48, 24, 'SSH containerized access via Docker/Apptainer. Maximum 4 GPUs per external allocation.', TRUE, NOW() - INTERVAL '10 days', NOW() - INTERVAL '10 days');

-- 11. Inter-Institution Sharing Request, Bilateral Agreement & Access Grant
INSERT INTO sharing_requests (id, equipment_id, requester_id, requester_institution_id, purpose, start_date, end_date, status, requester_reviewed_by, requester_reviewed_at, requester_review_remarks, reviewed_by, reviewed_at, review_remarks, created_at) VALUES
    (1, 1, 10, 2, 'Bilateral nanophotonics cellular fluorescence study in collaboration with MREC ECE department', NOW() + INTERVAL '1 day', NOW() + INTERVAL '3 days', 'APPROVED', 4, NOW() - INTERVAL '2 days', 'Approved for joint photonics research initiative.', 3, NOW() - INTERVAL '1 day', 'Approved with standard technician orientation.', NOW() - INTERVAL '3 days');

INSERT INTO sharing_agreements (id, equipment_id, owner_institution_id, partner_institution_id, request_id, start_date, end_date, allowed_start_time, allowed_end_time, fee_per_hour, status, approved_by, approved_at) VALUES
    (1, 1, 1, 2, 1, NOW() - INTERVAL '1 day', NOW() + INTERVAL '30 days', '09:00:00', '17:00:00', 450.00, 'ACTIVE', 3, NOW() - INTERVAL '1 day');

INSERT INTO sharing_access_grants (id, agreement_id, user_id, status) VALUES
    (1, 1, 10, 'ACTIVE');

-- 12. Bookings
INSERT INTO bookings (id, equipment_id, user_id, start_time, end_time, purpose, status, sharing_agreement_id, created_at) VALUES
    (1, 2, 9, NOW() - INTERVAL '1 day' + TIME '10:00:00' - TIME '00:00:00', NOW() - INTERVAL '1 day' + TIME '13:00:00' - TIME '00:00:00', 'Automated jitter and eye-diagram signal characterization', 'COMPLETED', NULL, NOW() - INTERVAL '3 days'),
    (2, 2, 9, CURRENT_DATE + TIME '09:00:00', CURRENT_DATE + TIME '17:00:00', 'FPGA serial link transceivers signal integrity validation', 'APPROVED', NULL, NOW() - INTERVAL '1 day'),
    (3, 1, 9, CURRENT_DATE + INTERVAL '1 day' + TIME '10:00:00', CURRENT_DATE + INTERVAL '1 day' + TIME '12:00:00', 'Live-cell fluorescence confocal imaging for drug-delivery assay', 'APPROVED', NULL, NOW() - INTERVAL '1 day'),
    (4, 4, 9, CURRENT_DATE + INTERVAL '2 days' + TIME '14:00:00', CURRENT_DATE + INTERVAL '2 days' + TIME '17:00:00', 'Edge-AI YOLOv8 inference accelerator quantization and testing', 'PENDING', NULL, NOW()),
    (5, 1, 10, CURRENT_DATE + INTERVAL '3 days' + TIME '09:00:00', CURRENT_DATE + INTERVAL '3 days' + TIME '13:00:00', 'Cellular fluorescence cross-institution study under bilateral agreement', 'APPROVED', 1, NOW() - INTERVAL '1 day');

-- 13. Booking Audit Logs
INSERT INTO booking_audit_logs (booking_id, event_type, previous_status, new_status, actor_email, occurred_at) VALUES
    (1, 'CREATED', NULL, 'PENDING', 'researcher@mrec.test', NOW() - INTERVAL '3 days'),
    (1, 'STATUS_CHANGE', 'PENDING', 'APPROVED', 'manager@mrec.test', NOW() - INTERVAL '2 days'),
    (1, 'STATUS_CHANGE', 'APPROVED', 'COMPLETED', 'researcher@mrec.test', NOW() - INTERVAL '1 day' + TIME '13:00:00' - TIME '00:00:00'),
    (2, 'CREATED', NULL, 'PENDING', 'researcher@mrec.test', NOW() - INTERVAL '1 day'),
    (2, 'STATUS_CHANGE', 'PENDING', 'APPROVED', 'manager@mrec.test', NOW() - INTERVAL '12 hours'),
    (3, 'CREATED', NULL, 'PENDING', 'researcher@mrec.test', NOW() - INTERVAL '1 day'),
    (3, 'STATUS_CHANGE', 'PENDING', 'APPROVED', 'manager@mrec.test', NOW() - INTERVAL '8 hours'),
    (4, 'CREATED', NULL, 'PENDING', 'researcher@mrec.test', NOW());

-- 14. Live and Historical Utilization Records
INSERT INTO utilization_records (id, equipment_id, user_id, booking_id, usage_type, start_time, end_time, actual_hours, notes, created_at) VALUES
    (1, 2, 9, 1, 'BOOKING', NOW() - INTERVAL '1 day' + TIME '10:00:00' - TIME '00:00:00', NOW() - INTERVAL '1 day' + TIME '13:00:00' - TIME '00:00:00', 3.0, 'Automated jitter and eye-diagram testing completed successfully', NOW() - INTERVAL '1 day' + TIME '13:00:00' - TIME '00:00:00'),
    (2, 2, 9, 2, 'BOOKING', NOW() - INTERVAL '45 minutes', NULL, NULL, 'Live bench testing in progress', NOW() - INTERVAL '45 minutes');

-- 15. Equipment Waitlist
INSERT INTO equipment_waitlist (equipment_id, user_id, requested_start_time, requested_end_time, priority, status, notes, created_at) VALUES
    (1, 9, CURRENT_DATE + INTERVAL '1 day' + TIME '10:00:00', CURRENT_DATE + INTERVAL '1 day' + TIME '12:00:00', 1, 'WAITING', 'Alternative slot requested in case of cancellation', NOW() - INTERVAL '5 hours');

-- 16. Milestone 3 Maintenance Work Orders
INSERT INTO maintenance_records (id, equipment_id, reported_by, assigned_to, description, status, priority, maintenance_type, reported_at, started_at, completed_at, scheduled_date, downtime_hours, cost, resolution_notes) VALUES
    (1, 3, 7, 8, 'Resin tank wiper axis mechanical binding during high-viscosity layer recoat', 'IN_PROGRESS', 'HIGH', 'CORRECTIVE', NOW() - INTERVAL '2 days', NOW() - INTERVAL '1 day', NULL, NOW() - INTERVAL '1 day', 14.5, NULL, 'Tension belts inspected; stepper motor lead screw realignment in progress.'),
    (2, 2, 9, 8, 'Channel 3 BNC input connector loose and displaying intermittent ground drift', 'COMPLETED', 'MEDIUM', 'CORRECTIVE', NOW() - INTERVAL '5 days', NOW() - INTERVAL '4 days' + TIME '10:00:00' - TIME '00:00:00', NOW() - INTERVAL '4 days' + TIME '15:30:00' - TIME '00:00:00', NULL, 5.5, 1200.00, 'Replaced BNC panel connector assembly; recalibrated input sensitivity and verified on 10MHz reference signal.'),
    (3, 5, 8, NULL, 'Quarterly preventative spindle vibration analysis and hydrostatic seal lubrication', 'REPORTED', 'LOW', 'PREVENTATIVE', NOW() - INTERVAL '1 day', NULL, NULL, NOW() + INTERVAL '3 days', NULL, 3500.00, NULL);

-- 17. Milestone 3 Calibration Records
INSERT INTO calibration_records (id, equipment_id, calibration_date, next_due_date, scheduled_date, result, status, assigned_to, certificate_number, notes, created_at) VALUES
    (1, 2, CURRENT_DATE - INTERVAL '1 month', CURRENT_DATE + INTERVAL '11 months', NULL, 'PASSED', 'COMPLETED', 8, 'CAL-KEY-2026-0891', 'Full NIST-traceable calibration performed. Timing jitter and vertical accuracy within +/-0.5% tolerance.', NOW() - INTERVAL '1 month'),
    (2, 1, NULL, CURRENT_DATE + INTERVAL '5 days', CURRENT_DATE + INTERVAL '2 days', NULL, 'SCHEDULED', 8, NULL, 'Annual optical alignment and fluorescence intensity standard bead calibration with Chroma filter check.', NOW() - INTERVAL '2 days'),
    (3, 6, CURRENT_DATE - INTERVAL '11 months', CURRENT_DATE + INTERVAL '7 days', NULL, 'PASSED', 'COMPLETED', NULL, 'CAL-SHIM-2025-4412', 'Deuterium and Tungsten lamp baseline wavelength alignment passed. Due for annual recertification.', NOW() - INTERVAL '11 months');

-- 18. Daily Utilization Snapshots (Past 7 Days for Analytics & Heatmap Visualization)
DO $$
DECLARE
    i INT;
    snap_d DATE;
BEGIN
    FOR i IN 1..7 LOOP
        snap_d := CURRENT_DATE - (i || ' days')::INTERVAL;
        INSERT INTO daily_utilization_snapshots (snapshot_date, equipment_id, institution_id, department_id, operating_minutes, used_minutes, utilization_rate, session_count, created_at)
        VALUES
            (snap_d, 1, 1, 2, 480, 240 + (i * 15) % 180, ROUND(((240 + (i * 15) % 180)::NUMERIC / 480.0) * 100, 2), 2, NOW()),
            (snap_d, 2, 1, 2, 480, 300 + (i * 20) % 120, ROUND(((300 + (i * 20) % 120)::NUMERIC / 480.0) * 100, 2), 3, NOW()),
            (snap_d, 4, 1, 1, 480, 180 + (i * 30) % 200, ROUND(((180 + (i * 30) % 200)::NUMERIC / 480.0) * 100, 2), 2, NOW()),
            (snap_d, 6, 2, 5, 480, 210 + (i * 25) % 150, ROUND(((210 + (i * 25) % 150)::NUMERIC / 480.0) * 100, 2), 1, NOW()),
            (snap_d, 7, 2, 4, 480, 360 + (i * 10) % 80, ROUND(((360 + (i * 10) % 80)::NUMERIC / 480.0) * 100, 2), 2, NOW())
        ON CONFLICT (snapshot_date, equipment_id) DO NOTHING;
    END LOOP;
END $$;

-- 19. Sync Auto-increment Sequences with Max IDs
SELECT setval(pg_get_serial_sequence('institutions', 'id'), COALESCE(MAX(id), 1)) FROM institutions;
SELECT setval(pg_get_serial_sequence('departments', 'id'), COALESCE(MAX(id), 1)) FROM departments;
SELECT setval(pg_get_serial_sequence('users', 'id'), COALESCE(MAX(id), 1)) FROM users;
SELECT setval(pg_get_serial_sequence('equipment', 'id'), COALESCE(MAX(id), 1)) FROM equipment;
SELECT setval(pg_get_serial_sequence('bookings', 'id'), COALESCE(MAX(id), 1)) FROM bookings;
SELECT setval(pg_get_serial_sequence('utilization_records', 'id'), COALESCE(MAX(id), 1)) FROM utilization_records;
SELECT setval(pg_get_serial_sequence('maintenance_records', 'id'), COALESCE(MAX(id), 1)) FROM maintenance_records;
SELECT setval(pg_get_serial_sequence('calibration_records', 'id'), COALESCE(MAX(id), 1)) FROM calibration_records;
SELECT setval(pg_get_serial_sequence('sharing_requests', 'id'), COALESCE(MAX(id), 1)) FROM sharing_requests;
SELECT setval(pg_get_serial_sequence('sharing_agreements', 'id'), COALESCE(MAX(id), 1)) FROM sharing_agreements;
SELECT setval(pg_get_serial_sequence('sharing_access_grants', 'id'), COALESCE(MAX(id), 1)) FROM sharing_access_grants;
SELECT setval(pg_get_serial_sequence('sharing_listings', 'id'), COALESCE(MAX(id), 1)) FROM sharing_listings;
SELECT setval(pg_get_serial_sequence('equipment_waitlist', 'id'), COALESCE(MAX(id), 1)) FROM equipment_waitlist;
SELECT setval(pg_get_serial_sequence('equipment_operating_hours', 'id'), COALESCE(MAX(id), 1)) FROM equipment_operating_hours;
SELECT setval(pg_get_serial_sequence('daily_utilization_snapshots', 'id'), COALESCE(MAX(id), 1)) FROM daily_utilization_snapshots;
