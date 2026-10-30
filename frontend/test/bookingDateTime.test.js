import assert from 'node:assert/strict'
import test from 'node:test'
import {
  buildBookingPayload,
  serializeLocalDateTime,
} from '../src/access/bookingDateTime.js'

test('booking payload preserves selected local wall-clock time when browser timezone is not UTC', () => {
  const previousTimezone = process.env.TZ
  process.env.TZ = 'Asia/Kolkata'

  try {
    const selectedLocalTime = '2026-01-15T09:30'
    const payload = buildBookingPayload({
      equipmentId: '14',
      startTime: selectedLocalTime,
      endTime: '2026-01-15T10:45',
      purpose: '  Equipment inspection  ',
    })

    assert.deepEqual(payload, {
      equipmentId: 14,
      startTime: '2026-01-15T09:30:00',
      endTime: '2026-01-15T10:45:00',
      purpose: 'Equipment inspection',
    })
    assert.equal(JSON.stringify(payload), '{"equipmentId":14,"startTime":"2026-01-15T09:30:00","endTime":"2026-01-15T10:45:00","purpose":"Equipment inspection"}')
    assert.equal(new Date(selectedLocalTime).toISOString(), '2026-01-15T04:00:00.000Z')
    assert.notEqual(payload.startTime, new Date(selectedLocalTime).toISOString())
  } finally {
    if (previousTimezone === undefined) delete process.env.TZ
    else process.env.TZ = previousTimezone
  }
})

test('LocalDateTime request values include seconds and contain no timezone suffix', () => {
  assert.equal(serializeLocalDateTime('2026-07-02T08:05'), '2026-07-02T08:05:00')
  assert.equal(serializeLocalDateTime('2026-07-02T08:05:17'), '2026-07-02T08:05:17')
  assert.throws(() => serializeLocalDateTime('2026-02-30T08:05'), TypeError)
  assert.throws(() => serializeLocalDateTime('2026-07-02T08:05Z'), TypeError)
})
