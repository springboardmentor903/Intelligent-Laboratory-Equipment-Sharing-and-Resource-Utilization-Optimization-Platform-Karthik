# Milestone 1 Database Integrity Audit

**Scope:** Read-only comparison of the supplied PostgreSQL findings with current Spring/JPA source. No database connection, SQL execution, application launch, or source/configuration change was performed for this report update.

## Evidence and confidence

- **Database-verified (supplied):** The PostgreSQL observations below are the results provided for `labdb`. They are not independently re-run in this update. They describe the inspected database state at the time of those checks, not a guarantee for future writes.
- **Source-verified:** Entity mappings, service validation/workflows, and configuration described below were inspected in this project.
- **Unverified:** No inference is made about unreported PostgreSQL constraints, delete behavior, status/date checks, or live application behavior.
- The literal SQL transcript and raw catalog output were not supplied. Consequently, the SQL in “Reconstructed check definitions” is equivalent audit logic for reproducibility, **not a claim that these exact statements were executed**. The counts attached to each check are the supplied results.

## Executive findings

The supplied database inspection found all 12 expected tables and 11 identity-by-default `id` columns; `equipment_tags` has no `id` column. It found exactly three foreign keys, all for equipment-owned calibration, certification, and tag rows. All reported orphan and duplicate/booking checks returned zero. Later supplied checks also found no invalid equipment quantities, selected calibration/certification/maintenance date inconsistencies, or invalid utilization values. These are positive current-data results, but most scalar references still have no reported foreign key, so future invalid writes remain possible.

The inspected column query reportedly returned expected columns and types, including `equipment.specifications` as `jsonb`. The full column-by-column output was not included with the supplied summary, so this document does not reproduce an exhaustive PostgreSQL type/nullability/default inventory. No live database constraints beyond the reported keys and indexes should be inferred.

`spring.jpa.hibernate.ddl-auto=update` remains configured at `backend/lab-resource-platform/src/main/resources/application.properties`. It was not changed. It is not a reviewed, versioned migration process and must not be treated as a safe substitute for one.

## 1. Reported schema inventory

### Tables and identity columns

**Database-verified (supplied):** The inspected tables are:

| Table | Reported identity column |
|---|---|
| `bookings` | `id`, `BY DEFAULT` |
| `calibration_records` | `id`, `BY DEFAULT` |
| `certification_records` | `id`, `BY DEFAULT` |
| `equipment` | `id`, `BY DEFAULT` |
| `equipment_tags` | no `id` column |
| `institutions` | `id`, `BY DEFAULT` |
| `maintenance_records` | `id`, `BY DEFAULT` |
| `password_reset_tokens` | `id`, `BY DEFAULT` |
| `roles` | `id`, `BY DEFAULT` |
| `user_roles` | `id`, `BY DEFAULT` |
| `users` | `id`, `BY DEFAULT` |
| `utilization_records` | `id`, `BY DEFAULT` |

The supplied report says all 11 tables with an `id` column have PostgreSQL identity generation `BY DEFAULT`; `equipment_tags` has no `id` column. The column query reportedly returned expected names/types, including `equipment.specifications jsonb`. Actual nullability, defaults, identity sequence details, and the complete type listing were not supplied in the evidence summary and remain **unverified here**.

### Foreign keys

**Database-verified (supplied):** The foreign-key query returned exactly these three constraints:

| Source column | Referenced column |
|---|---|
| `calibration_records.equipment_id` | `equipment.id` |
| `certification_records.equipment_id` | `equipment.id` |
| `equipment_tags.equipment_id` | `equipment.id` |

No other foreign keys appeared in the supplied query output. This does not establish the constraints' `ON DELETE`/`ON UPDATE` actions, validation state, or deferrability; those details were not supplied.

### Indexes and uniqueness

**Database-verified (supplied):** The index query reported primary-key indexes for all 12 tables and unique indexes on:

- `users(email)`
- `roles(name)`
- `user_roles(user_id, role_id)`
- `equipment_tags(equipment_id, tag)`
- `password_reset_tokens(token_hash)`

