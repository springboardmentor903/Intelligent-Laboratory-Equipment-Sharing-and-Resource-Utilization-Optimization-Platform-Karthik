# Intelligent Laboratory Equipment Sharing and Resource Utilization Optimization Platform

A modern web platform for centralized laboratory equipment management, cross-department sharing, reservation scheduling with peak-concurrency capacity controls, institution onboarding, role-based workflows, and maintenance/utilization tracking.

---

## Overview

This repository contains the full-stack implementation of the **Laboratory Equipment Sharing and Resource Utilization Optimization Platform**, featuring:

1. **Role-Based Access Control (RBAC):** Strict separation of platform-level and institution-level responsibilities across six distinct roles:
   - `SYSTEM_ADMIN`: Platform-wide governance, institution registration approvals, platform directory oversight, and global administrative access (without institution or department affiliations).
   - `INSTITUTION_ADMIN`: Institutional department management, user membership approvals, and institution-level equipment and booking oversight.
   - `LAB_MANAGER`: Equipment inventory management, booking approvals, and lab utilization optimization.
   - `DEPARTMENT_HEAD`: Departmental equipment oversight, lab reservation monitoring, and department utilization analytics.
   - `LAB_TECHNICIAN`: Equipment calibration, maintenance operations, and certification tracking.
   - `RESEARCHER_STUDENT`: Equipment catalog exploration, reservation requests, and personal booking history.
2. **Institution Onboarding & Membership Verification:**
   - Public institution registration workflow awaiting platform System Administrator approval.
   - Institution-scoped user registration requiring approval from the Institution Administrator before access is activated.
3. **Department Management:**
   - Scoped department creation, listing, and updates with case-insensitive unique naming within institutions.
   - User department affiliation updates with draft state and explicit save confirmation.
4. **Equipment Inventory & Smart Search:**
   - Multi-parameter search by name, category, status, department, location, and tags.
   - JSON specifications, calibration tracking, and certification lifecycle records.
   - Responsive card layout with consistent action buttons and status badges.
5. **Booking & Peak-Concurrency Capacity:**
   - Sweep-line interval concurrency algorithm over half-open intervals `[S, E)` with end events prioritized before start events at identical timestamps.
   - Pessimistic row locking (`PESSIMISTIC_WRITE`) guarantees capacity enforcement for multi-quantity equipment under concurrent access.
6. **Booking Audit Trail:**
   - Immutable audit logging (`BookingAuditLog`) tracking transitions (`PENDING → APPROVED`, `APPROVED → IN_USE`, `IN_USE → COMPLETED`, `CANCELLED`).
   - Clean UTF-8 status transition rendering and actor attribution.
7. **Modern User Experience:**
   - Role-adaptive authenticated Home dashboard with role guidance and quick-access cards.
   - Client-side and server-side validation with user-friendly error messages (never exposing raw HTTP status codes or stack traces).
   - Light and dark SaaS theme support with accessible contrast and responsive design.
8. **Database Versioning:**
   - Flyway migration baseline (`V1` through `V5`) guaranteeing repeatable schema setup.

---

## Technology Stack

### Backend
- **Language & Runtime:** Java 21 (LTS) / OpenJDK
- **Framework:** Spring Boot 4.1.1
- **Security:** Spring Security with stateless JWT authentication, BCrypt password hashing, and role-based authorization (`@PreAuthorize`)
- **Data & Persistence:** Spring Data JPA / Hibernate, Flyway database migrations
- **Search Integration:** Spring Data Elasticsearch (opt-in with automatic relational fallback)
- **Database:** PostgreSQL (production database `labdb`)
- **Build Tool:** Apache Maven

### Frontend
- **Library:** React 19
- **Build Tool:** Vite
- **Styling:** CSS variables, Tailwind CSS design tokens
- **Routing:** React Router v7
- **HTTP Client:** Axios with centralized authentication interceptors and error normalization
- **Calendar:** FullCalendar React

---

## Project Structure

