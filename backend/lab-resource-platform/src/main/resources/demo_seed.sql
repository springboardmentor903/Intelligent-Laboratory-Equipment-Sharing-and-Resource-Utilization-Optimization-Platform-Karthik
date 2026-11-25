-- =============================================================================
-- Demo Database Seed Script for Lab Resource Platform
-- Clean, realistic presentation dataset for all 6 roles across 3 institutions
-- Milestone 1, 2, and 3 Comprehensive Schema and Seed Data
-- =============================================================================

-- Milestone 3 schema alignment for calibration nullable fields
ALTER TABLE calibration_records ALTER COLUMN calibration_date DROP NOT NULL;
ALTER TABLE calibration_records ALTER COLUMN result DROP NOT NULL;

-- 1. Safely truncate all tables and restart identities
TRUNCATE TABLE 
    reminder_deduplication_logs,
    push_device_tokens,
    user_notification_preferences,
    notification_deliveries,
    notifications,
    invoice_items,
    invoices,
    usage_charges,
    department_budgets,
    stored_documents,
    equipment_downtime_logs,
    work_order_service_logs,
    work_orders,
    preventive_maintenance_schedules,
    maintenance_requests,
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
    (11, 'pending.admin@osmania.test', '$2a$10$vj6AvdJ63Irc0UAzBGj9.uMWd1A0NndoaWoDE/SiMSop9wuTM3xXi', 'Dr. P. Ramana', NULL, 1, NULL, 'INSTITUTION_ADMIN', 'APPROVED', TRUE, NOW() - INTERVAL '2 days');

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

-- 7. Seed Equipment (9 Instruments with realistic rates, purchase dates, and purchase costs)
INSERT INTO equipment (id, name, asset_tag, category, description, institution_id, department_id, lab_location, quantity, status, shareable, hourly_rate, purchase_date, purchase_cost, specifications, created_at) VALUES
    (1, 'High-Resolution Confocal Laser Microscope', 'EQ-MREC-MIC-01', 'Microscopy & Imaging', 'Multi-channel confocal microscope for advanced fluorescence and live-cell imaging', 1, 2, 'Advanced Microscopy Center, Block B, Room 204', 1, 'AVAILABLE', TRUE, 450.00, CURRENT_DATE - INTERVAL '520 days', 1800000.00, '{"magnification": "100x-1000x", "laser_lines": ["405nm", "488nm", "561nm", "640nm"], "resolution": "120nm"}'::jsonb, NOW() - INTERVAL '25 days'),
    (2, 'Keysight 4-Channel 1GHz Digital Storage Oscilloscope', 'EQ-MREC-OSC-02', 'Electronics & Test', 'High-speed digital storage oscilloscope with protocol decoding and jitter analysis', 1, 2, 'VLSI & Embedded Systems Lab, Block A, Room 102', 2, 'IN_USE', TRUE, 150.00, CURRENT_DATE - INTERVAL '400 days', 450000.00, '{"bandwidth": "1 GHz", "sample_rate": "5 GSa/s", "channels": 4}'::jsonb, NOW() - INTERVAL '25 days'),
    (3, 'Formlabs Form 3B+ Medical Grade 3D Bioprinter', 'EQ-MREC-BIO-03', 'Additive Manufacturing', 'Precision stereolithography (SLA) 3D printer for biocompatible resins and microfluidics', 1, 3, 'Robotics & Additive Manufacturing Lab, Block D, Room 10', 1, 'MAINTENANCE', FALSE, 250.00, CURRENT_DATE - INTERVAL '300 days', 250000.00, '{"technology": "SLA", "laser_spot_size": "85 microns", "build_volume": "145x145x185mm"}'::jsonb, NOW() - INTERVAL '25 days'),
    (4, 'Xilinx UltraScale+ FPGA Development Cluster', 'EQ-MREC-FPG-04', 'Embedded Systems & Computing', 'High-density FPGA development boards with PCIe Gen4 and dual 100GbE network interfaces', 1, 1, 'IoT & High-Performance Computing Lab, Tech Park 3F', 4, 'AVAILABLE', TRUE, 200.00, CURRENT_DATE - INTERVAL '600 days', 350000.00, '{"fpga_device": "Kintex UltraScale+ XCKU5P", "logic_cells": "475K", "ddr4_memory": "16 GB"}'::jsonb, NOW() - INTERVAL '25 days'),
    (5, 'Haas Mini Mill 5-Axis CNC Machining Unit', 'EQ-MREC-CNC-05', 'Machining & Fabrication', 'Compact high-precision 5-axis CNC vertical machining center with rigid tapping', 1, 3, 'Precision Workshop, Heavy Engg Hall 1', 1, 'AVAILABLE', FALSE, 350.00, CURRENT_DATE - INTERVAL '750 days', 2200000.00, '{"spindle_speed": "10000 RPM", "travel_xyz": "406x305x254mm", "tool_changer": "10-tool carousel"}'::jsonb, NOW() - INTERVAL '25 days'),
    (6, 'Shimadzu UV-2600i Double-Beam UV-Vis Spectrophotometer', 'EQ-NITW-UV-01', 'Spectroscopy', 'Research-grade double-beam spectrophotometer with integrating sphere attachment', 2, 5, 'Materials Characterization Lab, Science Complex Room 108', 1, 'AVAILABLE', TRUE, 300.00, CURRENT_DATE - INTERVAL '450 days', 850000.00, '{"wavelength_range": "185-900 nm", "spectral_bandwidth": "0.1 to 5 nm", "photometric_accuracy": "+/-0.002 Abs"}'::jsonb, NOW() - INTERVAL '25 days'),
    (7, 'NVIDIA DGX Station A100 AI Supercomputer Workstation', 'EQ-NITW-DGX-02', 'High Performance Computing', 'AI supercomputer workstation for large-scale deep learning models and scientific computing', 2, 4, 'AI & Deep Learning Center, CSE Block 2F', 1, 'IN_USE', TRUE, 800.00, CURRENT_DATE - INTERVAL '350 days', 2500000.00, '{"gpus": "4x NVIDIA A100 80GB", "cuda_cores": "27648", "tensor_cores": "1728", "system_ram": "512 GB"}'::jsonb, NOW() - INTERVAL '25 days'),
    (8, 'FLIR T865 Professional Thermal Imaging Camera', 'EQ-IITH-THM-01', 'Thermal & Sensors', 'High-resolution thermal camera for electronic component thermal mapping and analysis', 3, 6, 'Sensors & Microelectronics Lab, Academic Block C, Room 312', 1, 'AVAILABLE', TRUE, 400.00, CURRENT_DATE - INTERVAL '500 days', 550000.00, '{"ir_resolution": "640x480 pixels", "thermal_sensitivity": "<30 mK", "temp_range": "-40C to 650C"}'::jsonb, NOW() - INTERVAL '25 days'),
    (9, 'Agilent 1260 Infinity II HPLC System', 'EQ-IITH-HPL-02', 'Chromatography & Analytical', 'Quaternary liquid chromatography system with diode array detector and autosampler', 3, 7, 'Analytical Chemistry Wing, BioTech Park Room 15', 1, 'AVAILABLE', TRUE, 500.00, CURRENT_DATE - INTERVAL '620 days', 1400000.00, '{"flow_rate": "0.001-10 mL/min", "pressure_range": "up to 600 bar", "detector": "Diode Array Detector (DAD)"}'::jsonb, NOW() - INTERVAL '25 days');

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