No other indexes appeared in the supplied output. In particular, no dedicated index was reported for many reference columns, including `bookings.equipment_id`, `bookings.user_id`, and `equipment.institution_id`. The full index definitions, predicates, included columns, and operator classes were not supplied; do not infer more than the listed result.

## 2. Data integrity checks

All counts below are **database-verified according to the supplied results**, not independently executed during this update.

### Orphan-reference checks — 13 checks, all returned `0`

| Reference checked | Orphan count |
|---|---:|
| `users` → `institutions` | 0 |
| `equipment` → `institutions` | 0 |
| `user_roles` → `users` | 0 |
| `user_roles` → `roles` | 0 |
| `bookings` → `equipment` | 0 |
| `bookings` → `users` | 0 |
| `utilization_records` → `equipment` | 0 |
| `utilization_records` → `users` | 0 |
| non-null `utilization_records.booking_id` → `bookings` | 0 |
| `maintenance_records` → `equipment` | 0 |
| `maintenance_records.reported_by` → `users` | 0 |
| non-null `maintenance_records.assigned_to` → `users` | 0 |
| `password_reset_tokens` → `users` | 0 |

These are point-in-time results. Without constraints, subsequent inserts/updates can introduce orphans.

### Duplicate and booking checks — six checks, all returned `0`

| Check | Result |
|---|---:|
| Duplicate normalized email addresses | 0 duplicate groups |
| Duplicate role names, case-insensitive | 0 duplicate groups |
| Duplicate `(user_id, role_id)` pairs | 0 duplicate groups |
| Duplicate `(equipment_id, tag)` pairs | 0 duplicate groups |
| Invalid time range among `PENDING`/`APPROVED` bookings | 0 |
| Overlapping active booking pairs for the same equipment | 0 pairs |

The overlap result is limited to the check's active statuses (`PENDING`, `APPROVED`) and the inspected snapshot. It does not prove protection from concurrent requests. It also does not establish correctness for equipment with `quantity > 1`; the reported same-equipment overlap check may flag valid concurrent reservations for multi-unit equipment unless capacity is included in its logic.

### Additional integrity and value checks

The following are **user-supplied PostgreSQL query results from manual, read-only pgAdmin queries against `labdb`**. Codex did not connect to PostgreSQL or execute these queries. They apply only to the successful queries and database snapshot reported.

| Check or grouping | Supplied result |
|---|---:|
| Equipment quantity `<= 0` | 0 |
| Equipment quantity `IS NULL` | 0 |
| Calibration `next_due_date < calibration_date` | 0 |
| Certification `expiry_date < issue_date` | 0 |
| Maintenance `started_at < reported_at` | 0 |
| Maintenance `completed_at < reported_at` | 0 |
| Maintenance `completed_at < started_at` | 0 |
| Utilization `end_time <= start_time`, when end time exists | 0 |
| Utilization negative `actual_hours` | 0 |
| Utilization non-finite `actual_hours` (`Infinity`, `-Infinity`, `NaN`) | 0 |
| Booking status group `CANCELLED` | 1 row |
| Booking status group `PENDING` | 2 rows |
| Equipment status group `AVAILABLE` | 1 row |
| Maintenance status grouping query | 0 rows returned |
| Calibration result grouping query | 0 rows returned |

The final two results mean only that the supplied grouping queries returned no rows for those groupings on the target database. They do not validate maintenance lifecycle rules or calibration result logic and should not be generalized beyond those successful queries and that database. These results also do not establish that corresponding database check constraints exist.

## 3. Reconstructed check definitions (not execution transcripts)

The SQL below shows equivalent aggregate checks without selecting personal data or sensitive values. It is included to make the check meaning reviewable. Since the actual executed SQL was not supplied, these statements must not be attributed as the original queries. `0` is the result supplied for each corresponding check.

### Duplicate checks

