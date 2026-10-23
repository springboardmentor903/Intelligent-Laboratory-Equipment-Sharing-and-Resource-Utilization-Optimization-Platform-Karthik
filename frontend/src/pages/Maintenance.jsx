
import { useCallback, useEffect, useMemo, useState } from 'react'
import api from '../api/axiosInstance'

const statuses = [
  'REPORTED',
  'ASSIGNED',
  'IN_PROGRESS',
  'COMPLETED',
  'CANCELLED',
]

const priorities = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL']

function formatDate(value) {
  if (!value) return '—'

  const date = new Date(value)

  if (Number.isNaN(date.getTime())) return value

  return date.toLocaleString()
}

function getErrorMessage(error) {
  return (
    error.response?.data?.message ||
    error.response?.data?.error ||
    error.message ||
    'Unable to load maintenance information.'
  )
}

function displayValue(value) {
  if (!value) return '—'

  return String(value)
    .replaceAll('_', ' ')
    .toLowerCase()
    .replace(/\b\w/g, (letter) => letter.toUpperCase())
}

function statusClass(status) {
  return `maintenance-status maintenance-status-${String(
    status || 'unknown',
  ).toLowerCase()}`
}

function priorityClass(priority) {
  return `maintenance-priority maintenance-priority-${String(
    priority || 'unknown',
  ).toLowerCase()}`
}