-- 12. Bookings (Historical and Future across Internal and External)
INSERT INTO bookings (id, equipment_id, user_id, start_time, end_time, purpose, status, sharing_agreement_id, created_at) VALUES
    (1, 2, 9, NOW() - INTERVAL '10 days' + TIME '10:00:00' - TIME '00:00:00', NOW() - INTERVAL '10 days' + TIME '13:00:00' - TIME '00:00:00', 'Automated jitter and eye-diagram signal characterization', 'COMPLETED', NULL, NOW() - INTERVAL '12 days'),
    (2, 1, 9, NOW() - INTERVAL '8 days' + TIME '09:00:00' - TIME '00:00:00', NOW() - INTERVAL '8 days' + TIME '15:00:00' - TIME '00:00:00', 'Live-cell fluorescence confocal imaging for drug-delivery assay', 'COMPLETED', NULL, NOW() - INTERVAL '10 days'),
    (3, 2, 7, NOW() - INTERVAL '6 days' + TIME '09:00:00' - TIME '00:00:00', NOW() - INTERVAL '6 days' + TIME '17:00:00' - TIME '00:00:00', 'Multi-channel serial bus decode bench run', 'COMPLETED', NULL, NOW() - INTERVAL '8 days'),
    (4, 1, 7, NOW() - INTERVAL '4 days' + TIME '09:00:00' - TIME '00:00:00', NOW() - INTERVAL '4 days' + TIME '17:00:00' - TIME '00:00:00', 'High-throughput fluorescent sample screening', 'COMPLETED', NULL, NOW() - INTERVAL '6 days'),
    (5, 1, 7, NOW() - INTERVAL '2 days' + TIME '09:00:00' - TIME '00:00:00', NOW() - INTERVAL '2 days' + TIME '17:00:00' - TIME '00:00:00', 'Fluorescence correlation spectroscopy runs', 'COMPLETED', NULL, NOW() - INTERVAL '4 days'),
    (6, 3, 8, NOW() - INTERVAL '15 days' + TIME '09:00:00' - TIME '00:00:00', NOW() - INTERVAL '15 days' + TIME '17:00:00' - TIME '00:00:00', 'SLA surgical guide dimensional test print', 'COMPLETED', NULL, NOW() - INTERVAL '16 days'),
    (7, 3, 8, NOW() - INTERVAL '12 days' + TIME '09:00:00' - TIME '00:00:00', NOW() - INTERVAL '12 days' + TIME '17:00:00' - TIME '00:00:00', 'Biocompatible microfluidics prototype fabrication', 'COMPLETED', NULL, NOW() - INTERVAL '14 days'),
    (8, 3, 8, NOW() - INTERVAL '9 days' + TIME '09:00:00' - TIME '00:00:00', NOW() - INTERVAL '9 days' + TIME '17:00:00' - TIME '00:00:00', 'Orthodontic aligner model batch printing', 'COMPLETED', NULL, NOW() - INTERVAL '11 days'),
    (9, 1, 10, NOW() - INTERVAL '5 days' + TIME '09:00:00' - TIME '00:00:00', NOW() - INTERVAL '5 days' + TIME '17:00:00' - TIME '00:00:00', 'External partner cellular fluorescence study phase 1', 'COMPLETED', 1, NOW() - INTERVAL '7 days'),
    (10, 1, 10, NOW() - INTERVAL '3 days' + TIME '09:00:00' - TIME '00:00:00', NOW() - INTERVAL '3 days' + TIME '17:00:00' - TIME '00:00:00', 'External partner cellular fluorescence study phase 2', 'COMPLETED', 1, NOW() - INTERVAL '5 days'),
    (11, 2, 9, CURRENT_DATE + TIME '09:00:00', CURRENT_DATE + TIME '17:00:00', 'FPGA serial link transceivers signal integrity validation', 'APPROVED', NULL, NOW() - INTERVAL '1 day'),
    (12, 1, 9, CURRENT_DATE + INTERVAL '1 day' + TIME '10:00:00', CURRENT_DATE + INTERVAL '1 day' + TIME '12:00:00', 'High-res confocal microscopy follow-up assay', 'APPROVED', NULL, NOW() - INTERVAL '1 day'),
    (13, 4, 9, CURRENT_DATE + INTERVAL '2 days' + TIME '14:00:00', CURRENT_DATE + INTERVAL '2 days' + TIME '17:00:00', 'Edge-AI YOLOv8 inference accelerator quantization and testing', 'PENDING', NULL, NOW());

