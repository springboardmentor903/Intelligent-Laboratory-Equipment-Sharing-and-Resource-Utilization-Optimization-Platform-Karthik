/**
 * Resource Utilization Analytics Domain Model & Utilities
 * Enforces the platform timezone (Asia/Kolkata) and default operating schedule:
 * Monday–Friday, 09:00–17:00 (Saturday & Sunday closed).
 */

export const PLATFORM_TIMEZONE = 'Asia/Kolkata'

export const DEFAULT_OPERATING_SCHEDULE = {
  isCustom: false,
  displayText: 'Monday–Friday, 09:00–17:00 (Sat & Sun Closed)',
  badgeLabel: 'Default (No custom schedule)',
  openDays: [1, 2, 3, 4, 5], // Monday through Friday (ISO 1-7)
  closedDays: [6, 7],         // Saturday and Sunday
  openTime: '09:00',
  closeTime: '17:00',
  hoursPerDay: 8,
  description:
    'Default schedule: Monday–Friday, 09:00–17:00 IST (Sat & Sun closed). Custom equipment operating hours apply only when specifically configured.',
}

/**
 * Formats a Date object or timestamp as YYYY-MM-DD in the platform timezone (Asia/Kolkata).
 */
export function formatPlatformDate(date = new Date()) {
  const d = typeof date === 'string' || typeof date === 'number' ? new Date(date) : date
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: PLATFORM_TIMEZONE,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(d)
}

/**
 * Returns date range bounds { preset, start, end } for a given preset in Asia/Kolkata.
 * - '7d': 7 days prior to today through today (e.g. 2026-10-28 to 2026-11-04)
 * - '30d': 30 days prior to today through today
 * - '90d': 90 days prior to today through today
 */
export function getDateRangeForPreset(preset = '30d', baseDate = new Date()) {
  const endStr = formatPlatformDate(baseDate)
  let days = 30
  if (preset === '7d') days = 7
  else if (preset === '90d') days = 90
  else if (preset === '30d') days = 30

  const baseMs = typeof baseDate === 'string' || typeof baseDate === 'number'
    ? new Date(baseDate).getTime()
    : baseDate.getTime()

  const startMs = baseMs - days * 24 * 60 * 60 * 1000
  const startStr = formatPlatformDate(new Date(startMs))

  return {
    preset: ['7d', '30d', '90d'].includes(preset) ? preset : 'custom',
    start: startStr,
    end: endStr,
  }
}

/**
 * Restores or initializes analytics filter state from URL search parameters.
 */
export function resolveAnalyticsUrlState(searchParams, baseDate = new Date()) {
  const params = searchParams instanceof URLSearchParams
    ? searchParams
    : new URLSearchParams(searchParams || '')

  const presetParam = params.get('preset')
  const startParam = params.get('start')
  const endParam = params.get('end')
  const dept = params.get('dept') || ''
  const equipment = params.get('equipment') || ''
  const target = params.get('target') || ''

  // If valid preset is provided
  if (presetParam && ['7d', '30d', '90d'].includes(presetParam)) {
    if (startParam && endParam && startParam <= endParam) {
      return {
        preset: presetParam,
        start: startParam,
        end: endParam,
        dept,
        equipment,
        target,
      }
    }
    const computed = getDateRangeForPreset(presetParam, baseDate)
    return {
      preset: computed.preset,
      start: computed.start,
      end: computed.end,
      dept,
      equipment,
      target,
    }
  }

  // If explicit custom date bounds are provided
  if (startParam && endParam) {
    return {
      preset: 'custom',
      start: startParam,
      end: endParam,
      dept,
      equipment,
      target,
    }
  }

  // Default fallback: 30d
  const fallback = getDateRangeForPreset('30d', baseDate)
  return {
    preset: fallback.preset,
    start: fallback.start,
    end: fallback.end,
    dept,
    equipment,
    target,
  }
}

/**
 * Validates date range constraints (not empty, start <= end, max 90 days).
 */
export function validateDateRange(startDate, endDate) {
  if (!startDate || !endDate) {
    return { valid: false, error: 'Both start and end dates are required.' }
  }
  if (startDate > endDate) {
    return { valid: false, error: 'Start date cannot be after end date.' }
  }
  const s = new Date(`${startDate}T00:00:00`).getTime()
  const e = new Date(`${endDate}T00:00:00`).getTime()
  const diffDays = Math.round((e - s) / (24 * 60 * 60 * 1000))

  if (diffDays > 90) {
    return {
      valid: false,
      error: 'Selected date range exceeds the maximum allowed limit of 90 days. Please choose 90 days or less.',
    }
  }
  return { valid: true, error: null, days: diffDays }
}

/**
 * Constructs clean API query parameters for all analytics endpoints.
 * Omits any arbitrary hardcoded hours-per-day scalar so backend uses resolved operating schedule.
 */
export function buildAnalyticsQueryParams({
  startDate,
  endDate,
  departmentId,
  equipmentId,
  targetRate,
}) {
  const params = {
    start: `${startDate}T00:00:00`,
    end: `${endDate}T23:59:59`,
  }
  if (departmentId) params.departmentId = departmentId
  if (equipmentId) params.equipmentId = equipmentId
  if (targetRate !== undefined && targetRate !== null && targetRate !== '' && !Number.isNaN(Number(targetRate))) {
    params.targetUtilizationRate = Number(targetRate)
  }
  return params
}

/**
 * Calculates theoretical expected operating capacity hours across a date range
 * under the mentor-specified default schedule (Mon-Fri 09:00-17:00, Sat-Sun closed).
 */
export function calculateExpectedCapacityHours(startDateStr, endDateStr, quantity = 1, customSchedule = null) {
  if (!startDateStr || !endDateStr || startDateStr > endDateStr) return 0
  const qty = quantity > 0 ? quantity : 1
  let totalMinutes = 0

  const [sYear, sMonth, sDay] = startDateStr.split('-').map(Number)
  const [eYear, eMonth, eDay] = endDateStr.split('-').map(Number)
  const start = new Date(Date.UTC(sYear, sMonth - 1, sDay))
  const end = new Date(Date.UTC(eYear, eMonth - 1, eDay))

  for (let d = new Date(start); d <= end; d.setUTCDate(d.getUTCDate() + 1)) {
    const jsDay = d.getUTCDay()
    const isoDay = jsDay === 0 ? 7 : jsDay // 1=Mon, 7=Sun

    if (customSchedule && customSchedule[isoDay]) {
      const sched = customSchedule[isoDay]
      if (sched.isOpen && sched.openMinutes) {
        totalMinutes += sched.openMinutes * qty
      }
    } else {
      // Default: Mon-Fri (1-5) 09:00-17:00 (480 mins), Sat-Sun (6-7) closed
      if (isoDay <= 5) {
        totalMinutes += 480 * qty
      }
    }
  }

  return Math.round((totalMinutes / 60) * 10) / 10
}
