# Milestone 3 Live Demo Guide & Architecture Reference

**Platform:** Lab Resource Platform (`lab-resource-platform`)  
**Core Technologies:** Spring Boot 4.1.1 + Java 21 + PostgreSQL 18 (Backend) | React 19 + Vite + Tailwind CSS 4 + Lucide Icons + Recharts (Frontend)  
**Database Seed:** Automatically pre-seeded via `demo_seed.sql` / Flyway migrations `V1`–`V14`.

---

## 1. Architecture & Module Overview

The Lab Resource Platform enables multi-tenant equipment scheduling, inter-institution resource sharing, preventive/corrective maintenance workflows, automated usage billing, real-time alert dispatching, and comprehensive analytics across academic and research institutions.

```
+-------------------------------------------------------------------------------------------------------------+
|                                          FRONTEND (React 19 + Tailwind CSS 4)                               |
|   +-------------------+  +-------------------+  +-------------------+  +-------------------+  +-----------+ |
|   | Maintenance & WOs |  | Calibration Hub   |  | Billing & Invoices|  | Alerts & Notifs   |  | Dashboards| |
|   +-------------------+  +-------------------+  +-------------------+  +-------------------+  +-----------+ |
+-------------------------------------------------------+-----------------------------------------------------+
                                                        | REST APIs (JSON) & STOMP / WebSocket (/ws/equipment)
+-------------------------------------------------------v-----------------------------------------------------+
|                                      BACKEND (Spring Boot 4.1.1 + Java 21)                                  |
|  +-------------------------------------------------------------------------------------------------------+  |
|  | Security & RBAC: JwtAuthenticationFilter + ResourceAccessPolicy (6 Isolated Roles)                    |  |
|  +-------------------------------------------------------------------------------------------------------+  |
|  | Milestone 1: Users, Roles, Institutions, Multi-Department Scope, Equipment Registry, Document Store  |  |
|  | Milestone 2: Bookings (Single/Recurring), Overlap Prevention, Timezones, Sharing Listings & Agmts    |  |
|  | Milestone 3 (Sessions 1-5):                                                                           |  |
|  |   - Session 1: Maintenance Requests, Work Orders (WO-YYYY-XXXXXX), Schedules, Downtime & Analytics    |  |
|  |   - Session 2: Dynamic Calibration Engine, Work Order Calibration Records, 10MB Uploads, Workload     |  |
|  |   - Session 3: Usage Charges (BigDecimal HALF_UP), Department Budgets, Invoices (INV-YYYY-XXXXXX)     |  |
|  |   - Session 4: Centralized NotificationService, Channel Preference Validation, Real-Time WebSocket    |  |
|  |   - Session 5: Role-Specific Dashboards, ROI & Procurement Recommendations, PDF/XLSX Report Engine    |  |
|  +-------------------------------------------------------------------------------------------------------+  |
|                                                        | JPA / Hibernate / Flyway Baseline (V1 -> V14)       |
|                                                        v                                                     |
|                                       PostgreSQL 18 Database (`labdb`)                                      |
+-------------------------------------------------------------------------------------------------------------+
```

### Module Breakdown by Milestone
- **Milestone 1 — Identity, Multi-Tenancy & Asset Catalog:**
  - Multi-institution hierarchy (`institutions`, `departments`, `users`, `user_roles`).
  - Equipment registry with availability rules, custom specifications (JSONB), and operating hours.
  - Secure document storage with time-limited signed access URLs.
- **Milestone 2 — Booking Engine & Inter-Institution Sharing:**
  - Strict conflict-free booking scheduler with timezone normalization (UTC storage).
  - Multi-tier approval flows, automated FIFO waitlist queuing with 3-minute confirmation windows.
  - Inter-institution equipment listings, peer requests, and legal sharing agreements.
- **Milestone 3 — Operations, Maintenance & Financial Lifecycle:**
  - **Session 1 (Maintenance & Downtime):** Corrective & preventive work orders, start-preview conflict analysis, atomic auto-cancellation of conflicting bookings, and MTBF/MTTR downtime analytics.
  - **Session 2 (Calibration & Technician Workload):** Dynamic calibration state machine (`VALID`, `DUE_SOON`, `EXPIRED`, `NONE`), one-step work order completion + certificate creation, 10 MB file uploads, and technician workload balancing.
  - **Session 3 (Billing, Budgets & Invoicing):** Automatic usage charges computed from exact minute durations using `BigDecimal` (`HALF_UP`), department budget health tracking (`OK`, `WARNING`, `EXCEEDED`), and formal invoice generation (`INV-YYYY-XXXXXX`).
  - **Session 4 (Notifications & Alerts):** Centralized notification dispatcher across in-app, email, SMS, and push channels with WebSocket broadcasts and phone number verification safeguards.
  - **Session 5 (Dashboards & Reports):** Tailored researcher, lab manager, and institution administrator dashboards (with lifecycle ROI and procurement recommendations), plus live previewable reports exportable to PDF and Excel.