-- 13. Booking Audit Logs
INSERT INTO booking_audit_logs (booking_id, event_type, previous_status, new_status, actor_email, occurred_at) VALUES
    (1, 'CREATED', NULL, 'PENDING', 'researcher@mrec.test', NOW() - INTERVAL '12 days'),
    (1, 'STATUS_CHANGE', 'PENDING', 'APPROVED', 'manager@mrec.test', NOW() - INTERVAL '11 days'),
    (1, 'STATUS_CHANGE', 'APPROVED', 'COMPLETED', 'researcher@mrec.test', NOW() - INTERVAL '10 days' + TIME '13:00:00' - TIME '00:00:00'),
    (2, 'CREATED', NULL, 'PENDING', 'researcher@mrec.test', NOW() - INTERVAL '10 days'),
    (2, 'STATUS_CHANGE', 'PENDING', 'APPROVED', 'manager@mrec.test', NOW() - INTERVAL '9 days'),
    (2, 'STATUS_CHANGE', 'APPROVED', 'COMPLETED', 'researcher@mrec.test', NOW() - INTERVAL '8 days' + TIME '15:00:00' - TIME '00:00:00'),
    (11, 'CREATED', NULL, 'PENDING', 'researcher@mrec.test', NOW() - INTERVAL '1 day'),
    (11, 'STATUS_CHANGE', 'PENDING', 'APPROVED', 'manager@mrec.test', NOW() - INTERVAL '12 hours'),
    (12, 'CREATED', NULL, 'PENDING', 'researcher@mrec.test', NOW() - INTERVAL '1 day'),
    (12, 'STATUS_CHANGE', 'PENDING', 'APPROVED', 'manager@mrec.test', NOW() - INTERVAL '8 hours'),
    (13, 'CREATED', NULL, 'PENDING', 'researcher@mrec.test', NOW());

-- 14. Live and Historical Utilization Records
INSERT INTO utilization_records (id, equipment_id, user_id, booking_id, usage_type, start_time, end_time, actual_hours, notes, created_at) VALUES
    (1, 2, 9, 1, 'BOOKING', NOW() - INTERVAL '10 days' + TIME '10:00:00' - TIME '00:00:00', NOW() - INTERVAL '10 days' + TIME '13:00:00' - TIME '00:00:00', 3.0, 'Automated jitter and eye-diagram testing completed successfully', NOW() - INTERVAL '10 days' + TIME '13:00:00' - TIME '00:00:00'),
    (2, 1, 9, 2, 'BOOKING', NOW() - INTERVAL '8 days' + TIME '09:00:00' - TIME '00:00:00', NOW() - INTERVAL '8 days' + TIME '15:00:00' - TIME '00:00:00', 6.0, 'Confocal microscopy imaging run completed', NOW() - INTERVAL '8 days' + TIME '15:00:00' - TIME '00:00:00'),
    (3, 2, 9, 11, 'BOOKING', NOW() - INTERVAL '45 minutes', NULL, NULL, 'Live bench testing in progress', NOW() - INTERVAL '45 minutes');

-- 15. Equipment Waitlist (3 entries on Eq 1 to trigger HIGH_DEMAND recommendation)
INSERT INTO equipment_waitlist (id, equipment_id, user_id, requested_start_time, requested_end_time, priority, status, notes, created_at) VALUES
    (1, 1, 9, CURRENT_DATE + INTERVAL '1 day' + TIME '10:00:00', CURRENT_DATE + INTERVAL '1 day' + TIME '12:00:00', 1, 'WAITING', 'Alternative slot requested in case of cancellation', NOW() - INTERVAL '5 hours'),
    (2, 1, 10, CURRENT_DATE + INTERVAL '2 days' + TIME '14:00:00', CURRENT_DATE + INTERVAL '2 days' + TIME '16:00:00', 2, 'WAITING', 'Backup slot for cross-institution fluorescence validation', NOW() - INTERVAL '4 hours'),
    (3, 1, 11, CURRENT_DATE + INTERVAL '3 days' + TIME '11:00:00', CURRENT_DATE + INTERVAL '3 days' + TIME '13:00:00', 3, 'WAITING', 'Laser scanning priority queue', NOW() - INTERVAL '2 hours');

-- 16. Legacy Maintenance Records (Historical)
INSERT INTO maintenance_records (id, equipment_id, reported_by, assigned_to, description, status, priority, maintenance_type, reported_at, started_at, completed_at, scheduled_date, downtime_hours, cost, resolution_notes) VALUES
    (1, 3, 7, 8, 'Resin tank wiper axis mechanical binding during high-viscosity layer recoat', 'IN_PROGRESS', 'HIGH', 'CORRECTIVE', NOW() - INTERVAL '2 days', NOW() - INTERVAL '1 day', NULL, NOW() - INTERVAL '1 day', 14.5, NULL, 'Tension belts inspected; stepper motor lead screw realignment in progress.'),
    (2, 2, 9, 8, 'Channel 3 BNC input connector loose and displaying intermittent ground drift', 'COMPLETED', 'MEDIUM', 'CORRECTIVE', NOW() - INTERVAL '5 days', NOW() - INTERVAL '4 days' + TIME '10:00:00' - TIME '00:00:00', NOW() - INTERVAL '4 days' + TIME '15:30:00' - TIME '00:00:00', NULL, 5.5, 1200.00, 'Replaced BNC panel connector assembly; recalibrated input sensitivity and verified on 10MHz reference signal.'),
    (3, 5, 8, NULL, 'Quarterly preventative spindle vibration analysis and hydrostatic seal lubrication', 'REPORTED', 'LOW', 'PREVENTATIVE', NOW() - INTERVAL '1 day', NULL, NULL, NOW() + INTERVAL '3 days', NULL, 3500.00, NULL);

