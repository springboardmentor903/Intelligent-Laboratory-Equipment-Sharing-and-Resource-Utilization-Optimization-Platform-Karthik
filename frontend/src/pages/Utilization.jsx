
import { useCallback, useEffect, useMemo, useState } from 'react'
import api from '../api/axiosInstance'

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
    'Something went wrong while loading utilization data.'
  )
}

export default function Utilization() {
  const [records, setRecords] = useState([])
  const [summary, setSummary] = useState(null)
  const [equipment, setEquipment] = useState([])

  const [equipmentFilter, setEquipmentFilter] = useState('')
  const [userFilter, setUserFilter] = useState('')
  const [startDate, setStartDate] = useState('')
  const [endDate, setEndDate] = useState('')
  const [dateSummary, setDateSummary] = useState(null)

  const [loading, setLoading] = useState(true)
  const [summaryLoading, setSummaryLoading] = useState(true)
  const [dateLoading, setDateLoading] = useState(false)

  const [error, setError] = useState('')
  const [dateError, setDateError] = useState('')
  const [dateSuccess, setDateSuccess] = useState(false)

  const loadData = useCallback(async () => {
    setLoading(true)
    setSummaryLoading(true)
    setError('')

    try {
      const results = await Promise.allSettled([
        api.get('/api/utilization'),
        api.get('/api/equipment'),
        api.get('/api/utilization/summary'),
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
          `Records: ${getErrorMessage(recordsResult.reason)}`,
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
      setSummaryLoading(false)
    }
  }, [])

  useEffect(() => {
    loadData()
  }, [loadData])

  async function loadDateSummary(event) {
    event.preventDefault()
    setDateError('')
    setDateSummary(null)
    setDateSuccess(false)

    if (!startDate || !endDate) {
      setDateError('Select both a start date and an end date.')
      return
    }

    if (startDate > endDate) {
      setDateError('The start date must not be after the end date.')
      return
    }

    setDateLoading(true)

    try {
      const response = await api.get(
        '/api/utilization/summary/date-range',
        {
          params: {
            start: `${startDate}T00:00:00`,
            end: `${endDate}T23:59:59`,
          },
        },
      )

      setDateSummary(response.data)
      setDateSuccess(true)
    } catch (requestError) {
      setDateError(getErrorMessage(requestError))
    } finally {
      setDateLoading(false)
    }
  }

  const filteredRecords = useMemo(() => {
    return records.filter((record) => {
      const matchesEquipment =
        !equipmentFilter ||
        String(record.equipmentId) === equipmentFilter

      const matchesUser =
        !userFilter ||
        String(record.userId) === userFilter

      return matchesEquipment && matchesUser
    })
  }, [records, equipmentFilter, userFilter])

  const totalRecords = summary?.totalRecords ?? '—'
  const totalHours = summary?.totalUsageHours ?? '—'

  return (
    <section className="page-container utilization-page">
      <div className="utilization-heading">
        <div>
          <p className="page-eyebrow">RESOURCE ANALYTICS</p>
          <h1>Equipment Utilization</h1>
          <p className="muted-text">
            Review recorded equipment usage and analyze utilization hours.
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

      <div className="utilization-summary-grid">
        <article className="app-surface utilization-stat">
          <span className="utilization-stat-icon">▤</span>
          <p>Total records</p>
          <h2>
            {summaryLoading
              ? '…'
              : summary
                ? totalRecords
                : 'Unavailable'}
          </h2>
          <span className="muted-text">Recorded usage entries</span>
        </article>

        <article className="app-surface utilization-stat">
          <span className="utilization-stat-icon">◷</span>
          <p>Total usage hours</p>
          <h2>
            {summaryLoading
              ? '…'
              : summary
                ? totalHours
                : 'Unavailable'}
          </h2>
          <span className="muted-text">Based on saved records</span>
        </article>

        <article className="app-surface utilization-stat">
          <span className="utilization-stat-icon">▣</span>
          <p>Equipment tracked</p>
          <h2>{equipment.length}</h2>
          <span className="muted-text">
            Equipment returned by the API
          </span>
        </article>
      </div>

      <section className="app-surface utilization-panel">
        <div className="utilization-panel-heading">
          <div>
            <h2>Usage by date range</h2>
            <p className="muted-text">
              Calculate total recorded usage hours between two dates.
            </p>
          </div>
        </div>

        <form
          className="utilization-date-form"
          onSubmit={loadDateSummary}
        >
          <label>
            Start date
            <input
              type="date"
              value={startDate}
              max={endDate || undefined}
              onChange={(event) => setStartDate(event.target.value)}
              required
              disabled={dateLoading}
            />
          </label>

          <label>
            End date
            <input
              type="date"
              value={endDate}
              min={startDate || undefined}
              onChange={(event) => setEndDate(event.target.value)}
              required
              disabled={dateLoading}
            />
          </label>

          <button
            className="primary-button"
            type="submit"
            disabled={dateLoading}
          >
            {dateLoading ? 'Calculating…' : 'Calculate'}
          </button>
        </form>

        {dateError && (
          <p className="utilization-inline-error" role="alert">
            {dateError}
          </p>
        )}

        {dateSuccess && dateSummary && (
          <div className="utilization-date-result" aria-live="polite">
            <span>Total usage hours for the selected period</span>
            <strong>{dateSummary.totalUsageHours ?? '—'}</strong>
            <span className="muted-text">
              {formatDate(dateSummary.start)} —{' '}
              {formatDate(dateSummary.end)}
            </span>
          </div>
        )}
      </section>

      <section className="app-surface utilization-panel">
        <div className="utilization-panel-heading">
          <div>
            <h2>Utilization records</h2>
            <p className="muted-text">
              Filter the records returned by your backend.
            </p>
          </div>

          <span className="utilization-record-count">
            {filteredRecords.length}{' '}
            {filteredRecords.length === 1 ? 'record' : 'records'}
          </span>
        </div>

        <div className="utilization-filters">
          <label>
            Equipment
            <select
              value={equipmentFilter}
              onChange={(event) =>
                setEquipmentFilter(event.target.value)
              }
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
            User ID
            <input
              type="number"
              min="1"
              placeholder="Filter by user ID"
              value={userFilter}
              onChange={(event) => setUserFilter(event.target.value)}
            />
          </label>

          <button
            type="button"
            className="secondary-button"
            onClick={() => {
              setEquipmentFilter('')
              setUserFilter('')
            }}
            disabled={!equipmentFilter && !userFilter}
          >
            Clear filters
          </button>
        </div>

        {loading ? (
          <div className="utilization-state" role="status">
            <span className="utilization-spinner" />
            <p>Loading utilization records…</p>
          </div>
        ) : error && records.length === 0 ? (
          <div className="utilization-state">
            <h3>Utilization records unavailable</h3>
            <p className="muted-text">
              Use Retry above to request the data again.
            </p>
          </div>
        ) : filteredRecords.length === 0 ? (
          <div className="utilization-state">
            <span className="utilization-empty-icon">◷</span>
            <h3>No utilization records found</h3>
            <p className="muted-text">
              Records will appear here when usage data has been saved.
            </p>
          </div>
        ) : (
          <div className="utilization-table-wrapper">
            <table className="utilization-table">
              <thead>
                <tr>
                  <th>Record ID</th>
                  <th>Equipment ID</th>
                  <th>User ID</th>
                  <th>Booking ID</th>
                  <th>Start time</th>
                  <th>End time</th>
                  <th>Usage hours</th>
                  <th>Notes</th>
                </tr>
              </thead>

              <tbody>
                {filteredRecords.map((record) => (
                  <tr key={record.id}>
                    <td>#{record.id}</td>
                    <td>{record.equipmentId ?? '—'}</td>
                    <td>{record.userId ?? '—'}</td>
                    <td>{record.bookingId ?? '—'}</td>
                    <td>{formatDate(record.startTime)}</td>
                    <td>{formatDate(record.endTime)}</td>
                    <td>
                      <strong>{record.actualHours ?? '—'}</strong>
                    </td>
                    <td>{record.notes || '—'}</td>
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
