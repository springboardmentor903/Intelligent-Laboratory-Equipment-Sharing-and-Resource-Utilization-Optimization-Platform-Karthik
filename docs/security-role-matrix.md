# Authorization policy for the current API

All protected routes require a valid bearer token. Roles are loaded from the
user's `user_roles` rows on every authenticated request. Resource access is
limited to the user's institution unless the role is `SYSTEM_ADMIN`. The
project has no department entity or department-to-institution mapping, so
`DEPARTMENT_HEAD` is institution-scoped for now; department-specific scope
cannot be enforced until that model exists.

| Role | Intended permissions implemented in this batch |
|---|---|
| `RESEARCHER_STUDENT` | Browse equipment in own institution; view only own booking, utilization, and reported maintenance records; create bookings; cancel own bookings; create utilization records for own user ID; report maintenance for own user ID. Cannot browse cross-user operational lists or administer equipment/workflows. |
| `LAB_TECHNICIAN` | Read institution-scoped bookings and operational records; create/update equipment; create calibration/certification records; create and update utilization/maintenance records; cannot approve/reject or delete bookings, or delete equipment/maintenance records. |
| `LAB_MANAGER` | Technician operational permissions plus approve/reject/cancel/delete institution-scoped bookings, delete equipment/maintenance records, assign maintenance, and update resolution details. |
| `DEPARTMENT_HEAD` | Read institution-scoped booking, utilization, and maintenance data. No department-only boundary is possible with the current schema. |
| `INSTITUTION_ADMIN` | Institution-scoped equipment and operational administration, including calibration/certification creation, booking status/delete, maintenance assignment/status/delete, and record updates. Institution creation is not included. |
| `SYSTEM_ADMIN` | Cross-institution resource access and operational administration; may create institutions. |

Public routes remain limited to login, password-reset request/confirmation,
registration, health, and GET role/institution catalogs. Other routes require
authentication. This policy does not add user/role administration endpoints.

## Institution membership approval

Public registration always creates a `RESEARCHER_STUDENT` account with
`membership_approved = false`; request input cannot select a role or approval
state. Login returns HTTP 403 with code `MEMBERSHIP_PENDING` and no JWT until
approval. JWT authentication assigns no authorities to a pending account, and
resource-scope checks independently reject pending accounts.

`GET /api/users/pending-membership` and
`PATCH /api/users/{userId}/membership/approve` are restricted to an approved,
database-assigned `INSTITUTION_ADMIN`. The target must belong to that
administrator's institution, must be pending, and cannot be the administrator
themself. Responses contain only the pending user's ID, email, institution ID,
and membership status; they do not serialize the `User` entity or password
hash.

The nullable approval column preserves existing accounts: `null` means
legacy-approved, and only newly registered accounts are explicitly set to
false. No existing user is migrated or revoked. The project has no application
endpoint for assigning the `INSTITUTION_ADMIN` role; administrator provisioning
must therefore come from an already trusted operator process. Availability and
trustworthiness of such an account were not verified because this change does
not inspect or connect to PostgreSQL.