-- =============================================================================
-- MILESTONE 3: MAINTENANCE, CALIBRATION, WORK ORDERS & DOWNTIME
-- =============================================================================

-- 17. Maintenance Requests
INSERT INTO maintenance_requests (id, equipment_id, requester_id, institution_id, title, description, priority, status, created_at, reviewed_by, reviewed_at, review_remarks, work_order_id) VALUES
    (1, 3, 7, 1, 'Formlabs 3D Bioprinter Recoater Arm Sticking', 'Resin tank wiper axis binds intermittently during high-viscosity layer recoating cycles', 'HIGH', 'APPROVED', NOW() - INTERVAL '2 days', 7, NOW() - INTERVAL '1 day', 'Approved for emergency corrective maintenance and wiper realignment.', 1),
    (2, 2, 9, 1, 'Keysight DSO Channel 3 High-Frequency Attenuation', 'Channel 3 input shows 3dB attenuation drop-off beyond 500MHz with intermittent ground drift', 'MEDIUM', 'PENDING', NOW() - INTERVAL '1 day', NULL, NULL, NULL, NULL),
    (3, 1, 9, 1, 'Laser Power Fluctuation in Confocal Microscope 488nm Line', 'Argon-laser 488nm line exhibits 8% output power fluctuation during time-lapse imaging', 'CRITICAL', 'PENDING', NOW() - INTERVAL '4 hours', NULL, NULL, NULL, NULL);

-- 18. Preventive Maintenance Schedules (OK, DUE_SOON, OVERDUE)
INSERT INTO preventive_maintenance_schedules (id, equipment_id, title, description, repeat_period_days, last_completed_date, next_due_date, active, created_at, updated_at) VALUES
    (1, 1, 'Quarterly Laser Alignment and Objective Lens Cleaning', 'Precision alignment of 4 laser lines and ultrasonic cleaning of immersion objectives', 90, CURRENT_DATE - INTERVAL '30 days', CURRENT_DATE + INTERVAL '60 days', TRUE, NOW() - INTERVAL '90 days', NOW() - INTERVAL '30 days'),
    (2, 1, 'Biweekly Optical Power Calibration & Filter Inspection', 'Measurement of laser power at specimen focal plane and Chroma emission filter integrity', 14, CURRENT_DATE - INTERVAL '11 days', CURRENT_DATE + INTERVAL '3 days', TRUE, NOW() - INTERVAL '30 days', NOW() - INTERVAL '11 days'),
    (3, 5, 'Monthly Spindle Hydrostatic Pressure Check & Filter Replacement', 'Check hydraulic circulation pressure, drain separator sump, and replace cartridge filter', 30, CURRENT_DATE - INTERVAL '35 days', CURRENT_DATE - INTERVAL '5 days', TRUE, NOW() - INTERVAL '60 days', NOW() - INTERVAL '35 days');

-- 19. Work Orders (WO-YYYY-XXXXXX format across OPEN, IN_PROGRESS, COMPLETED)
INSERT INTO work_orders (id, work_order_number, equipment_id, schedule_id, request_id, title, description, work_order_type, priority, status, assigned_to, assigned_at, scheduled_date, started_at, expected_end_time, completed_at, cancellation_reason, resolution_notes, cost, downtime_hours, created_by, created_at, updated_at) VALUES
    (1, 'WO-2026-000001', 3, NULL, 1, 'Corrective wiper arm and stepper motor realignment', 'Resin tank wiper axis mechanical binding during high-viscosity layer recoat', 'CORRECTIVE', 'HIGH', 'IN_PROGRESS', 8, NOW() - INTERVAL '1 day', NOW() - INTERVAL '1 day', NOW() - INTERVAL '18 hours', NOW() + INTERVAL '6 hours', NULL, NULL, NULL, NULL, 18.0, 7, NOW() - INTERVAL '1 day', NOW() - INTERVAL '18 hours'),
    (2, 'WO-2026-000002', 2, NULL, NULL, 'Channel 3 BNC input connector replacement & calibration', 'Channel 3 BNC input connector loose and displaying intermittent ground drift', 'CORRECTIVE', 'MEDIUM', 'COMPLETED', 8, NOW() - INTERVAL '5 days', NOW() - INTERVAL '5 days', NOW() - INTERVAL '5 days', NOW() - INTERVAL '4 days', NOW() - INTERVAL '4 days', NULL, 'Replaced BNC connector assembly; recalibrated input sensitivity and verified on 10MHz reference signal.', 3500.00, 6.0, 7, NOW() - INTERVAL '5 days', NOW() - INTERVAL '4 days'),
    (3, 'WO-2026-000003', 3, NULL, NULL, 'High-precision UV optical laser engine recertification & mirror replacement', 'Comprehensive laser module maintenance and replacement of internal galvo mirrors', 'CORRECTIVE', 'CRITICAL', 'COMPLETED', 8, NOW() - INTERVAL '60 days', NOW() - INTERVAL '60 days', NOW() - INTERVAL '60 days', NOW() - INTERVAL '58 days', NOW() - INTERVAL '58 days', NULL, 'Galvo mirror block replaced and calibrated with optical power sensor. Passed burn-in test.', 95000.00, 48.0, 7, NOW() - INTERVAL '60 days', NOW() - INTERVAL '58 days'),
    (4, 'WO-2026-000004', 5, 3, NULL, 'Emergency spindle bearing diagnostic & hydraulic overhaul', 'Investigate spindle bearing noise and replace hydraulic circulation filter', 'PREVENTIVE', 'HIGH', 'OPEN', 8, NOW() - INTERVAL '2 days', NOW() + INTERVAL '1 day', NULL, NOW() + INTERVAL '1 day' + TIME '17:00:00', NULL, NULL, NULL, NULL, NULL, 7, NOW() - INTERVAL '2 days', NOW() - INTERVAL '2 days'),
    (5, 'WO-2026-000005', 1, 2, NULL, 'Optical power calibration & filter inspection', 'Biweekly preventive optical power calibration and 4-channel emission filter checks', 'PREVENTIVE', 'MEDIUM', 'OPEN', NULL, NULL, NOW() + INTERVAL '3 days', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 7, NOW() - INTERVAL '1 day', NOW() - INTERVAL '1 day'),
    (6, 'WO-2026-000006', 2, NULL, NULL, 'Annual NIST Traceable Calibration & Timing Jitter Verification', 'Comprehensive annual calibration for Keysight 1GHz DSO with certificate issuance', 'CALIBRATION', 'HIGH', 'COMPLETED', 8, NOW() - INTERVAL '30 days', NOW() - INTERVAL '30 days', NOW() - INTERVAL '30 days', NOW() - INTERVAL '29 days', NOW() - INTERVAL '29 days', NULL, 'Completed NIST-traceable calibration. Issued CAL-KEY-2026-0891 certificate.', 4800.00, 8.0, 7, NOW() - INTERVAL '30 days', NOW() - INTERVAL '29 days');

