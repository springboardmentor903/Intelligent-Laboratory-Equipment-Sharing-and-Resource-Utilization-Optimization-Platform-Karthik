import { hasValidRoles } from './roleAccess.js'

const EQUIPMENT_WRITE_ROLES = Object.freeze([
  'LAB_TECHNICIAN', 'LAB_MANAGER', 'INSTITUTION_ADMIN', 'SYSTEM_ADMIN',
])
const EQUIPMENT_DELETE_ROLES = Object.freeze([
  'LAB_MANAGER', 'INSTITUTION_ADMIN', 'SYSTEM_ADMIN',
])
const RECORD_CREATE_ROLES = EQUIPMENT_WRITE_ROLES
export const EQUIPMENT_STATUSES = Object.freeze([
  'AVAILABLE', 'BOOKED', 'UNDER_MAINTENANCE', 'OUT_OF_SERVICE', 'RETIRED',
])

export function getEquipmentPermissions(roles = []) {
  if (!hasValidRoles(roles)) roles = []
  return {
    canCreate: roles.some((role) => EQUIPMENT_WRITE_ROLES.includes(role)),
    canEdit: roles.some((role) => EQUIPMENT_WRITE_ROLES.includes(role)),
    canDelete: roles.some((role) => EQUIPMENT_DELETE_ROLES.includes(role)),
    canCreateRecords: roles.some((role) => RECORD_CREATE_ROLES.includes(role)),
    canFilterInstitution: roles.includes('SYSTEM_ADMIN'),
  }
}

export function buildEquipmentSearchParams(filters) {
  const params = {}
  for (const key of ['name', 'category', 'description', 'labLocation', 'status', 'tag']) {
    const value = filters?.[key]
    if (value != null && String(value).trim()) params[key] = String(value).trim()
  }
  if (filters?.institutionId != null && String(filters.institutionId).trim()) {
    const institutionId = Number(filters.institutionId)
    if (Number.isInteger(institutionId) && institutionId > 0) params.institutionId = institutionId
  }
  if (filters?.departmentId != null && String(filters.departmentId).trim()) {
    const departmentId = Number(filters.departmentId)
    if (Number.isInteger(departmentId) && departmentId > 0) params.departmentId = departmentId
  }
  return params
}

function requiredText(value, label, max, errors) {
  const normalized = String(value ?? '').trim()
  if (!normalized) errors.push(`${label} is required.`)
  else if (normalized.length > max) errors.push(`${label} must be ${max} characters or fewer.`)
  return normalized
}

export function validateEquipmentForm(form) {
  const errors = []
  const name = requiredText(form.name, 'Equipment name', 150, errors)
  const institutionId = Number(form.institutionId)
  if (!Number.isInteger(institutionId) || institutionId < 1) errors.push('A valid institution ID is required.')
  const quantity = Number(form.quantity)
  if (!Number.isInteger(quantity) || quantity < 1) errors.push('Quantity must be an integer of at least 1.')
  for (const [field, label, max] of [
    ['category', 'Category', 100], ['description', 'Description', 500], ['labLocation', 'Lab location', 150], ['status', 'Status', 30],
  ]) {
    if (String(form[field] ?? '').trim().length > max) errors.push(`${label} must be ${max} characters or fewer.`)
  }
  const status = String(form.status ?? '').trim().toUpperCase()
  if (status && !EQUIPMENT_STATUSES.includes(status)) {
    errors.push(`Status must be one of: ${EQUIPMENT_STATUSES.join(', ')}.`)
  }

  const tags = String(form.tags ?? '').split(',').map((tag) => tag.trim()).filter(Boolean)
  if (tags.length > 30) errors.push('At most 30 equipment tags are allowed.')
  if (tags.some((tag) => tag.length > 50)) errors.push('Equipment tags must be 50 characters or fewer.')

  let specifications = {}
  try {
    specifications = form.specificationsText?.trim()
      ? JSON.parse(form.specificationsText)
      : {}
    if (!specifications || Array.isArray(specifications) || typeof specifications !== 'object') {
      errors.push('Specifications must be a JSON object.')
      specifications = {}
    } else {
      if (Object.keys(specifications).length > 100) errors.push('At most 100 specifications are allowed.')
      if (Object.entries(specifications).some(([key, value]) => !key.trim() || value == null)) {
        errors.push('Specification keys must be nonblank and values cannot be null.')
      }
      const normalizedKeys = Object.keys(specifications).map((key) => key.trim())
      if (new Set(normalizedKeys).size !== normalizedKeys.length) errors.push('Specification keys must be unique after trimming.')
    }
  } catch {
    errors.push('Specifications must contain valid JSON.')
  }

  const body = {
    name,
    category: String(form.category ?? '').trim() || null,
    description: String(form.description ?? '').trim() || null,
    institutionId,
    labLocation: String(form.labLocation ?? '').trim() || null,
    quantity,
    specifications,
    tags,
    status: status || null,
  }
  if (form.departmentId != null && String(form.departmentId).trim()) {
    body.departmentId = Number(form.departmentId)
  }
  return { errors, body }
}

function isValidDate(value) {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return false
  const parsed = new Date(`${value}T00:00:00.000Z`)
  return !Number.isNaN(parsed.getTime()) && parsed.toISOString().slice(0, 10) === value
}

export function validateCalibrationForm(form) {
  const errors = []
  const calibrationDate = String(form.calibrationDate ?? '').trim()
  const nextDueDate = String(form.nextDueDate ?? '').trim()
  if (!calibrationDate || !isValidDate(calibrationDate)) errors.push('A valid calibration date is required.')
  if (!nextDueDate || !isValidDate(nextDueDate)) errors.push('A valid next due date is required.')
  if (calibrationDate && nextDueDate && nextDueDate <= calibrationDate) errors.push('Next due date must be after the calibration date.')
  const result = String(form.result ?? '').trim().toUpperCase()
  if (!['PASS', 'FAIL', 'CONDITIONAL'].includes(result)) errors.push('Result must be PASS, FAIL, or CONDITIONAL.')
  const notes = String(form.notes ?? '').trim()
  if (notes.length > 1000) errors.push('Notes must be 1000 characters or fewer.')
  return { errors, body: { calibrationDate, nextDueDate, result, notes: notes || null } }
}

export function validateCertificationForm(form) {
  const errors = []
  const certificateType = requiredText(form.certificateType, 'Certificate type', 150, errors)
  const issuingOrganization = requiredText(form.issuingOrganization, 'Issuing organization', 200, errors)
  const issueDate = String(form.issueDate ?? '').trim()
  const expiryDate = String(form.expiryDate ?? '').trim()
  if (!issueDate || !isValidDate(issueDate)) errors.push('A valid issue date is required.')
  if (!expiryDate || !isValidDate(expiryDate)) errors.push('A valid expiry date is required.')
  if (issueDate && expiryDate && expiryDate <= issueDate) errors.push('Expiry date must be after the issue date.')
  const documentReference = String(form.documentReference ?? '').trim()
  if (documentReference.length > 500) errors.push('Document reference must be 500 characters or fewer.')
  return {
    errors,
    body: { certificateType, issuingOrganization, issueDate, expiryDate, documentReference: documentReference || null },
  }
}
