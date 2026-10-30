import { hasValidRoles } from './roleAccess.js'

export function canManageDepartments(roles) {
  return hasValidRoles(roles) && (roles.includes('INSTITUTION_ADMIN') || roles.includes('SYSTEM_ADMIN'))
}

export function canEditUserDepartment(roles) {
  return hasValidRoles(roles) && (roles.includes('INSTITUTION_ADMIN') || roles.includes('SYSTEM_ADMIN'))
}

export function validateDepartmentName(name, existingDepartments = [], currentId = null) {
  if (!name || typeof name !== 'string' || name.trim() === '') {
    return 'Department name is required.'
  }
  const trimmed = name.trim()
  if (trimmed.length > 150) {
    return 'Department name must be 150 characters or fewer.'
  }
  const isDuplicate = existingDepartments.some(
    (dept) =>
      dept &&
      dept.name &&
      dept.name.trim().toLowerCase() === trimmed.toLowerCase() &&
      String(dept.id) !== String(currentId),
  )
  if (isDuplicate) {
    return 'A department with this name already exists in this institution.'
  }
  return null
}