-- 20. Work Order Service Logs
INSERT INTO work_order_service_logs (id, work_order_id, user_id, log_type, from_status, to_status, notes, created_at) VALUES
    (1, 1, 7, 'ASSIGNMENT', 'OPEN', 'ASSIGNED', 'Assigned work order to Lead Technician K. Ramesh for immediate mechanical diagnosis.', NOW() - INTERVAL '1 day'),
    (2, 1, 8, 'STATUS_CHANGE', 'ASSIGNED', 'IN_PROGRESS', 'Disassembled wiper rail housing; discovered polymerized photopolymer resin residue on linear guides. Commencing solvent purge and lead-screw realignment.', NOW() - INTERVAL '18 hours'),
    (3, 2, 8, 'STATUS_CHANGE', 'OPEN', 'IN_PROGRESS', 'Desoldered damaged Amphenol BNC receptacle from main RF acquisition board.', NOW() - INTERVAL '5 days'),
    (4, 2, 8, 'STATUS_CHANGE', 'IN_PROGRESS', 'COMPLETED', 'Installed new precision 50-ohm BNC receptacle, calibrated input amplifier offset, and verified 10MHz reference clock jitter < 15ps.', NOW() - INTERVAL '4 days'),
    (5, 3, 8, 'STATUS_CHANGE', 'IN_PROGRESS', 'COMPLETED', 'Galvo mirror block aligned with He-Ne alignment laser. Optical spot profile meets OEM 85-micron circularity specification.', NOW() - INTERVAL '58 days');

-- 21. Equipment Downtime Logs
INSERT INTO equipment_downtime_logs (id, equipment_id, work_order_id, start_time, expected_end_time, end_time, total_downtime_minutes, operating_downtime_minutes, reason, created_at) VALUES
    (1, 3, 1, NOW() - INTERVAL '18 hours', NOW() + INTERVAL '6 hours', NULL, NULL, 480, 'Mechanical binding in resin tank wiper axis during biocompatible print run.', NOW() - INTERVAL '18 hours'),
    (2, 2, 2, NOW() - INTERVAL '5 days', NOW() - INTERVAL '4 days', NOW() - INTERVAL '4 days', 360, 360, 'BNC connector replacement and RF front-end recalibration on Channel 3.', NOW() - INTERVAL '4 days'),
    (3, 3, 3, NOW() - INTERVAL '60 days', NOW() - INTERVAL '58 days', NOW() - INTERVAL '58 days', 2880, 960, 'Galvo mirror failure and UV laser engine recertification overhaul.', NOW() - INTERVAL '58 days');

-- 22. Stored Documents (Certificates, Manuals, Service Reports)
INSERT INTO stored_documents (id, entity_type, entity_id, file_name, file_type, file_size, file_reference, uploaded_by, created_at, updated_at) VALUES
    (1, 'CALIBRATION_CERTIFICATE', 1, 'CAL-KEY-2026-0891-Cert.pdf', 'application/pdf', 245120, '/documents/calibration/cal-key-2026-0891.pdf', 8, NOW() - INTERVAL '30 days', NOW() - INTERVAL '30 days'),
    (2, 'EQUIPMENT_MANUAL', 1, 'Confocal_Microscope_User_Manual.pdf', 'application/pdf', 8421000, '/documents/manuals/confocal-manual.pdf', 7, NOW() - INTERVAL '25 days', NOW() - INTERVAL '25 days'),
    (3, 'MAINTENANCE_REPORT', 2, 'WO-2026-000002-ServiceReport.pdf', 'application/pdf', 184500, '/documents/maintenance/wo-2026-000002.pdf', 8, NOW() - INTERVAL '4 days', NOW() - INTERVAL '4 days');