```text
lab-resource-platform/
├── backend/
│   └── lab-resource-platform/
│       ├── src/main/java/com/labresource/platform/
│       │   ├── auth/              # JWT, login, registration, OAuth2 code exchange, password reset
│       │   ├── user/              # User entity, profile endpoints, role mapping, approval workflows
│       │   ├── institution/       # Institution and Department entities, controllers, services
│       │   ├── equipment/         # Equipment entity, inventory management, search filters
│       │   ├── booking/           # Booking entity, sweep-line capacity, audit trail, controller
│       │   ├── calibration/       # Calibration records and scheduling
│       │   ├── certification/     # Certification tracking
│       │   ├── maintenance/       # Maintenance requests and work order lifecycle
│       │   ├── utilization/       # Equipment usage logging and metrics
│       │   └── common/            # SecurityConfig, rate limiter, request logging, CORS
│       ├── src/main/resources/
│       │   ├── application.properties
│       │   ├── application-google-oauth.properties
│       │   ├── application-mail.properties
│       │   └── db/migration/      # Flyway versioned migrations (V1 to V5)
│       └── pom.xml
├── frontend/
│   ├── src/
│   │   ├── access/                # Workflow helpers, role rules, validation helpers
│   │   ├── api/                   # Axios configuration and error handlers
│   │   ├── components/            # Navbar, ProtectedRoute, shared UI components
│   │   ├── context/               # AuthContext and ThemeContext session management
│   │   └── pages/                 # Catalog, Bookings, Calendar, Profile, Login, Register, Home
│   ├── test/                      # Frontend unit and workflow tests
│   ├── package.json
│   └── vite.config.js
├── docs/                          # Architecture, traceability matrix, and ER documents
├── infra/                         # Docker Compose local service templates
├── .gitignore
└── README.md
```

---

## Database Migrations (Flyway)

The database schema is managed via Flyway migrations located in `backend/lab-resource-platform/src/main/resources/db/migration/`:

- `V1__initial_schema.sql`: Core schema for users, roles, institutions, equipment, bookings, maintenance, and utilization.
- `V2__add_departments_and_booking_audit.sql`: Department entity and immutable booking audit log table.
- `V3__add_institution_and_user_approval_fields.sql`: Institution approval status and user membership approval workflows.
- `V4__add_audit_and_approval_metadata.sql`: Institution review audit timestamps and decision actor metadata.
- `V5__make_user_institution_nullable.sql`: Nullable institutional foreign keys for platform-level `SYSTEM_ADMIN` separation.

---

## Local Setup & Startup Instructions

### 1. Prerequisites
- **Java JDK 21+**
- **Node.js 18+ & npm**
- **PostgreSQL 14+** running locally with database `labdb`

### 2. Environment Variables
Configure the required secrets in your local shell or environment (never commit real credentials):

```bash
# Backend environment variables
export DB_PASSWORD="your-postgres-password"
export JWT_SECRET="your-256-bit-hmac-secret-base64-encoded"

# Optional integrations
export GOOGLE_OAUTH_ENABLED="false"
export MAIL_ENABLED="false"
export ELASTICSEARCH_ENABLED="false"
```

On Windows (PowerShell):
```powershell
$env:DB_PASSWORD="your-postgres-password"
$env:JWT_SECRET="your-256-bit-hmac-secret-base64-encoded"
```

### 3. Backend Startup
Navigate to the backend directory and launch Spring Boot:
```bash
cd backend/lab-resource-platform
mvn spring-boot:run
```
The backend API starts at `http://localhost:8080`.

### 4. Frontend Startup
In a separate terminal, install dependencies and start the Vite dev server:
```bash
cd frontend
npm install
npm run dev
```
The frontend application will be accessible at `http://localhost:5173`.

---

## Verification & Testing

### Backend Test Suite
Run all backend integration and unit tests:
```bash
cd backend/lab-resource-platform
mvn test
```
- **Results:** 122 tests run, 0 failures, 0 errors, 100% pass rate.
- Includes sweep-line interval capacity tests, membership authorization tests, department uniqueness checks, and role access policies.

### Frontend Test Suite & Build
Run automated frontend tests, linting, and production build:
```bash
cd frontend
npm test
npm run lint
npm run build
```
- **Node Test Runner:** 77 tests run, 0 failures, 100% pass rate.
- **Linter:** 0 errors.
- **Production Build:** Vite production build succeeds with zero errors.

---

## Security & Compliance Notice

- All secrets and credentials must be supplied via environment variables (`DB_PASSWORD`, `JWT_SECRET`, `GOOGLE_CLIENT_SECRET`, `MAIL_PASSWORD`).
- Raw HTTP error codes and exception traces are masked from client responses.
- User email existence is never leaked via authentication or password reset endpoints.
- Database backups, local environment files (`.env*`), and sensitive logs are excluded from version control via `.gitignore`.
