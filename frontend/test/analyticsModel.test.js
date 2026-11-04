import assert from 'node:assert/strict'
import test from 'node:test'
import {
  PLATFORM_TIMEZONE,
  DEFAULT_OPERATING_SCHEDULE,
  formatPlatformDate,
  getDateRangeForPreset,
  resolveAnalyticsUrlState,
  validateDateRange,
  buildAnalyticsQueryParams,
  calculateExpectedCapacityHours,
} from '../src/access/analyticsModel.js'

test('platform timezone is Asia/Kolkata', () => {
  assert.equal(PLATFORM_TIMEZONE, 'Asia/Kolkata')
})

test('default operating schedule explicitly specifies Mon-Fri 09:00-17:00 and is not custom', () => {
  assert.equal(DEFAULT_OPERATING_SCHEDULE.isCustom, false)
  assert.equal(DEFAULT_OPERATING_SCHEDULE.hoursPerDay, 8)
  assert.equal(DEFAULT_OPERATING_SCHEDULE.openTime, '09:00')
  assert.equal(DEFAULT_OPERATING_SCHEDULE.closeTime, '17:00')
  assert.deepEqual(DEFAULT_OPERATING_SCHEDULE.openDays, [1, 2, 3, 4, 5])
  assert.deepEqual(DEFAULT_OPERATING_SCHEDULE.closedDays, [6, 7])
  assert.match(DEFAULT_OPERATING_SCHEDULE.badgeLabel, /Default/i)
  assert.match(DEFAULT_OPERATING_SCHEDULE.badgeLabel, /No custom/i)
})

test('formatPlatformDate formats timestamp in Asia/Kolkata timezone', () => {
  // 2026-11-04 02:00:00 UTC is 2026-11-04 07:30:00 IST -> '2026-11-04'
  const dateUtc = new Date(Date.UTC(2026, 10, 4, 2, 0, 0))
  assert.equal(formatPlatformDate(dateUtc), '2026-11-04')

  // 2026-11-03 20:00:00 UTC is 2026-11-04 01:30:00 IST (+05:30 next day)
  const eveningUtc = new Date(Date.UTC(2026, 10, 3, 20, 0, 0))
  assert.equal(formatPlatformDate(eveningUtc), '2026-11-04')
})

test('getDateRangeForPreset calculates exact offsets in Asia/Kolkata', () => {
  const fixedBase = new Date('2026-11-04T12:00:00+05:30')

  const range7d = getDateRangeForPreset('7d', fixedBase)
  assert.equal(range7d.preset, '7d')
  assert.equal(range7d.end, '2026-11-04')
  assert.equal(range7d.start, '2026-10-28')

  const range30d = getDateRangeForPreset('30d', fixedBase)
  assert.equal(range30d.preset, '30d')
  assert.equal(range30d.end, '2026-11-04')
  assert.equal(range30d.start, '2026-10-05')

  const range90d = getDateRangeForPreset('90d', fixedBase)
  assert.equal(range90d.preset, '90d')
  assert.equal(range90d.end, '2026-11-04')
  assert.equal(range90d.start, '2026-08-06')
})

test('resolveAnalyticsUrlState restores preset 7d without explicit dates', () => {
  const fixedBase = new Date('2026-11-04T12:00:00+05:30')
  const params = new URLSearchParams('preset=7d')
  const state = resolveAnalyticsUrlState(params, fixedBase)

  assert.equal(state.preset, '7d')
  assert.equal(state.end, '2026-11-04')
  assert.equal(state.start, '2026-10-28')
})

test('resolveAnalyticsUrlState preserves explicit start, end and filter parameters', () => {
  const params = new URLSearchParams('preset=custom&start=2026-10-10&end=2026-10-20&dept=12&equipment=42&target=85')
  const state = resolveAnalyticsUrlState(params)

  assert.equal(state.preset, 'custom')
  assert.equal(state.start, '2026-10-10')
  assert.equal(state.end, '2026-10-20')
  assert.equal(state.dept, '12')
  assert.equal(state.equipment, '42')
  assert.equal(state.target, '85')
})

test('validateDateRange enforces start <= end, non-empty and 90-day limit', () => {
  assert.equal(validateDateRange('', '2026-11-04').valid, false)
  assert.equal(validateDateRange('2026-11-05', '2026-11-04').valid, false)

  // 95 days exceeds limit
  const over90 = validateDateRange('2026-07-01', '2026-11-04')
  assert.equal(over90.valid, false)
  assert.match(over90.error, /exceeds the maximum allowed limit of 90 days/i)

  // 7 days is valid
  const valid7d = validateDateRange('2026-10-28', '2026-11-04')
  assert.equal(valid7d.valid, true)
  assert.equal(valid7d.error, null)

  // 90 days is valid
  const valid90d = validateDateRange('2026-08-06', '2026-11-04')
  assert.equal(valid90d.valid, true)
  assert.equal(valid90d.error, null)
})

test('buildAnalyticsQueryParams formats timestamps and omits misleading hours scalar', () => {
  const query = buildAnalyticsQueryParams({
    startDate: '2026-10-28',
    endDate: '2026-11-04',
    departmentId: '5',
    equipmentId: '10',
    targetRate: '75',
  })

  assert.equal(query.start, '2026-10-28T00:00:00')
  assert.equal(query.end, '2026-11-04T23:59:59')
  assert.equal(query.departmentId, '5')
  assert.equal(query.equipmentId, '10')
  assert.equal(query.targetUtilizationRate, 75)
  assert.equal(query.operatingHoursPerDay, undefined)
  assert.equal(query.hours, undefined)
})

test('calculateExpectedCapacityHours matches mentor default schedule (40h/week/unit)', () => {
  // Monday 2026-11-02 to Sunday 2026-11-08 (1 full week)
  // Mon-Fri = 5 days * 8h = 40h per unit
  const cap1Unit = calculateExpectedCapacityHours('2026-11-02', '2026-11-08', 1)
  assert.equal(cap1Unit, 40.0)

  // 5 units for a 7-day Monday-Sunday week = 200h
  const cap5Units = calculateExpectedCapacityHours('2026-11-02', '2026-11-08', 5)
  assert.equal(cap5Units, 200.0)

  // Weekend-only window (Saturday to Sunday) yields 0h
  const capWeekend = calculateExpectedCapacityHours('2026-11-07', '2026-11-08', 5)
  assert.equal(capWeekend, 0.0)

  // 13 full weeks (91 days): 65 weekdays * 8h = 520h per unit
  // 7 units across 91 days = 3640h (proving the 91-day artifact)
  const cap13Weeks7Units = calculateExpectedCapacityHours('2026-08-06', '2026-11-04', 7)
  assert.equal(cap13Weeks7Units, 3640.0)
})