-- 23. Calibration Records (VALID, DUE_SOON, EXPIRED states)
INSERT INTO calibration_records (id, equipment_id, calibration_date, next_due_date, scheduled_date, result, status, assigned_to, certificate_number, certificate_document_id, certificate_reference, work_order_id, notes, created_at) VALUES
    (1, 2, CURRENT_DATE - INTERVAL '30 days', CURRENT_DATE + INTERVAL '335 days', NULL, 'PASSED', 'COMPLETED', 8, 'CAL-KEY-2026-0891', 1, '/documents/calibration/cal-key-2026-0891.pdf', 6, 'Full NIST-traceable calibration performed. Timing jitter and vertical accuracy within +/-0.5% tolerance.', NOW() - INTERVAL '30 days'),
    (2, 1, NULL, CURRENT_DATE + INTERVAL '5 days', CURRENT_DATE + INTERVAL '2 days', NULL, 'SCHEDULED', 8, NULL, NULL, NULL, NULL, 'Annual optical alignment and fluorescence intensity standard bead calibration with Chroma filter check.', NOW() - INTERVAL '2 days'),
    (3, 6, CURRENT_DATE - INTERVAL '400 days', CURRENT_DATE - INTERVAL '35 days', NULL, 'PASSED', 'COMPLETED', NULL, 'CAL-SHIM-2025-4412', NULL, NULL, NULL, 'Deuterium and Tungsten lamp baseline wavelength alignment expired. Recertification required.', NOW() - INTERVAL '400 days');

-- Link work orders back to calibration and documents
UPDATE work_orders SET calibration_record_id = 1, report_document_id = 1, report_reference = '/documents/calibration/cal-key-2026-0891.pdf' WHERE id = 6;
UPDATE work_orders SET report_document_id = 3, report_reference = '/documents/maintenance/wo-2026-000002.pdf' WHERE id = 2;

-- =============================================================================
-- MILESTONE 3: USAGE CHARGES, BUDGETS, INVOICES & BILLING
-- =============================================================================

-- 24. Usage Charges (INTERNAL and EXTERNAL across PENDING, INVOICED, PAID)
INSERT INTO usage_charges (id, booking_id, equipment_id, user_id, institution_id, user_institution_id, department_id, charge_type, hourly_rate, usage_minutes, amount, status, invoice_id, sharing_agreement_id, created_at) VALUES
    (1, 1, 2, 9, 1, 1, 1, 'INTERNAL', 150.00, 180, 450.00, 'INVOICED', 1, NULL, NOW() - INTERVAL '10 days'),
    (2, 2, 1, 9, 1, 1, 1, 'INTERNAL', 450.00, 360, 2700.00, 'INVOICED', 1, NULL, NOW() - INTERVAL '8 days'),
    (3, 3, 2, 7, 1, 1, 2, 'INTERNAL', 150.00, 480, 1200.00, 'PENDING', NULL, NULL, NOW() - INTERVAL '6 days'),
    (4, 4, 1, 7, 1, 1, 2, 'INTERNAL', 450.00, 480, 3600.00, 'PENDING', NULL, NULL, NOW() - INTERVAL '4 days'),
    (5, 5, 1, 7, 1, 1, 2, 'INTERNAL', 450.00, 480, 3600.00, 'PENDING', NULL, NULL, NOW() - INTERVAL '2 days'),
    (6, 6, 3, 8, 1, 1, 3, 'INTERNAL', 250.00, 480, 2000.00, 'PENDING', NULL, NULL, NOW() - INTERVAL '15 days'),
    (7, 7, 3, 8, 1, 1, 3, 'INTERNAL', 250.00, 480, 2000.00, 'PENDING', NULL, NULL, NOW() - INTERVAL '12 days'),
    (8, 8, 3, 8, 1, 1, 3, 'INTERNAL', 250.00, 480, 2000.00, 'PENDING', NULL, NULL, NOW() - INTERVAL '9 days'),
    (9, 9, 1, 10, 1, 2, 4, 'EXTERNAL', 450.00, 480, 3600.00, 'PAID', 2, 1, NOW() - INTERVAL '5 days'),
    (10, 10, 1, 10, 1, 2, 4, 'EXTERNAL', 450.00, 480, 3600.00, 'PAID', 2, 1, NOW() - INTERVAL '3 days');

-- 25. Department Budgets for Fiscal Year 2026 (OK, WARNING, EXCEEDED statuses)
-- Dept 1: Allocated ₹50,000 | Spent ₹3,150 -> 6.3% -> OK
-- Dept 2: Allocated ₹10,000 | Spent ₹8,400 -> 84.0% -> WARNING
-- Dept 3: Allocated ₹5,000  | Spent ₹6,000 -> 120.0% -> EXCEEDED
INSERT INTO department_budgets (id, institution_id, department_id, fiscal_year, allocated_amount, created_at, updated_at) VALUES
    (1, 1, 1, 2026, 50000.00, NOW() - INTERVAL '30 days', NOW() - INTERVAL '30 days'),
    (2, 1, 2, 2026, 10000.00, NOW() - INTERVAL '30 days', NOW() - INTERVAL '30 days'),
    (3, 1, 3, 2026, 5000.00, NOW() - INTERVAL '30 days', NOW() - INTERVAL '30 days'),
    (4, 2, 4, 2026, 75000.00, NOW() - INTERVAL '30 days', NOW() - INTERVAL '30 days'),
    (5, 2, 5, 2026, 60000.00, NOW() - INTERVAL '30 days', NOW() - INTERVAL '30 days'),
    (6, 3, 6, 2026, 80000.00, NOW() - INTERVAL '30 days', NOW() - INTERVAL '30 days'),
    (7, 3, 7, 2026, 65000.00, NOW() - INTERVAL '30 days', NOW() - INTERVAL '30 days');

