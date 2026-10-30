export const ROLE_LABELS = Object.freeze({
  RESEARCHER_STUDENT: 'Researcher / Student',
  LAB_TECHNICIAN: 'Lab Technician',
  LAB_MANAGER: 'Lab Manager',
  DEPARTMENT_HEAD: 'Department Head',
  INSTITUTION_ADMIN: 'Institution Administrator',
  SYSTEM_ADMIN: 'System Administrator',
})

export function getProfileDetails(user) {
  if (!user || !Array.isArray(user.roles)) return null

  return {
    email: user.email || 'Unavailable',
    userId: user.id ?? 'Unavailable',
    institutionId: user.institutionId ?? 'Unavailable',
    roles: user.roles.map((role) => ROLE_LABELS[role] || role),
  }
}
