export const SUPPORTED_ROLES = Object.freeze([
  'RESEARCHER_STUDENT',
  'LAB_TECHNICIAN',
  'LAB_MANAGER',
  'DEPARTMENT_HEAD',
  'INSTITUTION_ADMIN',
  'SYSTEM_ADMIN',
])

export const BOOKING_LIST_ROLES = Object.freeze([
  'LAB_TECHNICIAN',
  'LAB_MANAGER',
  'DEPARTMENT_HEAD',
  'INSTITUTION_ADMIN',
  'SYSTEM_ADMIN',
])

export const MAINTENANCE_SUMMARY_ROLES = Object.freeze([
  'LAB_TECHNICIAN',
  'LAB_MANAGER',
  'DEPARTMENT_HEAD',
  'INSTITUTION_ADMIN',
  'SYSTEM_ADMIN',
])

const COMMON_NAVIGATION = Object.freeze([
  { label: 'Dashboard', to: '/dashboard' },
  { label: 'Equipment', to: '/equipment' },
  { label: 'Bookings', to: '/bookings' },
  { label: 'Booking calendar', to: '/booking-calendar' },
  { label: 'Utilization', to: '/utilization' },
  { label: 'Maintenance', to: '/maintenance' },
  { label: 'Profile', to: '/profile' },
])

// Each role has a permitted view for these existing routes. The backend
// still applies institution and ownership scope to the returned records.
export const ROLE_NAVIGATION = Object.freeze(Object.fromEntries(
  SUPPORTED_ROLES.map((role) => [role, COMMON_NAVIGATION]),
))

export function hasValidRoles(roles) {
  return Array.isArray(roles)
    && roles.length > 0
    && roles.every((role) => typeof role === 'string' && SUPPORTED_ROLES.includes(role))
}

export function hasAnyRole(roles, allowedRoles) {
  return hasValidRoles(roles)
    && roles.some((role) => allowedRoles.includes(role))
}

export function getNavigationItems(roles) {
  if (!hasValidRoles(roles)) return []
  const visibleRoutes = new Set(
    roles.flatMap((role) => ROLE_NAVIGATION[role].map((item) => item.to)),
  )
  return COMMON_NAVIGATION.filter((item) => visibleRoutes.has(item.to))
}

export function getBookingRecordsUrl(roles, userId) {
  if (!hasValidRoles(roles)) return null
  if (hasAnyRole(roles, BOOKING_LIST_ROLES)) return '/api/bookings'
  if (roles.includes('RESEARCHER_STUDENT') && userId != null && userId !== '') {
    return `/api/bookings/user/${encodeURIComponent(userId)}`
  }
  return null
}

export function getMaintenanceAccessPlan(roles, userId) {
  if (!hasValidRoles(roles)) {
    return { canView: false, recordsUrl: null, summaryUrl: null, scope: 'none' }
  }

  if (hasAnyRole(roles, MAINTENANCE_SUMMARY_ROLES)) {
    return {
      canView: true,
      recordsUrl: '/api/maintenance',
      summaryUrl: '/api/maintenance/summary',
      scope: roles.includes('SYSTEM_ADMIN') ? 'platform' : 'institution',
    }
  }

  if (roles.includes('RESEARCHER_STUDENT') && userId != null && userId !== '') {
    return {
      canView: true,
      recordsUrl: `/api/maintenance/reporter/${encodeURIComponent(userId)}`,
      summaryUrl: null,
      scope: 'owner',
    }
  }

  return { canView: false, recordsUrl: null, summaryUrl: null, scope: 'none' }
}

export function getDashboardAccessPlan(roles, userId) {
  if (!hasValidRoles(roles)) return null
  const maintenance = getMaintenanceAccessPlan(roles, userId)
  return {
    bookingsUrl: getBookingRecordsUrl(roles, userId),
    maintenanceSummaryUrl: maintenance.summaryUrl,
    bookingScope: roles.includes('SYSTEM_ADMIN')
      ? 'platform'
      : hasAnyRole(roles, BOOKING_LIST_ROLES) ? 'institution' : 'owner',
  }
}

export function canRequestBooking(roles) {
  return hasValidRoles(roles) && roles.includes('RESEARCHER_STUDENT')
}
