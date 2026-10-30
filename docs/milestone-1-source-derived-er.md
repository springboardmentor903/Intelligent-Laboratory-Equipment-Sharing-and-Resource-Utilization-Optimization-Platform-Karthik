# Milestone 1 Source-Derived Data Model

**Evidence date:** 2026-10-25. This is derived from Java entity annotations, not from a live database inspection or migration file.

## Relationship notation and database evidence

- **Solid connectors** represent explicit JPA associations (`@ManyToOne` or `@ElementCollection`/`@CollectionTable`).
- **Dotted connectors** represent scalar ID fields whose entity has no JPA association for that field.
- Neither connector proves the live PostgreSQL schema contains a foreign-key constraint. No database connection or schema inspection was performed.
- `PK`, `NN`, and `UQ` below mean source-declared primary key, non-null, and uniqueness. Actual deployed constraints remain unverified.

```mermaid
erDiagram
    INSTITUTION {
        Long id PK
        String name NN
    }
    DEPARTMENT {
        Long id PK
        Long institutionId NN "scalar reference"
        String name NN
        LocalDateTime createdAt NN
    }
    USER {
        Long id PK
        Long institutionId NN "scalar reference"
        Long departmentId "optional scalar reference"
        String email NN UQ
        String passwordHash NN
    }
    ROLE {
        Short id PK
        String name NN UQ
        String description
    }
    USER_ROLE {
        Long id PK
        Long userId NN "scalar reference"
        Short roleId NN "scalar reference"
    }
    EQUIPMENT {
        Long id PK
        String name NN
        String category
        String description
        Long institutionId NN "scalar reference"
        Long departmentId "optional scalar reference"
        String labLocation
        Integer quantity NN
        String status NN
        LocalDateTime createdAt NN
        Map specifications "JSONB"
    }
    EQUIPMENT_TAG {
        Long equipment_id "collection-table join"
        String tag NN
    }
    BOOKING {
        Long id PK
        Long equipmentId NN "scalar reference"
        Long userId NN "scalar reference"
        LocalDateTime startTime NN
        LocalDateTime endTime NN
        String purpose
        String status NN
        LocalDateTime createdAt NN
    }
    BOOKING_AUDIT_LOG {
        Long id PK
        Long bookingId NN "scalar reference"
        String eventType NN
        String previousStatus
        String newStatus NN
        String actorEmail NN
        LocalDateTime occurredAt NN
    }
    UTILIZATION_RECORD {
        Long id PK
        Long equipmentId NN "scalar reference"
        Long userId NN "scalar reference"
        Long bookingId "optional scalar reference"
        LocalDateTime startTime NN
        LocalDateTime endTime
        Double actualHours
        String notes
        LocalDateTime createdAt NN
    }
    MAINTENANCE_RECORD {
        Long id PK
        Long equipmentId NN "scalar reference"
        Long reportedBy NN "scalar reference"
        Long assignedTo "optional scalar reference"
        String description NN
        String status NN
        String priority NN
        LocalDateTime reportedAt NN
        LocalDateTime startedAt
        LocalDateTime completedAt
        Double downtimeHours
        String resolutionNotes
    }
    CALIBRATION_RECORD {
        Long id PK
        LocalDate calibrationDate NN
        LocalDate nextDueDate NN
        String result NN
        String notes
        LocalDateTime createdAt NN
    }
    CERTIFICATION_RECORD {
        Long id PK
        String certificateType NN
        String issuingOrganization NN
        LocalDate issueDate NN
        LocalDate expiryDate NN
        String documentReference
        LocalDateTime createdAt NN
    }
    PASSWORD_RESET_TOKEN {
        Long id PK
        Long userId NN "scalar reference"
        String tokenHash NN UQ
        Instant expiresAt NN
        Boolean used NN
    }

    INSTITUTION ||..o{ DEPARTMENT : "institutionId scalar"
    DEPARTMENT ||..o{ USER : "departmentId scalar"
    DEPARTMENT ||..o{ EQUIPMENT : "departmentId scalar"
    INSTITUTION ||..o{ USER : "institutionId scalar"
    INSTITUTION ||..o{ EQUIPMENT : "institutionId scalar"
    USER ||..o{ USER_ROLE : "userId scalar"
    ROLE ||..o{ USER_ROLE : "roleId scalar"
    USER ||..o{ BOOKING : "userId scalar"
    EQUIPMENT ||..o{ BOOKING : "equipmentId scalar"
    BOOKING ||..o{ BOOKING_AUDIT_LOG : "bookingId scalar"
    USER ||..o{ UTILIZATION_RECORD : "userId scalar"
    EQUIPMENT ||..o{ UTILIZATION_RECORD : "equipmentId scalar"
    BOOKING o|..o{ UTILIZATION_RECORD : "optional bookingId scalar"
    EQUIPMENT ||..o{ MAINTENANCE_RECORD : "equipmentId scalar"
    USER ||..o{ MAINTENANCE_RECORD : "reportedBy scalar"
    USER o|..o{ MAINTENANCE_RECORD : "assignedTo scalar"
    USER ||..o{ PASSWORD_RESET_TOKEN : "userId scalar"
    EQUIPMENT ||--o{ EQUIPMENT_TAG : "ElementCollection"
    EQUIPMENT ||--o{ CALIBRATION_RECORD : "ManyToOne"
    EQUIPMENT ||--o{ CERTIFICATION_RECORD : "ManyToOne"
```