---

## 2. Demo Credentials & Role Access Matrix

All demo accounts share the standard password: **`Demo@1234`** *(recovery System Admin also supports `Bunty@12345`)*.

| Role | Email | Institution / Scope | Primary Responsibilities in Demo |
| :--- | :--- | :--- | :--- |
| **System Admin** | `sysadmin@platform.test` / `bunty@1234.com` | Global Platform | Platform health, system settings, global metrics. *(Strictly receives 403 Forbidden on lab maintenance)* |
| **Institution Admin** | `admin@mrec.test` | MREC (Inst #1) | Institutional dashboard, department budgets, invoice issuing/payments, calibration overview, equipment ROI |
| **Department Head** | `depthead.cse@mrec.test` | MREC - CSE Dept | Department budget monitoring, chargeback review, department utilization |
| **Lab Manager** | `manager@mrec.test` | MREC - ECE Dept | Reviewing maintenance requests, assigning work orders, starting work orders (with auto-cancellations), lab health |
| **Lab Technician** | `technician@mrec.test` | MREC - ECE Dept | My Tasks view (`/my-tasks`), executing work orders, logging service notes, completing work orders & calibrations |
| **Researcher / Student** | `researcher@mrec.test` | MREC - CSE Dept | Booking equipment, submitting maintenance issue requests, viewing personal dashboard and notifications |
| **External Researcher** | `researcher@nitw.test` | NITW (Inst #2) | Cross-institution external bookings (used for testing inter-institution external usage charges & invoices) |
| **Admin (No Phone)** | `pending.admin@osmania.test` | Osmania University | Used to demonstrate phone number validation when attempting to enable SMS notifications |

---

## 3. Step-by-Step Live Walkthrough Order

### Act 1: Maintenance, Overlap Auto-Cancellation & Security Boundary
1. **Submit Maintenance Request (as Researcher):**
   - Sign in as `researcher@mrec.test`.
   - Navigate to **Maintenance > Submit Request** (or Equipment detail page).
   - Submit issue for **Keysight Infiniium 4-Channel DSO** (`EQ-MREC-OSC-01`): Title: *"Channel 1 Trace Drift"*, Priority: **HIGH**.
   - Notice status is `PENDING`.
2. **Review & Approve Request (as Lab Manager):**
   - Sign in as `manager@mrec.test`.
   - Open **Maintenance > Requests Queue**.
   - Click **Approve**.
   - **Verification:** Request transitions to `APPROVED` and automatically creates a new `CORRECTIVE` work order with strictly formatted number (e.g. `WO-2026-000007`) in the same database transaction.
3. **Start-Preview Analysis & Work Order Execution:**
   - In Work Orders list, click on the newly created work order and open the **Start Maintenance** drawer/dialog.
   - The **Start-Preview** API automatically scans for overlapping bookings during the expected maintenance window and displays the affected researcher names and session times.
   - Click **Confirm & Start Maintenance**.
   - **Verification:**
     - Work order status switches to `IN_PROGRESS`.
     - Equipment status immediately updates to `UNDER_MAINTENANCE`.
     - Overlapping bookings are automatically transitioned to `CANCELLED` with audit notes.
     - Researchers receive immediate in-app and WebSocket notifications.
4. **Complete Work Order (as Lab Technician):**
   - Sign in as `technician@mrec.test`.
   - Open **My Tasks** (`/my-tasks`).
   - Open the work order, add a service log note: *"Replaced preamp bypass capacitor"*, then click **Complete Work Order**.
   - Input cost: `1450.00` and resolution notes.
   - **Verification:** Work order status becomes `COMPLETED`, downtime log is finalized, and equipment status restores to `AVAILABLE`.
5. **System Admin Isolation Verification:**
   - Sign in as `sysadmin@platform.test` (or `bunty@1234.com`).
   - Attempt to access `/api/maintenance/work-orders` or maintenance management pages.
   - **Verification:** API strictly returns **403 Forbidden** (System Admin cannot inspect lab-level operational maintenance).

---

### Act 2: Calibration Tracking, Technician Workload & Document Links
1. **Dynamic Calibration Overview:**
   - Sign in as `admin@mrec.test` or `manager@mrec.test`.
   - Navigate to **Calibration Overview** (`/calibrations`).
   - Notice the dynamic state chips computed on-the-fly:
     - `VALID` (Green): Next due date > 30 days away.
     - `DUE_SOON` (Yellow): Next due date within 30 days.
     - `EXPIRED` (Red): Next due date in the past.
     - `NONE` (Gray): No calibration records on file.
   - *Technical Note:* These states are calculated dynamically from the latest record and current clock — no stale state column is stored.
2. **Technician Workload Balancing:**
   - Switch to **Technician Tasks & Workload** tab (or call `/api/technicians`).
   - View assigned tasks per technician with active work orders vs completed work orders.
3. **File Upload & 10-Minute Signed Document URLs:**
   - In the calibration / work order detail drawer, test document attachments:
     - Attempt to upload an oversized file (> 10 MB): Rejected immediately with **HTTP 400 Bad Request**.
     - Upload a valid calibration certificate (`test_cert.pdf`).
     - Click the generated document link: Inspect the presigned URL containing HMAC signature and 10-minute expiry (`?ref=...&expires=...&sig=...`).
     - Paste the link into a private/incognito window without Authorization headers to verify direct, secure access.

---

### Act 3: Usage Charges, Department Budgets & Invoicing
1. **Automated Usage Charge Generation:**
   - Navigate to **Billing & Finance > Usage Charges** (`/billing/charges`).
   - Observe records generated automatically upon booking completion:
     - `INTERNAL`: Bookings by researchers within the institution (e.g. CSE Dept).
     - `EXTERNAL`: Bookings via Sharing Agreements (billed at agreement rates).
     - Calculated from actual usage minutes: `(actualMinutes * hourlyRate) / 60` using `BigDecimal HALF_UP` rounding.
2. **Department Budget Health:**
   - Navigate to **Billing > Department Budgets** (`/billing/budgets`).
   - View visual budget health cards:
     - **OK** (Green): Spend under 80% of allocated budget (e.g., Mechanical Engineering).
     - **WARNING** (Yellow): Spend between 80% and 100% (e.g., Electronics & Communication).
     - **EXCEEDED** (Red): Spend > 100% of allocation (e.g., Computer Science & Engineering).
3. **Formal Invoice Generation & Payment Lifecycle:**
   - Click **Generate Invoice**.
   - Select `DEPARTMENT_CHARGEBACK` for Computer Science department (or `INTER_INSTITUTION` for NITW).
   - Set billing period (last 30 days) and tax rate (0% for internal, 18% for inter-institution).
   - Click **Generate Draft**.
   - **Verification:** An invoice is created with strictly formatted identifier `INV-2026-00000X` in `DRAFT` status with line items linked to un-invoiced usage charges.
   - Click **Issue Invoice** -> Status transitions to `ISSUED`.
   - Click **Record Payment** -> Status transitions to `PAID`.
   - Click **Download PDF** -> Generates a styled, printable invoice PDF with official header, itemized usage charges, and tax totals.

---

### Act 4: Real-Time Notifications & Channel Preferences
1. **Interactive Notification Center:**
   - Notice the bell icon in the top header displaying the live unread count (`/api/notifications/unread-count`).
   - Click the bell to open the flyout menu showing recent notifications (Work Order assignments, booking approvals, reminders).
2. **Real-Time WebSocket Updates:**
   - Keep two browser sessions side-by-side (Manager and Technician).
   - As Manager, assign a work order to the Technician.
   - Notice the Technician's bell icon badge updates instantly via STOMP WebSocket topic `/topic/users/{id}/notifications`.
3. **Notification Preferences & Phone Verification Safeguard:**
   - Navigate to **Account Settings > Notifications**.
   - Sign in as `pending.admin@osmania.test` (an account with no registered phone number).
   - Attempt to enable the **SMS Notifications** toggle and save.
   - **Verification:** The backend rejects the change with **HTTP 400 Bad Request** (*"A registered phone number is required to enable SMS notifications"*), ensuring SMS alerts cannot fail silently.
4. **Bulk Action:**
   - Click **Mark all as read**. The unread badge instantly zeroes out.

---

### Act 5: Role-Specific Dashboards & Exportable Reports
1. **Researcher Dashboard (`/dashboard`):**
   - Sign in as `researcher@mrec.test`.
   - Displays personalized next 5 bookings, quick availability status of preferred equipment, and 90-day usage summary (hours used, bookings by status).
2. **Lab Manager Dashboard (`/dashboard/manager`):**
   - Sign in as `manager@mrec.test`.
   - Displays operational lab equipment health, pending booking approvals, pending maintenance requests, and upcoming preventive maintenance countdowns.
3. **Institution Admin Dashboard (`/dashboard/institution`):**
   - Sign in as `admin@mrec.test`.
   - Displays institutional utilization rate, revenue & cost aggregates.
   - **Lifecycle ROI Table:** Equipment purchase cost vs cumulative maintenance expense vs revenue generated vs Net ROI percentage.
   - **Procurement Recommendations:** AI/rule-based cards classifying equipment into:
     - `HIGH_DEMAND`: Utilization > 85% and waitlist queue > 3 (recommend purchasing additional unit).
     - `LOW_UTILIZATION`: Utilization < 15% (recommend listing on inter-institution sharing network).
     - `HIGH_MAINTENANCE`: Maintenance costs exceed 25% of purchase cost (recommend replacement/refurbishment).
4. **Report Previews and Binary Exports:**
   - Navigate to **Reports & Analytics** (`/reports`).
   - Select report type: `UTILIZATION`, `MAINTENANCE`, `FINANCIAL`, or `CALIBRATION`.
   - Adjust date range and department filters, then click **Generate Report**.
   - View interactive paginated data table and summary metrics in-browser.
   - Click **Export PDF**: Generates branded, styled PDF document.
   - Click **Export Excel (XLSX)**: Generates structured Microsoft Excel spreadsheet with formatted data columns.

---

## 4. Key Technical Highlights

1. **High-Precision Financial Math:**
   - All charges, budgets, invoice line items, and taxes are calculated using Java `BigDecimal` with `RoundingMode.HALF_UP` rounded to 2 decimal places at the very end of computation.
   - Billing calculations use exact usage minutes (`(minutes * hourlyRate) / 60`) rather than prematurely rounded hours, avoiding cumulative rounding drift.
2. **Atomic Transactional Integrity:**
   - Starting a work order transitions equipment to `UNDER_MAINTENANCE`, auto-cancels overlapping bookings, and logs downtime logs inside a single `@Transactional` boundary.
   - Completing a booking creates the corresponding `UsageCharge` in the same transaction.
   - Completing a calibration work order updates the work order, records resolution notes, and writes the `CalibrationRecord` atomically.
3. **Dynamic State Computation:**
   - Calibration status (`VALID`, `DUE_SOON`, `EXPIRED`, `NONE`) is evaluated dynamically via SQL/service logic comparing `nextDueDate` against `LocalDate.now()`, ensuring the database never contains stale cached state flags.
4. **Document Security & Multipart Protection:**
   - Document access links are protected by HMAC-SHA256 signatures and strict 10-minute expiry windows (`/api/documents/raw?ref=...&expires=...&sig=...`).
   - Multipart file uploads enforce a strict 10 MB boundary, validate MIME types, and verify magic byte signatures (`%PDF` for PDFs, PNG magic header, JPEG magic header).
5. **Caching & Eviction Strategy:**
   - Analytical queries and static dashboards leverage Spring Caching (`@Cacheable("analytics")`), with granular `@CacheEvict` triggers upon booking completions, work order starts/completions, and invoice payments. Personal researcher dashboards remain un-cached for real-time accuracy.
6. **Strict Multi-Tenant Role Isolation:**
   - Every protected endpoint enforces both Spring Security HTTP filters and `@PreAuthorize("@resourceAccess...")` rules.
   - Institution Administrators can only inspect and manage assets belonging to their institution.
   - `SYSTEM_ADMIN` is explicitly prohibited from accessing lab-level operational endpoints (e.g. maintenance requests, work orders, booking approvals), returning **403 Forbidden**.

---

## 5. Verification Checklist

To run the automated end-to-end verification suite:

```powershell
# In PowerShell (with local PostgreSQL 18 running):
$env:DB_PASSWORD = "Saikiran@123"
$env:JWT_SECRET = "Az4QgClB/R8osKj42wsav5RSBc2Qpld2UK52lzjE+L4="

# Run backend test suite (297+ unit & integration tests)
cd backend\lab-resource-platform
.\mvnw.cmd test -B -o --no-transfer-progress

# Run frontend tests & build
cd ..\..\frontend
npm test
npm run build
```
