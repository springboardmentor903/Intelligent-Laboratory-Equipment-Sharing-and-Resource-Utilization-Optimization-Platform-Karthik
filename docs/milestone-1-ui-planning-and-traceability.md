# Milestone 1 UI Planning and Requirements Traceability

**Evidence date:** 2026-10-25. “Existing” describes inspected code; “Proposed” is a planning sketch, not a claim that the wireframe is implemented.

## Requirement source and scope

The mentor PDF and original Excel tracker are present at the project root and were read. The tracker places T1?T17 in Milestone 1. Exact descriptions, evidence, gaps, and statuses are recorded in `docs/milestone-1-requirements-traceability.md`; designs below remain proposed planning artifacts where identified.

## Existing routes and screens

Routes are configured in `frontend/src/App.jsx`.

| Route | Access | Existing screen/source | Current role handling |
|---|---|---|---|
| `/` | Public | `frontend/src/pages/Home.jsx` | Static product landing page. |
| `/login` | Public | `frontend/src/pages/Login.jsx` | Uses `AuthContext.login`. |
| `/register` | Public | `frontend/src/pages/Register.jsx` | Registration form calls the existing registration API. |
| `/password-reset` | Public | `frontend/src/pages/PasswordReset.jsx` | Request and confirmation form; accepts a reset token from the URL fragment. SMTP delivery is optional and environment-gated. |
| `/oauth/callback` | Public | `frontend/src/pages/OAuthCallback.jsx` | Exchanges a one-use opaque Google login code for the normal trusted backend session. |
| `/dashboard` | Protected | `frontend/src/pages/Dashboard.jsx` | Uses role-derived endpoint plans; shows equipment, booking, utilization, and staff maintenance summaries as permitted by current helpers. |
| `/equipment` | Protected | `frontend/src/pages/Equipment.jsx` | Inventory search/list and role-gated mutations; selected-equipment calibration/certification history and creation controls. |
| `/bookings` | Protected | `frontend/src/pages/Bookings.jsx` | Researcher history uses the authenticated user's ID; staff roles use the authorized booking collection. Booking actions are role/status-gated. |
| `/booking-calendar` | Protected | `frontend/src/pages/BookingCalendar.jsx` | FullCalendar displays only the current role?s authorized booking collection; it does not represent free slots. |
| `/utilization` | Protected | `frontend/src/pages/Utilization.jsx` | List, summaries/date range, and role-gated record mutations. |
| `/maintenance` | Protected | `frontend/src/pages/Maintenance.jsx` | Researcher report history is owner-scoped; staff records/summary and workflow actions are role-gated. |
| `/profile` | Protected | `frontend/src/pages/Profile.jsx` | Read-only identity and role fields from the trusted login response/session; links to password reset. No profile editing API is present. |
| `*` | Public fallback | `frontend/src/pages/NotFound.jsx` | Not-found screen. |

All six recognized role names and route/action helpers are in `frontend/src/access/roleAccess.js`. Frontend checks guide presentation only; backend authorization remains authoritative.

## Existing equipment catalog contract

Fields in `Equipment.java` are `id`, `name`, `category`, `description`, `institutionId`, `labLocation`, `quantity`, `status`, `createdAt`, `specifications`, and `tags`. Specifications are represented as a JSONB map; tags are an element collection. Calibration and certification are separate record entities related to equipment. `documentReference` is a text reference field, not a file upload contract.

The equipment list endpoint is `GET /api/equipment` with optional `name`, `category`, `description`, `labLocation`, `status`, optional `tag`, and `institutionId` query parameters. The frontend helper constructs only supported, non-empty filters. Tag matching is case-insensitive substring matching over existing equipment tags. No pagination parameter is supported. Equipment status is normalized and service-validated against the mentor-listed values; the PostgreSQL audit has not verified a database check constraint.

## Proposed wireframe: equipment catalog

This is a layout proposal derived from current inventory fields and API filters. It does not add pagination, endpoints, or a category service.

```text
┌──────────────────────────────────────────────────────────────────┐
│ Equipment catalog                                  [Add equipment]*│
├──────────────────────────────────────────────────────────────────┤
│ Name [____________] Category [________] Status [___________]      │
│ Location [________] Description [___________] Institution [____]  │
│ [Search] [Clear]                                                  │
├──────────────────────────────────────────────────────────────────┤
│ Name | Category | Status | Quantity | Location | Tags | Actions  │
│ ...                                                               │
├──────────────────────────────────────────────────────────────────┤
│ Selected item: specifications · calibration history · certificate│
│ history                                                           │
└──────────────────────────────────────────────────────────────────┘
* Mutation control shown only where current backend policy permits.
```

Recommended planning details: retain visible empty/loading/error states; show tags and specifications without treating them as search filters; expose record history from a selected equipment item; label document references as references. Final field labels and status values should follow the accepted requirement source, not this sketch.

## Proposed wireframe: core booking flow