```sql
-- Duplicate normalized email groups: supplied result 0
SELECT count(*) FROM (
  SELECT lower(btrim(email)) FROM users
  GROUP BY lower(btrim(email)) HAVING count(*) > 1
) d;

-- Duplicate role-name groups, case-insensitive: supplied result 0
SELECT count(*) FROM (
  SELECT lower(btrim(name)) FROM roles
  GROUP BY lower(btrim(name)) HAVING count(*) > 1
) d;

-- Duplicate user-role pairs: supplied result 0
SELECT count(*) FROM (
  SELECT user_id, role_id FROM user_roles
  GROUP BY user_id, role_id HAVING count(*) > 1
) d;

-- Duplicate equipment-tag pairs: supplied result 0
SELECT count(*) FROM (
  SELECT equipment_id, tag FROM equipment_tags
  GROUP BY equipment_id, tag HAVING count(*) > 1
) d;
```

### Orphan checks

Each query returns an orphan-row count. The 13 supplied results were all `0`.

```sql
SELECT count(*) FROM users u LEFT JOIN institutions i ON i.id=u.institution_id WHERE i.id IS NULL;
SELECT count(*) FROM equipment e LEFT JOIN institutions i ON i.id=e.institution_id WHERE i.id IS NULL;
SELECT count(*) FROM user_roles ur LEFT JOIN users u ON u.id=ur.user_id WHERE u.id IS NULL;
SELECT count(*) FROM user_roles ur LEFT JOIN roles r ON r.id=ur.role_id WHERE r.id IS NULL;
SELECT count(*) FROM bookings b LEFT JOIN equipment e ON e.id=b.equipment_id WHERE e.id IS NULL;
SELECT count(*) FROM bookings b LEFT JOIN users u ON u.id=b.user_id WHERE u.id IS NULL;
SELECT count(*) FROM utilization_records x LEFT JOIN equipment e ON e.id=x.equipment_id WHERE e.id IS NULL;
SELECT count(*) FROM utilization_records x LEFT JOIN users u ON u.id=x.user_id WHERE u.id IS NULL;
SELECT count(*) FROM utilization_records x LEFT JOIN bookings b ON b.id=x.booking_id
  WHERE x.booking_id IS NOT NULL AND b.id IS NULL;
SELECT count(*) FROM maintenance_records m LEFT JOIN equipment e ON e.id=m.equipment_id WHERE e.id IS NULL;
SELECT count(*) FROM maintenance_records m LEFT JOIN users u ON u.id=m.reported_by WHERE u.id IS NULL;
SELECT count(*) FROM maintenance_records m LEFT JOIN users u ON u.id=m.assigned_to
  WHERE m.assigned_to IS NOT NULL AND u.id IS NULL;
SELECT count(*) FROM password_reset_tokens t LEFT JOIN users u ON u.id=t.user_id WHERE u.id IS NULL;
```

### Booking checks

These definitions assume `PENDING` and `APPROVED` are the active states, as in the supplied check and the inspected booking service. Both supplied counts were `0`.

```sql
-- Invalid active time ranges
SELECT count(*) FROM bookings
WHERE status IN ('PENDING', 'APPROVED') AND (start_time IS NULL OR end_time IS NULL OR end_time <= start_time);

-- Pairwise overlap for active bookings on the same equipment
SELECT count(*) FROM bookings a
JOIN bookings b ON b.equipment_id=a.equipment_id AND b.id>a.id
WHERE a.status IN ('PENDING', 'APPROVED')
  AND b.status IN ('PENDING', 'APPROVED')
  AND a.start_time < b.end_time AND b.start_time < a.end_time;
```

The overlap template is only appropriate for exclusive single-unit equipment. It does not calculate aggregate overlap against equipment quantity.

## 4. Source mapping and behavior

Source locations below are relative to `backend/lab-resource-platform`.