-- 26. Invoices (INV-YYYY-XXXXXX format across DRAFT, ISSUED, PAID)
INSERT INTO invoices (id, invoice_number, institution_id, recipient_institution_id, recipient_department_id, invoice_type, period_start, period_end, subtotal, tax_rate, tax_amount, total_amount, due_date, status, issued_at, paid_at, cancelled_at, created_at, updated_at) VALUES
    (1, 'INV-2026-000001', 1, NULL, 1, 'DEPARTMENT_CHARGEBACK', CURRENT_DATE - INTERVAL '30 days', CURRENT_DATE, 3150.00, 0.00, 0.00, 3150.00, CURRENT_DATE + INTERVAL '15 days', 'ISSUED', NOW() - INTERVAL '2 days', NULL, NULL, NOW() - INTERVAL '2 days', NOW() - INTERVAL '2 days'),
    (2, 'INV-2026-000002', 1, 2, NULL, 'INTER_INSTITUTION', CURRENT_DATE - INTERVAL '30 days', CURRENT_DATE, 7200.00, 18.00, 1296.00, 8496.00, CURRENT_DATE + INTERVAL '10 days', 'PAID', NOW() - INTERVAL '5 days', NOW() - INTERVAL '1 day', NULL, NOW() - INTERVAL '5 days', NOW() - INTERVAL '1 day'),
    (3, 'INV-2026-000003', 1, 2, NULL, 'INTER_INSTITUTION', CURRENT_DATE - INTERVAL '15 days', CURRENT_DATE, 3600.00, 18.00, 648.00, 4248.00, CURRENT_DATE + INTERVAL '20 days', 'DRAFT', NULL, NULL, NULL, NOW() - INTERVAL '1 day', NOW() - INTERVAL '1 day');

-- 27. Invoice Items (Snapshot Line Items)
INSERT INTO invoice_items (id, invoice_id, charge_id, equipment_id, equipment_name, user_id, user_name, usage_minutes, hourly_rate, amount, created_at) VALUES
    (1, 1, 1, 2, 'Keysight 4-Channel 1GHz Digital Storage Oscilloscope', 9, 'Dilip Karthik', 180, 150.00, 450.00, NOW() - INTERVAL '2 days'),
    (2, 1, 2, 1, 'High-Resolution Confocal Laser Microscope', 9, 'Dilip Karthik', 360, 450.00, 2700.00, NOW() - INTERVAL '2 days'),
    (3, 2, 9, 1, 'High-Resolution Confocal Laser Microscope', 10, 'Ananya Roy', 480, 450.00, 3600.00, NOW() - INTERVAL '5 days'),
    (4, 2, 10, 1, 'High-Resolution Confocal Laser Microscope', 10, 'Ananya Roy', 480, 450.00, 3600.00, NOW() - INTERVAL '5 days');

-- =============================================================================
-- MILESTONE 3: NOTIFICATIONS, PREFERENCES & ALERTS
-- =============================================================================

-- 28. User Notification Preferences
INSERT INTO user_notification_preferences (id, user_id, email_enabled, sms_enabled, push_enabled, created_at, updated_at) VALUES
    (1, 1, TRUE, FALSE, TRUE, NOW() - INTERVAL '20 days', NOW() - INTERVAL '20 days'),
    (2, 2, TRUE, FALSE, TRUE, NOW() - INTERVAL '20 days', NOW() - INTERVAL '20 days'),
    (3, 3, TRUE, FALSE, TRUE, NOW() - INTERVAL '20 days', NOW() - INTERVAL '20 days'),
    (4, 4, TRUE, FALSE, TRUE, NOW() - INTERVAL '20 days', NOW() - INTERVAL '20 days'),
    (5, 5, TRUE, FALSE, TRUE, NOW() - INTERVAL '20 days', NOW() - INTERVAL '20 days'),
    (6, 6, TRUE, FALSE, TRUE, NOW() - INTERVAL '20 days', NOW() - INTERVAL '20 days'),
    (7, 7, TRUE, FALSE, TRUE, NOW() - INTERVAL '20 days', NOW() - INTERVAL '20 days'),
    (8, 8, TRUE, FALSE, TRUE, NOW() - INTERVAL '20 days', NOW() - INTERVAL '20 days'),
    (9, 9, TRUE, FALSE, TRUE, NOW() - INTERVAL '20 days', NOW() - INTERVAL '20 days'),
    (10, 10, TRUE, FALSE, TRUE, NOW() - INTERVAL '20 days', NOW() - INTERVAL '20 days'),
    (11, 11, TRUE, FALSE, FALSE, NOW() - INTERVAL '2 days', NOW() - INTERVAL '2 days');

