import { hasValidRoles } from './roleAccess.js'

const CREATE_ROLES = Object.freeze([
  'RESEARCHER_STUDENT', 'LAB_TECHNICIAN', 'LAB_MANAGER',
  'INSTITUTION_ADMIN', 'SYSTEM_ADMIN',
])
const STAFF_WRITE_ROLES = Object.freeze([
  'LAB_TECHNICIAN', 'LAB_MANAGER', 'INSTITUTION_ADMIN', 'SYSTEM_ADMIN',
])

export function getUtilizationPermissions(roles) {
  if (!hasValidRoles(roles)) {
    return { canCreate: false, canEdit: false, canDelete: false }
  }
  return {
    canCreate: roles.some((role) => CREATE_ROLES.includes(role)),
    canEdit: roles.some((role) => STAFF_WRITE_ROLES.includes(role)),
    canDelete: roles.some((role) => STAFF_WRITE_ROLES.includes(role)),
  }
}

function validLocalDateTime(value) {
  if (!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(?::\d{2})?$/.test(value)) return false
  const [date, time] = value.split('T')
  const parsedDate = new Date(`${date}T00:00:00.000Z`)
  if (Number.isNaN(parsedDate.getTime()) || parsedDate.toISOString().slice(0, 10) !== date) return false
  const [hours, minutes, seconds = '0'] = time.split(':').map(Number)
  return hours <= 23 && minutes <= 59 && seconds <= 59
}

function validId(value) {
  const id = Number(value)
  return Number.isInteger(id) && id > 0 ? id : null
}

function normalizeLocalDateTime(value) {
  return value.length === 16 ? `${value}:00` : value
}

function validDate(value) {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return false
  const parsed = new Date(`${value}T00:00:00.000Z`)
  return !Number.isNaN(parsed.getTime()) && parsed.toISOString().slice(0, 10) === value
}

export function validateUtilizationCreate(form) {
  const errors = []
  const equipmentId = validId(form.equipmentId)
  if (!equipmentId) errors.push('Select valid equipment.')
  const startTime = String(form.startTime ?? '').trim()
  const endTime = String(form.endTime ?? '').trim()
  if (!startTime || !validLocalDateTime(startTime)) errors.push('A valid start time is required.')
  if (endTime && !validLocalDateTime(endTime)) errors.push('End time must be a valid date and time.')
  if (startTime && endTime && validLocalDateTime(startTime) && validLocalDateTime(endTime) && endTime <= startTime) {
    errors.push('End time must be after start time.')
  }
  const bookingId = String(form.bookingId ?? '').trim() ? validId(form.bookingId) : null
  if (String(form.bookingId ?? '').trim() && !bookingId) errors.push('Booking ID must be a positive integer.')
  const notes = String(form.notes ?? '').trim()
  if (notes.length > 500) errors.push('Notes must be 500 characters or fewer.')
  const body = { equipmentId, startTime, endTime: endTime || null, notes: notes || null }
  if (bookingId) body.bookingId = bookingId
  return { errors, body }
}

export function validateUtilizationUpdate(form, record) {
  const errors = []
  const endTime = String(form.endTime ?? '').trim()
  if (endTime && !validLocalDateTime(endTime)) errors.push('End time must be a valid date and time.')
  const startTime = String(record?.startTime ?? '').replace(/\.\d+$/, '')
  if (endTime && validLocalDateTime(endTime) && startTime && normalizeLocalDateTime(endTime) <= normalizeLocalDateTime(startTime)) {
    errors.push('End time must be after the recorded start time.')
  }
  const notes = String(form.notes ?? '')
  if (notes.length > 500) errors.push('Notes must be 500 characters or fewer.')
  return { errors, body: { endTime: endTime || null, notes } }
}

export function validateDateRange(startDate, endDate) {
  const errors = []
  if (!startDate || !endDate) errors.push('Select both a start date and an end date.')
  else if (!validDate(startDate) || !validDate(endDate)) errors.push('Select valid start and end dates.')
  else if (startDate > endDate) errors.push('The start date must not be after the end date.')
  return {
    errors,
    params: startDate && endDate
      ? { start: `${startDate}T00:00:00`, end: `${endDate}T23:59:59` }
      : null,
  }
}

export function utilizationApiError(error, fallback) {
  if (error?.response?.status === 401) return 'Your session has expired. Sign in again to continue.'
  if (error?.response?.status === 403) return 'Access denied. Your role or account scope does not permit this utilization operation.'
  return error?.response?.data?.message
    || error?.response?.data?.detail
    || error?.response?.data?.error
    || error?.message
    || fallback
}
