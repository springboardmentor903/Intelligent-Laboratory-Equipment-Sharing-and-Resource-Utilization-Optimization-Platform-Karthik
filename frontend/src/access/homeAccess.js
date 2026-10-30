import { hasValidRoles, SUPPORTED_ROLES } from './roleAccess.js'
import { ROLE_LABELS } from './profile.js'

export { SUPPORTED_ROLES, ROLE_LABELS }

export function getRoleHomeItems(roles) {
  if (!hasValidRoles(roles)) return []

  const items = []
  const seenTitles = new Set()

  function addItem(item) {
    if (!seenTitles.has(item.title)) {
      seenTitles.add(item.title)
      items.push(item)
    }
  }

  if (roles.includes('SYSTEM_ADMIN')) {
    addItem({
      icon: '🛡️',
      title: 'Institution Onboarding',
      description: 'Review and approve pending college and institution registrations.',
      path: '/profile',
      action: 'Review institutions',
    })
    addItem({
      icon: '🔑',
      title: 'Platform Administrators',
      description: 'Review and approve prospective platform administrator applications.',
      path: '/profile',
      action: 'Review applications',
    })
    addItem({
      icon: '🌐',
      title: 'Platform Directory',
      description: 'Manage institutions across the platform and update their status.',
      path: '/profile',
      action: 'Manage directory',
    })
    addItem({
      icon: '🔬',
      title: 'Platform Equipment',
      description: 'Global oversight of equipment records across all institutions.',
      path: '/equipment',
      action: 'View equipment',
    })
    addItem({
      icon: '📊',
      title: 'Platform Analytics',
      description: 'Access platform-wide utilization metrics and system health.',
      path: '/dashboard',
      action: 'Open dashboard',
    })
  }

  if (roles.includes('INSTITUTION_ADMIN')) {
    addItem({
      icon: '👥',
      title: 'Membership Approvals',
      description: 'Review and approve pending user registrations for your institution.',
      path: '/profile',
      action: 'Review memberships',
    })
    addItem({
      icon: '🏛️',
      title: 'Department Management',
      description: 'Create and manage academic departments in your institution.',
      path: '/profile',
      action: 'Manage departments',
    })
    addItem({
      icon: '🔬',
      title: 'Institution Equipment',
      description: 'Configure and manage institution laboratory equipment inventory.',
      path: '/equipment',
      action: 'Manage equipment',
    })
    addItem({
      icon: '📅',
      title: 'Institution Bookings',
      description: 'Monitor and review equipment bookings across the institution.',
      path: '/bookings',
      action: 'View bookings',
    })
    addItem({
      icon: '📊',
      title: 'Utilization Analytics',
      description: 'Track institutional equipment utilization and optimization metrics.',
      path: '/utilization',
      action: 'View utilization',
    })
  }

  if (roles.includes('LAB_MANAGER')) {
    addItem({
      icon: '📅',
      title: 'Booking Approvals',
      description: 'Review pending reservations and make booking decisions.',
      path: '/bookings',
      action: 'Manage bookings',
    })
    addItem({
      icon: '🔬',
      title: 'Equipment Inventory',
      description: 'Add and configure equipment, manage inventory lifecycle.',
      path: '/equipment',
      action: 'Manage inventory',
    })
    addItem({
      icon: '📊',
      title: 'Utilization Optimization',
      description: 'Analyze equipment usage patterns and optimize lab utilization.',
      path: '/utilization',
      action: 'Analyze utilization',
    })
    addItem({
      icon: '🛠️',
      title: 'Maintenance Oversight',
      description: 'Track equipment maintenance workflows and repair progress.',
      path: '/maintenance',
      action: 'Review maintenance',
    })
    addItem({
      icon: '📆',
      title: 'Booking Calendar',
      description: 'Overview of all laboratory schedules and reservations.',
      path: '/booking-calendar',
      action: 'View calendar',
    })
  }

  if (roles.includes('DEPARTMENT_HEAD')) {
    addItem({
      icon: '📊',
      title: 'Department Utilization',
      description: 'Review departmental equipment usage and operating hours.',
      path: '/utilization',
      action: 'View utilization',
    })
    addItem({
      icon: '🔬',
      title: 'Department Equipment',
      description: 'Monitor equipment assigned to your department.',
      path: '/equipment',
      action: 'View equipment',
    })
    addItem({
      icon: '📅',
      title: 'Department Bookings',
      description: 'Review laboratory reservations across departmental labs.',
      path: '/bookings',
      action: 'View bookings',
    })
    addItem({
      icon: '📆',
      title: 'Booking Calendar',
      description: 'Track departmental equipment schedules and availability.',
      path: '/booking-calendar',
      action: 'View calendar',
    })
    addItem({
      icon: '🛠️',
      title: 'Maintenance Status',
      description: 'Track reported maintenance issues and equipment readiness.',
      path: '/maintenance',
      action: 'View maintenance',
    })
  }

  if (roles.includes('LAB_TECHNICIAN')) {
    addItem({
      icon: '🛠️',
      title: 'Maintenance Operations',
      description: 'Review assigned maintenance requests and record repair actions.',
      path: '/maintenance',
      action: 'Manage maintenance',
    })
    addItem({
      icon: '🔬',
      title: 'Equipment & Calibrations',
      description: 'Manage equipment status, record calibrations, and certifications.',
      path: '/equipment',
      action: 'Manage equipment',
    })
    addItem({
      icon: '📅',
      title: 'Lab Bookings',
      description: 'Review equipment bookings and verify laboratory schedules.',
      path: '/bookings',
      action: 'Review bookings',
    })
    addItem({
      icon: '📆',
      title: 'Booking Calendar',
      description: 'Check scheduled equipment sessions and room occupancy.',
      path: '/booking-calendar',
      action: 'View calendar',
    })
    addItem({
      icon: '📊',
      title: 'Utilization Tracking',
      description: 'Monitor operational hours and recorded equipment usage.',
      path: '/utilization',
      action: 'View utilization',
    })
  }

  if (roles.includes('RESEARCHER_STUDENT')) {
    addItem({
      icon: '🔬',
      title: 'Equipment Catalog',
      description: 'Search and browse laboratory equipment and check availability.',
      path: '/equipment',
      action: 'Browse equipment',
    })
    addItem({
      icon: '📅',
      title: 'My Bookings',
      description: 'Review your equipment reservations and booking status.',
      path: '/bookings',
      action: 'View my bookings',
    })
    addItem({
      icon: '📆',
      title: 'Booking Calendar',
      description: 'Explore laboratory equipment schedules and reserved time slots.',
      path: '/booking-calendar',
      action: 'View calendar',
    })
    addItem({
      icon: '🛠️',
      title: 'Report Maintenance',
      description: 'Report equipment issues or request maintenance for laboratory tools.',
      path: '/maintenance',
      action: 'Report issue',
    })
    addItem({
      icon: '📊',
      title: 'Utilization',
      description: 'Check recorded equipment usage hours and resource utilization.',
      path: '/utilization',
      action: 'View utilization',
    })
  }

  return items
}