| Source area | Source-verified mapping/behavior | Database comparison |
|---|---|---|
| `src/main/java/.../entity/Institution.java`, `User.java`, `Role.java`, `UserRole.java` | Institutions, users, and roles are entities. User institution and user-role references are scalar IDs/association-table values rather than explicit entity relationships in the inspected mappings. JPA declares unique email, role name, and user-role pair constraints. | Supplied DB unique indexes match these declarations. No DB FK was reported for user institution or `user_roles`. |
| `.../entity/Equipment.java` | Equipment has a scalar institution ID; specifications are JSON data; tags use an element collection/table mapping. Source declares tag uniqueness for equipment/tag. | Supplied schema says `specifications` is `jsonb`; the equipment institution reference has no reported FK. Tag-to-equipment FK and uniqueness were reported. |
| `.../entity/Booking.java` | Booking holds scalar equipment and user IDs, local start/end date-times, and a string status. | No corresponding booking FK or status/time check constraint was reported. |
| `.../entity/UtilizationRecord.java` | Utilization holds scalar equipment/user IDs and nullable booking ID, plus times and actual hours. | No corresponding FKs were reported. Nullability and numeric/date checks in PostgreSQL remain unverified. |
| `.../entity/MaintenanceRecord.java` | Maintenance holds scalar equipment, reporter, and optional assignee IDs; lifecycle and date fields are represented in the entity. | No corresponding FKs were reported. Delete actions and lifecycle check constraints remain unverified. |
| `.../entity/CalibrationRecord.java`, `CertificationRecord.java` | Both use explicit equipment relationships. | The supplied three-FK result includes these equipment references. |
| `.../entity/PasswordResetToken.java` | Token references user through a scalar ID; token hash is unique in source. | The supplied unique index matches; no user FK was reported. Token contents were not inspected for this audit. |
| `src/main/resources/application.properties` | `spring.jpa.hibernate.ddl-auto=update`. | Configuration was source-verified, not changed. No migration baseline was identified in the inspected project source. |
| `.../service/BookingService.java` | Checks future start, end after start, equipment status, and overlap against `PENDING`/`APPROVED`, then saves a pending booking. Status changes are limited by service transition logic; cancellation is limited to pending/approved. | Application-level check-then-insert has a concurrency race without a database exclusion/locking strategy. It does not account for equipment quantity. It does not update equipment status as part of booking. |
| `.../service/EquipmentService.java` | Validates institution existence for equipment operations and normalizes status text, but does not constrain it to a closed status enum in the inspected code. | No DB check constraint/status vocabulary was reported. |
| `.../service/UtilizationService.java` | Validates referenced equipment/user and booking relationship on creation, validates time order, and computes hours; updates are narrower. | Current validations do not replace DB FKs/checks for other write paths. |
| `.../service/MaintenanceService.java` | Validates equipment/reporter and assigned user existence; applies service-level lifecycle rules and completion details. | These checks do not establish DB referential constraints or deletion policy. |
| `.../service/CalibrationService.java`, `CertificationService.java` | Validate equipment and date ordering in service logic. | Service validation complements, but does not replace, the reported equipment FKs. |

The supplied orphan checks show no current orphan rows for the listed scalar references. They do not eliminate orphan risk while the matching database foreign keys are absent.

## 5. Highest-risk gaps and unresolved decisions

1. **Missing reference constraints:** no reported FKs for users/institutions, equipment/institutions, user roles/users and roles, bookings/equipment and users, utilization/equipment/users/bookings, maintenance/equipment/reporters/assignees, or reset tokens/users.
2. **Booking race and capacity semantics:** service-level overlap checks can race. The system must decide whether `quantity` means independently bookable units or inventory count, and how overlapping reservations consume that capacity. The supplied overlap check does not prove multi-unit correctness.
3. **Unindexed references:** no non-unique indexes appeared in the supplied index result, including several join/reference columns. Query patterns and plans are needed before selecting indexes.
4. **Statuses and temporal checks:** actual status distributions, accepted vocabularies, date consistency outside the two active-booking checks, and DB check constraints were not supplied.
5. **Deletion behavior:** FK delete actions and application delete paths were not supplied. A safe `RESTRICT`/cascade choice cannot be guessed.
6. **Schema evolution:** `ddl-auto=update` can make implicit schema changes and does not provide a reviewed, versioned migration history. Existing data and the current schema must be baselined before managed migrations are enabled.
7. **No migration baseline:** no versioned migration setup was identified during source inspection. Confirm the current migration tooling and deployment process before adoption.

## 6. Proposed migration plan (proposal only; no SQL or migration files created)