export default function Maintenance() {
  const [records, setRecords] = useState([])
  const [equipment, setEquipment] = useState([])
  const [summary, setSummary] = useState(null)

  const [statusFilter, setStatusFilter] = useState('')
  const [priorityFilter, setPriorityFilter] = useState('')
  const [equipmentFilter, setEquipmentFilter] = useState('')

  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const loadData = useCallback(async () => {
    setLoading(true)
    setError('')

    try {
      const results = await Promise.allSettled([
        api.get('/api/maintenance'),
        api.get('/api/equipment'),
        api.get('/api/maintenance/summary'),
      ])

      const [recordsResult, equipmentResult, summaryResult] = results
      const errors = []

      if (recordsResult.status === 'fulfilled') {
        setRecords(
          Array.isArray(recordsResult.value.data)
            ? recordsResult.value.data
            : [],
        )
      } else {
        errors.push(
          `Maintenance records: ${getErrorMessage(recordsResult.reason)}`,
        )
      }

      if (equipmentResult.status === 'fulfilled') {
        setEquipment(
          Array.isArray(equipmentResult.value.data)
            ? equipmentResult.value.data
            : [],
        )
      } else {
        errors.push(
          `Equipment: ${getErrorMessage(equipmentResult.reason)}`,
        )
      }

      if (summaryResult.status === 'fulfilled') {
        setSummary(summaryResult.value.data)
      } else {
        setSummary(null)
        errors.push(
          `Summary: ${getErrorMessage(summaryResult.reason)}`,
        )
      }

      setError(errors.join(' | '))
    } catch (requestError) {
      setError(getErrorMessage(requestError))
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    loadData()
  }, [loadData])

  const filteredRecords = useMemo(() => {
    return records.filter((record) => {
      const matchesStatus =
        !statusFilter ||
        String(record.status ?? '').toUpperCase() === statusFilter

      const matchesPriority =
        !priorityFilter ||
        String(record.priority ?? '').toUpperCase() === priorityFilter

      const matchesEquipment =
        !equipmentFilter ||
        String(record.equipmentId) === equipmentFilter

      return matchesStatus && matchesPriority && matchesEquipment
    })
  }, [records, statusFilter, priorityFilter, equipmentFilter])

  const equipmentName = useCallback(
    (equipmentId) => {
      const item = equipment.find(
        (entry) => String(entry.id) === String(equipmentId),
      )

      return item?.name || `Equipment #${equipmentId ?? '—'}`
    },
    [equipment],
  )

  const summaryCards = [
    {
      label: 'Total records',
      value: summary?.totalRecords,
      description: 'All maintenance requests',
      icon: '▤',
    },
    {
      label: 'Reported',
      value: summary?.reported,
      description: 'Awaiting further action',
      icon: '!',
    },
    {
      label: 'In progress',
      value: summary?.inProgress,
      description: 'Currently being worked on',
      icon: '◷',
    },
    {
      label: 'Completed',
      value: summary?.completed,
      description: 'Completed maintenance',
      icon: '✓',
    },
    {
      label: 'High priority',
      value: summary?.highPriority,
      description: 'Marked HIGH',
      icon: '↑',
    },
    {
      label: 'Critical priority',
      value: summary?.criticalPriority,
      description: 'Marked CRITICAL',
      icon: '⚠',
    },
  ]

  return (
    <section className="page-container maintenance-page">
      <div className="maintenance-heading">
        <div>
          <p className="page-eyebrow">OPERATIONS &amp; EQUIPMENT CARE</p>
          <h1>Maintenance Management</h1>
          <p className="muted-text">
            Track reported issues, maintenance progress, and equipment care.
          </p>
        </div>

        <button
          type="button"
          className="primary-button"
          onClick={loadData}
          disabled={loading}
        >
          {loading ? 'Refreshing…' : '↻ Refresh'}
        </button>
      </div>

      {error && (
        <div className="auth-alert" role="alert">
          <p>{error}</p>
          <button
            type="button"
            className="secondary-button"
            onClick={loadData}
            disabled={loading}
          >
            Retry
          </button>
        </div>
      )}

      <div className="maintenance-summary-grid">
        {summaryCards.map((card) => (
          <article
            className="app-surface maintenance-stat"
            key={card.label}
          >
            <div className="maintenance-stat-top">
              <span className="maintenance-stat-icon" aria-hidden="true">
                {card.icon}
              </span>
              <span className="muted-text">{card.label}</span>
            </div>

            <h2>
              {loading && summary === null
                ? '…'
                : summary
                  ? card.value ?? 0
                  : 'Unavailable'}
            </h2>

            <p className="muted-text">{card.description}</p>
          </article>
        ))}
      </div>

      <section className="app-surface maintenance-panel">
        <div className="maintenance-panel-heading">
          <div>
            <h2>Maintenance records</h2>
            <p className="muted-text">
              Review issues and narrow the list using filters.
            </p>
          </div>

          <span className="maintenance-record-count">
            {filteredRecords.length}{' '}
            {filteredRecords.length === 1 ? 'record' : 'records'}
          </span>
        </div>

        <div className="maintenance-filters">
          <label>
            Equipment
            <select
              value={equipmentFilter}
              onChange={(event) => setEquipmentFilter(event.target.value)}
            >
              <option value="">All equipment</option>
              {equipment.map((item) => (
                <option key={item.id} value={String(item.id)}>
                  {item.name || `Equipment #${item.id}`} (ID: {item.id})
                </option>
              ))}
            </select>
          </label>

          <label>
            Status
            <select
              value={statusFilter}
              onChange={(event) => setStatusFilter(event.target.value)}
            >
              <option value="">All statuses</option>
              {statuses.map((status) => (
                <option key={status} value={status}>
                  {displayValue(status)}
                </option>
              ))}
            </select>
          </label>

          <label>
            Priority
            <select
              value={priorityFilter}
              onChange={(event) => setPriorityFilter(event.target.value)}
            >
              <option value="">All priorities</option>
              {priorities.map((priority) => (
                <option key={priority} value={priority}>
                  {displayValue(priority)}
                </option>
              ))}
            </select>
          </label>

          <button
            type="button"
            className="secondary-button"
            onClick={() => {
              setEquipmentFilter('')
              setStatusFilter('')
              setPriorityFilter('')
            }}
            disabled={
              !equipmentFilter && !statusFilter && !priorityFilter
            }
          >
            Clear filters
          </button>
        </div>

        {loading ? (
          <div className="maintenance-state" role="status">
            <span className="utilization-spinner" />
            <p>Loading maintenance records…</p>
          </div>
        ) : error && records.length === 0 ? (
          <div className="maintenance-state">
            <h3>Maintenance records unavailable</h3>
            <p className="muted-text">
              Use Retry above to request the data again.
            </p>
          </div>
        ) : filteredRecords.length === 0 ? (
          <div className="maintenance-state">
            <span className="maintenance-empty-icon" aria-hidden="true">
              ⚒
            </span>
            <h3>No maintenance records found</h3>
            <p className="muted-text">
              {records.length === 0
                ? 'Maintenance requests will appear here after they are recorded.'
                : 'Try changing or clearing the selected filters.'}
            </p>
          </div>
        ) : (
          <div className="maintenance-table-wrapper">
            <table className="maintenance-table">
              <thead>
                <tr>
                  <th>Record</th>
                  <th>Equipment</th>
                  <th>Description</th>
                  <th>Priority</th>
                  <th>Status</th>
                  <th>Reported by</th>
                  <th>Assigned technician</th>
                  <th>Reported at</th>
                  <th>Resolution notes</th>
                </tr>
              </thead>

              <tbody>
                {filteredRecords.map((record) => (
                  <tr key={record.id}>
                    <td>#{record.id}</td>

                    <td>
                      <strong>{equipmentName(record.equipmentId)}</strong>
                      <span className="maintenance-cell-subtitle">
                        ID: {record.equipmentId ?? '—'}
                      </span>
                    </td>

                    <td className="maintenance-description">
                      {record.description || '—'}
                    </td>

                    <td>
                      <span className={priorityClass(record.priority)}>
                        {displayValue(record.priority)}
                      </span>
                    </td>

                    <td>
                      <span className={statusClass(record.status)}>
                        {displayValue(record.status)}
                      </span>
                    </td>

                    <td>{record.reportedBy ?? '—'}</td>
                    <td>{record.assignedTo ?? 'Unassigned'}</td>
                    <td>{formatDate(record.reportedAt)}</td>
                    <td>{record.resolutionNotes || '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </section>
  )
}