-- 29. Notifications (Unread and Read notifications for all roles)
INSERT INTO notifications (id, recipient_id, notification_type, title, message, reference_type, reference_id, is_read, created_at) VALUES
    -- System Admin (bunty@1234.com, user 2)
    (1, 2, 'SHARING_REQUEST_SUBMITTED', 'New Inter-Institution Request', 'Researcher Ananya Roy from NITW submitted a sharing request for Confocal Microscope.', 'SHARING_REQUEST', 1, FALSE, NOW() - INTERVAL '2 hours'),
    (2, 2, 'CALIBRATION_WORK_ORDER_CREATED', 'Calibration Task Initialized', 'Work order WO-2026-000006 created for Keysight DSO annual calibration.', 'WORK_ORDER', 6, TRUE, NOW() - INTERVAL '1 day'),
    
    -- Institution Admin (admin@mrec.test, user 3)
    (3, 3, 'SHARING_REQUEST_SUBMITTED', 'Incoming Equipment Sharing Request', 'NITW has forwarded an external booking request for Confocal Microscope requiring your review.', 'SHARING_REQUEST', 1, FALSE, NOW() - INTERVAL '3 hours'),
    (4, 3, 'CALIBRATION_EXPIRED', 'Equipment Calibration Expired Warning', 'Shimadzu UV-2600i Double-Beam UV-Vis Spectrophotometer calibration expired on ' || TO_CHAR(CURRENT_DATE - INTERVAL '35 days', 'DD Mon YYYY') || '.', 'CALIBRATION', 3, FALSE, NOW() - INTERVAL '1 day'),
    (5, 3, 'BOOKING_CONFIRMED', 'External Usage Session Completed', 'External booking session #10 under sharing agreement #1 successfully completed. Invoiced as INV-2026-000002.', 'BOOKING', 10, TRUE, NOW() - INTERVAL '2 days'),

    -- Department Head (depthead.cse@mrec.test, user 6)
    (6, 6, 'BOOKING_CONFIRMED', 'Department Booking Confirmed', 'Dilip Karthik booked Xilinx UltraScale+ FPGA Development Cluster for FPGA serial link transceivers signal integrity validation.', 'BOOKING', 11, FALSE, NOW() - INTERVAL '1 hour'),
    (7, 6, 'IDLE_EQUIPMENT_ALERT', 'Equipment Idle Notice', 'Haas Mini Mill 5-Axis CNC Machining Unit has recorded under 10% utilization over the past 30 days.', 'EQUIPMENT', 5, TRUE, NOW() - INTERVAL '3 days'),

    -- Lab Manager (manager@mrec.test, user 7)
    (8, 7, 'PREVENTIVE_MAINTENANCE_CREATED', 'Preventive Maintenance Due in 3 Days', 'Biweekly Optical Power Calibration & Filter Inspection is due on ' || TO_CHAR(CURRENT_DATE + INTERVAL '3 days', 'DD Mon YYYY') || '.', 'WORK_ORDER', 5, FALSE, NOW() - INTERVAL '4 hours'),
    (9, 7, 'BOOKING_REMINDER', 'Upcoming Session Review', 'Researcher Dilip Karthik has an upcoming session on Confocal Laser Microscope starting tomorrow at 10:00.', 'BOOKING', 12, FALSE, NOW() - INTERVAL '6 hours'),
    (10, 7, 'CALIBRATION_WORK_ORDER_CREATED', 'Calibration Assigned', 'Annual NIST calibration WO-2026-000006 assigned to technician K. Ramesh.', 'WORK_ORDER', 6, TRUE, NOW() - INTERVAL '2 days'),

    -- Lab Technician (technician@mrec.test, user 8)
    (11, 8, 'PREVENTIVE_MAINTENANCE_CREATED', 'New Work Order Assigned', 'You have been assigned work order WO-2026-000001 (Formlabs 3D Bioprinter Recoater Arm Sticking).', 'WORK_ORDER', 1, FALSE, NOW() - INTERVAL '1 day'),
    (12, 8, 'CALIBRATION_EXPIRED', 'Upcoming Calibration Task', 'Confocal Laser Microscope optical alignment and calibration is scheduled within 2 days.', 'CALIBRATION', 2, FALSE, NOW() - INTERVAL '5 hours'),
    (13, 8, 'BOOKING_REMINDER', 'Work Order Completed', 'WO-2026-000002 has been marked as completed. Service log successfully archived.', 'WORK_ORDER', 2, TRUE, NOW() - INTERVAL '4 days'),

    -- Researcher (researcher@mrec.test, user 9)
    (14, 9, 'BOOKING_CONFIRMED', 'Booking Approved', 'Your booking for Keysight 4-Channel 1GHz Digital Storage Oscilloscope today at 09:00 has been approved.', 'BOOKING', 11, FALSE, NOW() - INTERVAL '30 minutes'),
    (15, 9, 'WAITLIST_OFFER', 'Waitlist Slot Available', 'A booking slot has opened for Confocal Laser Microscope tomorrow at 10:00. Claim within 2 hours.', 'WAITLIST', 1, FALSE, NOW() - INTERVAL '1 hour'),
    (16, 9, 'BOOKING_CONFIRMED', 'Booking Approved', 'Your booking for Confocal Laser Microscope tomorrow at 10:00 has been approved.', 'BOOKING', 12, TRUE, NOW() - INTERVAL '8 hours');

-- 30. Daily Utilization Snapshots (Past 7 Days for Analytics & Heatmap Visualization)
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

-- 31. Sync Auto-increment Sequences with Max IDs
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
-- Milestone 3 sequence synchronization
SELECT setval(pg_get_serial_sequence('maintenance_requests', 'id'), COALESCE(MAX(id), 1)) FROM maintenance_requests;
SELECT setval(pg_get_serial_sequence('preventive_maintenance_schedules', 'id'), COALESCE(MAX(id), 1)) FROM preventive_maintenance_schedules;
SELECT setval(pg_get_serial_sequence('work_orders', 'id'), COALESCE(MAX(id), 1)) FROM work_orders;
SELECT setval(pg_get_serial_sequence('work_order_service_logs', 'id'), COALESCE(MAX(id), 1)) FROM work_order_service_logs;
SELECT setval(pg_get_serial_sequence('equipment_downtime_logs', 'id'), COALESCE(MAX(id), 1)) FROM equipment_downtime_logs;
SELECT setval(pg_get_serial_sequence('stored_documents', 'id'), COALESCE(MAX(id), 1)) FROM stored_documents;
SELECT setval(pg_get_serial_sequence('usage_charges', 'id'), COALESCE(MAX(id), 1)) FROM usage_charges;
SELECT setval(pg_get_serial_sequence('department_budgets', 'id'), COALESCE(MAX(id), 1)) FROM department_budgets;
SELECT setval(pg_get_serial_sequence('invoices', 'id'), COALESCE(MAX(id), 1)) FROM invoices;
SELECT setval(pg_get_serial_sequence('invoice_items', 'id'), COALESCE(MAX(id), 1)) FROM invoice_items;
SELECT setval(pg_get_serial_sequence('user_notification_preferences', 'id'), COALESCE(MAX(id), 1)) FROM user_notification_preferences;
SELECT setval(pg_get_serial_sequence('notifications', 'id'), COALESCE(MAX(id), 1)) FROM notifications;
SELECT setval('invoice_number_seq', 10, true);