export function getRoleGuidance(roles) {
  if (!hasValidRoles(roles)) return ''

  if (roles.includes('SYSTEM_ADMIN')) {
    return 'As a System Administrator, you manage platform-wide operations, verify institution onboarding requests, and supervise global platform access.'
  }
  if (roles.includes('INSTITUTION_ADMIN')) {
    return 'As an Institution Administrator, you manage institutional departments, verify applicant memberships, and oversee laboratory equipment and bookings for your college.'
  }
  if (roles.includes('LAB_MANAGER')) {
    return 'As a Lab Manager, you are authorized to manage the equipment catalog, approve or reject equipment reservation requests, and monitor resource utilization.'
  }
  if (roles.includes('DEPARTMENT_HEAD')) {
    return 'As a Department Head, you have oversight of departmental laboratory utilization, equipment allocation, and reservation schedules.'
  }
  if (roles.includes('LAB_TECHNICIAN')) {
    return 'As a Lab Technician, you manage equipment calibration records, perform maintenance operations, and ensure laboratory equipment is ready for use.'
  }
  return 'As a Researcher or Student, you can explore available laboratory equipment, submit reservation requests, and track your booking schedules. Equipment reservations are confirmed after administrator review.'
}

export function getLoginDestination(redirectTo) {
  if (
    redirectTo?.pathname &&
    redirectTo.pathname !== '/login' &&
    redirectTo.pathname !== '/register' &&
    redirectTo.pathname !== '/password-reset'
  ) {
    return `${redirectTo.pathname}${redirectTo.search || ''}${redirectTo.hash || ''}`
  }
  return '/'
}

export function resolveHomeView(session) {
  if (session?.user && session?.token && hasValidRoles(session.user.roles)) {
    return 'authenticated'
  }
  return 'public'
}
