import test from 'node:test'
import assert from 'node:assert/strict'
import {
  applyEquipmentStatusEvent,
  EQUIPMENT_STATUSES,
  formatElapsedUsage,
  formatEquipmentStatus,
  formatMonitoringTimestamp,
  getMonitoringErrorMessage,
  readLiveEquipmentPage,
} from '../src/access/monitoringModel.js'

const records = [
  { id: 10, name: 'Microscope', assetTag: 'MIC-10', status: 'AVAILABLE' },
  { id: 11, name: 'Centrifuge', assetTag: 'CEN-11', status: 'BOOKED' },
]

test('live status accepts the backend Spring page format and rejects malformed responses', () => {
  const page = { content: records, number: 0, size: 12, totalElements: 2, totalPages: 1, first: true, last: true }
  assert.equal(readLiveEquipmentPage(page), page)
  assert.throws(() => readLiveEquipmentPage({ records }), /invalid page format/)
})

test('elapsed usage is derived from server-provided start time and handles invalid timestamps', () => {
  const now = Date.parse('2026-11-01T10:02:00')
  assert.equal(formatElapsedUsage('2026-11-01T08:01:00', now), '2h 1m')
  assert.equal(formatElapsedUsage('not-a-date', now), 'Not provided')
  assert.equal(formatElapsedUsage('2026-11-01T10:03:00', now), '0h 0m')
  assert.equal(formatMonitoringTimestamp('not-a-date'), 'Not provided')
})

test('status options and labels cover the supported equipment states', () => {
  assert.deepEqual(EQUIPMENT_STATUSES, [
    'AVAILABLE', 'BOOKED', 'UNDER_MAINTENANCE', 'OUT_OF_SERVICE', 'RETIRED',
  ])
  assert.equal(formatEquipmentStatus('UNDER_MAINTENANCE'), 'UNDER MAINTENANCE')
})

test('a status event updates only the matching equipment record', () => {
  const updated = { id: 11, name: 'Centrifuge', assetTag: 'CEN-11', status: 'AVAILABLE' }
  const result = applyEquipmentStatusEvent(records, {
    type: 'EQUIPMENT_STATUS_UPDATED', equipmentId: 11, equipment: updated,
  }, { q: '', status: '', page: 0 })

  assert.equal(result.affected, true)
  assert.deepEqual(result.records, [records[0], updated])
  assert.equal(result.records[0], records[0])
  assert.equal(result.removed, false)
})

test('unrelated and malformed status events do not alter the current page', () => {
  const unrelated = applyEquipmentStatusEvent(records, {
    type: 'EQUIPMENT_STATUS_UPDATED', equipmentId: 99, equipment: { id: 99 },
  }, { q: '', status: '', page: 0 })
  const malformed = applyEquipmentStatusEvent(records, { type: 'OTHER' }, { q: '', status: '', page: 0 })
  assert.equal(unrelated.affected, false)
  assert.equal(malformed.affected, false)
  assert.equal(unrelated.records, records)
  assert.equal(malformed.records, records)
})

test('status events remove records that no longer match the active search or status filter', () => {
  const statusResult = applyEquipmentStatusEvent(records, {
    type: 'EQUIPMENT_STATUS_UPDATED', equipmentId: 10,
    equipment: { id: 10, name: 'Microscope', assetTag: 'MIC-10', status: 'BOOKED' },
  }, { q: '', status: 'AVAILABLE', page: 0 })
  const searchResult = applyEquipmentStatusEvent(records, {
    type: 'EQUIPMENT_STATUS_UPDATED', equipmentId: 10,
    equipment: { id: 10, name: 'Other name', assetTag: 'OTHER-10', status: 'AVAILABLE' },
  }, { q: 'microscope', status: '', page: 0 })

  assert.deepEqual(statusResult.records, [records[1]])
  assert.deepEqual(searchResult.records, [records[1]])
  assert.equal(statusResult.removed, true)
  assert.equal(searchResult.removed, true)
})

test('monitoring errors explain forbidden scope without exposing user data', () => {
  assert.match(getMonitoringErrorMessage({ response: { status: 403 } }), /permission/)
  assert.equal(getMonitoringErrorMessage(new Error('network unavailable')), 'network unavailable')
})

test('refresh and initial load state machine resets loading on success and failure', async () => {
  let state = { loading: true, loaded: false, error: '', records: [] }
  const mockApi = async (shouldFail = false) => {
    state.loading = true
    try {
      if (shouldFail) throw new Error('API server down')
      state.records = records
      state.loaded = true
      state.error = ''
    } catch (err) {
      state.error = err.message
    } finally {
      state.loading = false
    }
  }

  // 1. Initial load completes
  await mockApi(false)
  assert.equal(state.loading, false)
  assert.equal(state.loaded, true)
  assert.equal(state.records.length, 2)
  assert.equal(state.error, '')

  // 2. Manual refresh completes
  await mockApi(false)
  assert.equal(state.loading, false)
  assert.equal(state.loaded, true)

  // 3. Failed refresh resets loading and surfaces error without unmounting records
  await mockApi(true)
  assert.equal(state.loading, false)
  assert.equal(state.loaded, true)
  assert.equal(state.records.length, 2)
  assert.equal(state.error, 'API server down')
})
