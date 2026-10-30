# Milestone 1 additive migration design

## Scope and execution boundary

This design addresses the explicit M1 tracker requirements for departments (T2), equipment/institution/departments schema (T3), and booking history/audit trail (T10). The corresponding SQL is [V001__milestone_1_departments_and_booking_audit.sql](../database/migration-proposals/V001__milestone_1_departments_and_booking_audit.sql).

It is a proposal only. It is deliberately outside `src/main/resources/db/migration`, there is no Flyway or Liquibase dependency, and no application setting enables it. It has not been applied to `labdb`, a disposable database, or any database.

## Confirmed source and database assumptions

- `users.institution_id` and `equipment.institution_id` are scalar identifiers in current source.
- The supplied `labdb` audit reported zero current orphans for users/equipment to institutions, but reported no foreign keys for those references.
- Current users and equipment have no department column, so the proposed nullable `department_id` fields preserve existing rows.
- Existing bookings are hard-deletable through `DELETE /api/bookings/{id}`.
- The migration proposal does not change `spring.jpa.hibernate.ddl-auto=update`.

## Department model decisions

The tracker explicitly requires departments and relationships but does not prescribe department attributes. The smallest non-speculative model is therefore:

| Field | Reason |
|---|---|
| `id` | Primary identifier consistent with existing tables. |
| `institution_id` | Departments belong to an institution. |
| `name` | Required human-readable department identity. |
| `created_at` | Supports basic traceability without inventing workflow fields. |

`UNIQUE (institution_id, name)` prevents duplicate department names inside one institution while allowing independently named departments in different institutions. Existing users/equipment retain null `department_id` until an authorized data-enrichment operation is defined.

The intended delete behavior is explicit but unexecuted:

- Institution deletion is `RESTRICT` while departments exist.
- Department deletion is `SET NULL` for users/equipment, preserving those records.

The current source has no protected department administration API. Adding a JPA `Department` entity now would let Hibernate `ddl-auto=update` alter `labdb` on an ordinary application start. That is intentionally deferred until this migration is approved, rehearsed on a disposable restored snapshot, and a department authorization contract is implemented.

## Booking-audit policy blocker

T10 requires a history/audit trail. A `booking_events` table needs a foreign key to preserve referential integrity. The current hard delete endpoint creates an unresolved choice:

1. `ON DELETE RESTRICT` preserves audit history but requires replacing hard deletes with a retention/soft-delete policy.
2. `ON DELETE CASCADE` retains hard delete behavior but removes the very audit history T10 requires.

The tracker and mentor PDF do not resolve that retention policy. The event-table SQL is consequently commented out, preventing accidental execution. No JPA audit entity or recorder is registered, so current booking behavior remains intact.

## Required preflight and rollout

1. Obtain an approved schema-only snapshot and a restorable copy of the target database.
2. Re-run the supplied orphan/duplicate checks and add checks for duplicate department names before migration.
3. Choose a migration tool and baseline the pre-existing schema without changing it.
4. Apply the department section to a disposable PostgreSQL database restored from the approved snapshot.
5. Verify nullable columns, unique constraint, foreign keys, indexes, and existing-row counts.
6. Implement and test department administration and scoping APIs against that disposable database.
7. Resolve the booking retention policy, then enable the booking-event section in a separate reviewed migration.

## Rollback/recovery

For an approved rollout, capture a recoverable schema/data backup before migration. If the department migration must be reversed before data assignment, remove its new indexes and constraints before columns/table. Once department assignments exist, use a forward corrective migration or restore the approved snapshot; do not drop populated columns as an operational rollback shortcut.