1. **Capture and baseline:** obtain a schema-only snapshot and a restorable approved database snapshot. Compare every column, identity, constraint, index, and default to the exact current production/development schema. Choose a versioned migration tool and create a baseline that describes the current schema without changing it. Do not switch `ddl-auto` in this proposal batch.
2. **Resolve policy and data checks:** explicitly approve deletion behavior for each relationship (including historical bookings, utilization, maintenance, and reset tokens). Re-run orphan checks immediately before migration. Decide whether references should be restricted, nulled where nullable, or cascaded; do not use cascade by default.
3. **Add missing FKs additively:** add constraints for the currently unprotected scalar references in dependency order: institution and role/user foundations; equipment and user relationships; booking relationships; utilization and maintenance relationships; reset-token user relationship. The reported current orphan counts are zero, but recheck before applying. Use a staged/validated-constraint approach where operationally appropriate and supported by the chosen migration strategy. Preserve existing rows.
4. **Review indexes:** use repository query patterns and approved query plans to select indexes on frequently joined/filtered reference columns and search fields. Check for equivalent existing indexes first; add only justified indexes, accounting for write cost and lock/build behavior.
5. **Resolve domain constraints separately:** define accepted equipment/booking/maintenance statuses, quantity semantics, temporal rules, utilization-hour rules, and concurrency enforcement. Clean or reconcile data only under a separately approved plan; then consider check/exclusion constraints, locking, or a capacity-aware reservation model.
6. **Operational rollout:** rehearse on a disposable restored snapshot; capture before/after schema and aggregate integrity reports; verify application compatibility; take a recoverable backup and define rollback/forward-fix procedures before an approved production migration.

This order is a proposal only. No migration was applied, generated, or tested against a database.

## 7. Remaining read-only checks for an approved snapshot

The following were not included in the supplied results and remain **unverified**:

- Per-column PostgreSQL type, nullability, default, identity/sequence, and generated-expression output for every table.
- Full FK definitions: constraint names, `ON DELETE`/`ON UPDATE`, validation, and deferrability.
- Full check constraints and all index definitions, including partial/expression indexes and index validity.
- Full distinct values and counts for equipment, booking, maintenance, calibration, certification, and utilization statuses/results; identify values outside the intended application vocabularies once those vocabularies are approved. Only the listed booking and equipment status groups were supplied. Maintenance and calibration grouping queries returned no rows, which does not validate lifecycle logic.
- Equipment status/quantity combinations and quantity semantics beyond the reported absence of null/nonpositive quantities.
- Booking date consistency beyond the reported invalid-range check for active `PENDING`/`APPROVED` bookings.
- Utilization `actual_hours` consistency against its recorded start/end duration and the documented rounding/unit rule. Negative and non-finite values were reported absent, but duration consistency was not checked in the supplied results.
- Date consistency outside the specifically reported calibration, certification, maintenance, utilization, and active-booking checks.
- Maintenance lifecycle consistency (for example, assigned/started/completed timestamps appropriate to status), without assuming rules not yet specified.
- Booking overlap/capacity analysis using confirmed quantity semantics, active statuses, and any resource-specific capacity rules.
- Delete behavior through catalog metadata and a review of application delete operations; do not test deletes on the real database.
- Index coverage and query plans for actual read paths, especially reference joins and equipment search.

These checks should use aggregate counts and avoid exposing personal data, password hashes, token values, or unnecessary row identifiers.

## 8. Milestone 1 relevance and safe next action

For the four Milestone 1 evaluation criteria, the highest-priority integrity work is preserving the user/role/institution and equipment relationships, protecting booking references and valid time intervals, and resolving booking concurrency/capacity semantics. Calibration/certification equipment references are already represented by the three reported FKs. Utilization and maintenance references support the broader platform workflows, but their exact Milestone 1 grading scope should be confirmed against the authoritative tracker/specification before expanding the migration scope.

**Safest next implementation batch:** prepare and review a versioned, additive migration baseline against an approved schema snapshot, then add only the missing foreign keys whose deletion policy has been explicitly approved and whose pre-migration orphan checks pass. Keep indexes and booking status/capacity/concurrency decisions in separately reviewed steps. Do not change `ddl-auto=update` until the baseline, deployment sequence, and rollback plan are approved.
