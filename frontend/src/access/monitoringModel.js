export const EQUIPMENT_STATUSES = Object.freeze([
  'AVAILABLE',
  'BOOKED',
  'UNDER_MAINTENANCE',
  'OUT_OF_SERVICE',
  'RETIRED',
])

export function formatMonitoringTimestamp(value) {
  if (!value) return 'Not provided'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? 'Not provided' : date.toLocaleString()
}

export function formatElapsedUsage(value, now) {
  const start = new Date(value).getTime()
  if (!Number.isFinite(start) || !Number.isFinite(now)) return 'Not provided'
  const minutes = Math.max(0, Math.floor((now - start) / 60000))
  const hours = Math.floor(minutes / 60)
  const days = Math.floor(hours / 24)
  return `${days ? `${days}d ` : ''}${hours % 24}h ${minutes % 60}m`
}

export function formatEquipmentStatus(value) {
  return String(value || 'UNKNOWN').replaceAll('_', ' ')
}

export function getMonitoringErrorMessage(error) {
  if (error?.response?.status === 403) {
    return 'You do not have permission to view equipment monitoring data for this scope.'
  }
  return error?.response?.data?.message || error?.message || 'Unable to load equipment.'
}

export function readLiveEquipmentPage(data) {
  if (!data || !Array.isArray(data.content)) {
    throw new Error('The live equipment response has an invalid page format.')
  }
  return data
}

export function applyEquipmentStatusEvent(records, message, query) {
  if (message?.type !== 'EQUIPMENT_STATUS_UPDATED'
      || message.equipmentId == null
      || !message.equipment) {
    return { records, affected: false, removed: false }
  }

  const id = String(message.equipmentId)
  if (!records.some((record) => String(record.id) === id)) {
    return { records, affected: false, removed: false }
  }

  const incoming = message.equipment
  const matchesStatus = !query.status
    || String(incoming.status || '').toUpperCase() === query.status
  const searchValue = query.q.toLowerCase()
  const matchesSearch = !searchValue || [incoming.name, incoming.assetTag]
    .some((value) => String(value || '').toLowerCase().includes(searchValue))

  if (!matchesStatus || !matchesSearch) {
    return {
      records: records.filter((record) => String(record.id) !== id),
      affected: true,
      removed: true,
    }
  }

  return {
    records: records.map((record) => String(record.id) === id ? incoming : record),
    affected: true,
    removed: false,
  }
}