The current API exposes an advisory `GET /api/bookings/availability` check. Booking creation repeats the capacity calculation under a pessimistic equipment-row lock, so the UI must still treat the submission response as authoritative. The calendar shows authorized booking records rather than free slots.

```text
Researcher view                              Staff view
┌──────────────────────────────┐             ┌─────────────────────────────┐
│ Choose equipment             │             │ Booking requests            │
│ Catalog filters + results    │             │ Pending request list        │
│ Select item                  │             │ Request details / equipment  │
├──────────────────────────────┤             │ [Approve] [Reject]           │
│ Request booking              │             │ Current status / history     │
│ Start [local date/time]      │             └─────────────────────────────┘
│ End   [local date/time]      │
│ Purpose [______________]     │
│ [Submit request]             │
├──────────────────────────────┤
│ My requests: PENDING /       │
│ APPROVED / REJECTED /        │
│ CANCELLED                    │
│ [Cancel] when permitted      │
└──────────────────────────────┘
```

The labels above reflect the current API states: `PENDING` is shown as Pending Approval and `APPROVED` as Confirmed. Staff lifecycle actions support `APPROVED → IN_USE/NO_SHOW/CANCELLED` and `IN_USE → COMPLETED`; rejected requests remain `REJECTED`.

## Traceability to the mentor evaluation criteria

The full T1?T17 task matrix is maintained in `docs/milestone-1-requirements-traceability.md`. The table below summarizes the four evaluation criteria and separates source, automated, and runtime evidence.

| Supplied evaluation criterion | Status | Source evidence | Automated-test evidence | Gaps and runtime verification |
|---|---|---|---|---|
| Project initialization and architecture setup completed | **PARTIAL** | React/Vite setup: `frontend/package.json`, `frontend/vite.config.js`; backend setup: `backend/lab-resource-platform/pom.xml`, `src/main/resources/application.properties`; modular packages; `docs/milestone-1-architecture-and-workflows.md`. | No dedicated architecture/deployment test. | Source design exists; target nine modules are not all implemented. PostgreSQL connectivity and schema are not runtime-verified. No separate gateway process or T6 integrations. |
| Authentication and role-based access control implemented | **PARTIAL** | `AuthController`, `JwtService`, `JwtAuthenticationFilter`, `SecurityConfig`, `GoogleOAuthSecurityConfig`, `ResourceAccessPolicy`, `RoleInitializer`; frontend `AuthContext`, `ProtectedRoute`, `roleAccess.js`, `OAuthCallback.jsx`, `Profile.jsx`. | Safe backend suite and frontend auth/role/OAuth tests pass. | Google and SMTP providers are not configured or runtime-tested. Server-side JWT revocation is absent. |
| Equipment inventory management and booking system functional | **PARTIAL** | Equipment CRUD/search/record endpoints; booking availability, create, decision, cancel, in-use, completion, and no-show actions; pages `Equipment.jsx`, `Bookings.jsx`, `BookingCalendar.jsx`. | Equipment/records/booking backend tests and frontend helper tests pass. | Persistent booking audit history is absent. Database-backed concurrency tests are skipped because Docker is unavailable. No live API test. |
| System design and UI planning completed | **PARTIAL** | Architecture, source ER, UI planning and full T1?T17 traceability documents. | No design-artifact/usability test. | Wireframes are textual proposals, not visual design-tool mockups; browser usability is unverified. |

### Verification snapshot

- `npm run test`: **46 passed, 0 failed**. Node tests cover auth/workflow helpers; they do not render React components or call a running backend.
- `npm run lint`: exit code 0 with three warnings in `ThemeContext.jsx`, `AuthContext.jsx`, and `MainLayout.jsx`. The dashboard and booking state-effect warnings were removed.
- Backend command `mvn -Dtest=*,!LabResourcePlatformApplicationTests test`: **89 passed, 0 failed, 2 skipped**. The skipped tests require a disposable PostgreSQL Testcontainers environment, but Docker is unavailable. The Spring context test was excluded because its default configuration points at `labdb` and has `ddl-auto=update`; no database was connected.
- `npm run build` and `mvnw package -DskipTests` succeeded. No application startup, HTTP call, browser session, live CORS preflight, or database operation occurred.

## Confirmed Milestone 1 scope vs later/unclear scope

The four mentor evaluation criteria and tracker T1?T17 apply to Milestone 1. The tracker specifically includes T6 infrastructure, T8/T14 Google OAuth2, T11 UI planning, T15 profile/protected UI, T16 equipment catalog, and T17 FullCalendar booking UI. Implementation status for each is listed in `docs/milestone-1-requirements-traceability.md`.

Tracker items in Milestones 2?4 include live utilization monitoring/analytics, inter-institution sharing workflows and external bookings, waitlists/recurring booking optimization, maintenance/calibration management, billing/invoices, notifications, reports/exports, deployment, and final integration. They were not implemented in this Milestone 1 pass.

## Runtime limitation

Route and page descriptions above are source-derived. They do not assert that PostgreSQL, external services, or browser API workflows were exercised. See the traceability matrix for task-specific evidence and unverified dependencies.