Mermaid ER notation may be rendered as relationship lines; the solid/dotted distinction above is the source-level distinction. It is not a declaration that PostgreSQL constraints were observed.

## Source mapping details

| Java entity | Table | Source-declared key/constraints | Relationship implementation |
|---|---|---|---|
| `Institution` | `institutions` | Generated `Long id`; `name` non-null | Institution root entity. |
| `Department` | `departments` | Generated `Long id`; `institutionId`, `name`, `createdAt` non-null; unique `(institution_id, name)` | `institutionId` is scalar reference to institution. |
| `User` | `users` | Generated `Long id`; `institutionId` non-null; `departmentId` nullable; unique non-null email; non-null password hash | `institutionId` and `departmentId` are scalar. |
| `Role` | `roles` | Generated `Short id`; unique non-null name | No JPA collection relationship to users. |
| `UserRole` | `user_roles` | Generated `Long id`; unique `(user_id, role_id)`; both columns non-null | Both IDs are scalar. |
| `Equipment` | `equipment` | Generated `Long id`; name, institution ID, quantity, status, created time non-null; `departmentId` nullable; JSONB specifications | Institution ID and department ID are scalar. Tags use `@ElementCollection` in `equipment_tags`, with unique `(equipment_id, tag)`. |
| `Booking` | `bookings` | Generated `Long id`; equipment/user IDs, start/end, status, created time non-null | Equipment and user IDs are scalar. |
| `BookingAuditLog` | `booking_audit_logs` | Generated `Long id`; `bookingId`, `eventType`, `newStatus`, `actorEmail`, `occurredAt` non-null; `previousStatus` nullable | `bookingId` is scalar reference to booking. |
| `Utilization` | `utilization_records` | Generated `Long id`; equipment/user IDs, start time, created time non-null | Equipment, user, and optional booking IDs are scalar. |
| `Maintenance` | `maintenance_records` | Generated `Long id`; equipment/reporter IDs, description, status, priority, reported time non-null | Equipment, reporter, and optional assigned-user IDs are scalar. |
| `CalibrationRecord` | `calibration_records` | Generated `Long id`; calibration dates, result, created time non-null | `@ManyToOne(optional=false)` equipment via non-null `equipment_id`; JSON exposes a read-only derived `equipmentId`. |
| `CertificationRecord` | `certification_records` | Generated `Long id`; certificate type, issuer, issue/expiry dates, created time non-null | `@ManyToOne(optional=false)` equipment via non-null `equipment_id`; JSON exposes a read-only derived `equipmentId`. |
| `PasswordResetToken` | `password_reset_tokens` | Generated `Long id`; token hash unique/non-null; expiry and used flag non-null | `userId` is scalar. |

All source primary keys are generated identity values. No source-level enum/check constraint for equipment or booking status was found. Application services impose validations (e.g. equipment statuses, booking statuses, non-negative capacity, institution matching for departments). Existing schema values, foreign keys, and indexes are documented in the Flyway baseline migrations `V1__initial_schema.sql` and `V2__add_departments_and_booking_audit.sql`.

## Schema concepts status

- `Department` entity, department repository, service, and controller are implemented with `(institution_id, name)` uniqueness.
- `BookingAuditLog` entity, repository, and service tracking for booking creation, status transitions, and cancellations are implemented.
- Versioned Flyway migrations `V1__initial_schema.sql` and `V2__add_departments_and_booking_audit.sql` define the complete baseline schema and additive tables/columns/indexes.
- Waitlist, sharing agreement/access request, billing/invoice, and external notification entities belong to subsequent milestone scopes.

## Entity source files

- `backend/lab-resource-platform/src/main/java/com/labresource/platform/institution/entity/Institution.java`
- `backend/lab-resource-platform/src/main/java/com/labresource/platform/institution/entity/Department.java`
- `backend/lab-resource-platform/src/main/java/com/labresource/platform/user/entity/User.java`
- `backend/lab-resource-platform/src/main/java/com/labresource/platform/user/entity/Role.java`
- `backend/lab-resource-platform/src/main/java/com/labresource/platform/user/entity/UserRole.java`
- `backend/lab-resource-platform/src/main/java/com/labresource/platform/equipment/entity/Equipment.java`
- `backend/lab-resource-platform/src/main/java/com/labresource/platform/booking/entity/Booking.java`
- `backend/lab-resource-platform/src/main/java/com/labresource/platform/booking/entity/BookingAuditLog.java`
- `backend/lab-resource-platform/src/main/java/com/labresource/platform/utilization/entity/Utilization.java`
- `backend/lab-resource-platform/src/main/java/com/labresource/platform/maintenance/entity/Maintenance.java`
- `backend/lab-resource-platform/src/main/java/com/labresource/platform/calibration/entity/CalibrationRecord.java`
- `backend/lab-resource-platform/src/main/java/com/labresource/platform/certification/entity/CertificationRecord.java`
- `backend/lab-resource-platform/src/main/java/com/labresource/platform/auth/entity/PasswordResetToken.java`
- `backend/lab-resource-platform/src/main/java/com/labresource/platform/maintenance/entity/Maintenance.java`
- `backend/lab-resource-platform/src/main/java/com/labresource/platform/calibration/entity/CalibrationRecord.java`
- `backend/lab-resource-platform/src/main/java/com/labresource/platform/certification/entity/CertificationRecord.java`
- `backend/lab-resource-platform/src/main/java/com/labresource/platform/auth/entity/PasswordResetToken.java`
