import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import api from '../api/axiosInstance'
import { useAuth } from '../context/AuthContext'
import {
  getUtilizationPermissions,
  utilizationApiError,
  validateDateRange,
  validateUtilizationCreate,
  validateUtilizationUpdate,
} from '../access/utilizationWorkflow.js'

const blankCreateForm = {
  equipmentId: '', bookingId: '', startTime: '', endTime: '', notes: '',
}

function formatDate(value) {
  if (!value) return '—'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString()
}

function inputDateTime(value) {
  return value ? String(value).slice(0, 16) : ''
}

export default function Utilization() {
  const { user } = useAuth()
  const permissions = useMemo(() => getUtilizationPermissions(user?.roles), [user?.roles])
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
  const [mutationSuccess, setMutationSuccess] = useState('')
  const [mutationError, setMutationError] = useState('')
  const [formMode, setFormMode] = useState(null)
  const [selectedRecordId, setSelectedRecordId] = useState(null)
  const [createForm, setCreateForm] = useState(blankCreateForm)
  const [editForm, setEditForm] = useState({ endTime: '', notes: '' })
  const [formErrors, setFormErrors] = useState([])
  const [mutationLoading, setMutationLoading] = useState(false)
  const [deletingId, setDeletingId] = useState(null)

  const snapshotRequestId = useRef(0)
  const dateRequestId = useRef(0)
  const selectedRecordIdRef = useRef(null)
  const mutationLock = useRef(false)
  const deleteLock = useRef(false)
  const snapshotPromise = useRef(null)

  const fetchSnapshot = useCallback(() => {
    if (snapshotPromise.current) return snapshotPromise.current
    const request = Promise.allSettled([
      api.get('/api/utilization'),
      api.get('/api/equipment'),
      api.get('/api/utilization/summary'),
    ])
    snapshotPromise.current = request
    request.then(
      () => { if (snapshotPromise.current === request) snapshotPromise.current = null },
      () => { if (snapshotPromise.current === request) snapshotPromise.current = null },
    )
    return request
  }, [])

  const applySnapshot = useCallback((results) => {
    const [recordsResult, equipmentResult, summaryResult] = results
    const errors = []
    const accessFailure = results.find((result) => result.status === 'rejected'
      && [401, 403].includes(result.reason?.response?.status))
    if (recordsResult.status === 'fulfilled') {
      const nextRecords = Array.isArray(recordsResult.value.data) ? recordsResult.value.data : []
      setRecords(nextRecords)
      const selectedId = selectedRecordIdRef.current
      if (selectedId != null && !nextRecords.some((record) => String(record.id) === String(selectedId))) {
        selectedRecordIdRef.current = null
        setSelectedRecordId(null)
        setFormMode(null)
      }
    } else {
      if ([401, 403].includes(recordsResult.reason?.response?.status)) {
        setRecords([])
        selectedRecordIdRef.current = null
        setSelectedRecordId(null)
        setFormMode(null)
      }
      if (!accessFailure) errors.push(`Records: ${utilizationApiError(recordsResult.reason, 'Unable to load utilization records.')}`)
    }
    if (equipmentResult.status === 'fulfilled') {
      setEquipment(Array.isArray(equipmentResult.value.data) ? equipmentResult.value.data : [])
    } else {
      if ([401, 403].includes(equipmentResult.reason?.response?.status)) setEquipment([])
      if (!accessFailure) errors.push(`Equipment: ${utilizationApiError(equipmentResult.reason, 'Unable to load equipment.')}`)
    }
    if (summaryResult.status === 'fulfilled') {
      setSummary(summaryResult.value.data)
    } else {
      setSummary(null)
      if (!accessFailure) errors.push(`Summary: ${utilizationApiError(summaryResult.reason, 'Unable to load utilization summary.')}`)
    }
    setError(accessFailure
      ? utilizationApiError(accessFailure.reason, 'Access denied.')
      : errors.join(' | '))
  }, [])

  const refreshData = useCallback(async () => {
    const requestId = ++snapshotRequestId.current
    snapshotPromise.current = null
    setLoading(true)
    setSummaryLoading(true)
    setError('')
    try {
      const results = await fetchSnapshot()
      if (requestId === snapshotRequestId.current) applySnapshot(results)
    } catch (requestError) {
      if (requestId === snapshotRequestId.current) {
        setError(utilizationApiError(requestError, 'Unable to load utilization data.'))
      }
    } finally {
      if (requestId === snapshotRequestId.current) {
        setLoading(false)
        setSummaryLoading(false)
      }
    }
  }, [applySnapshot, fetchSnapshot])

  useEffect(() => {
    const requestId = ++snapshotRequestId.current
    let active = true
    fetchSnapshot()
      .then((results) => {
        if (active && requestId === snapshotRequestId.current) applySnapshot(results)
      })
      .catch((requestError) => {
        if (active && requestId === snapshotRequestId.current) {
          setError(utilizationApiError(requestError, 'Unable to load utilization data.'))
        }
      })
      .finally(() => {
        if (active && requestId === snapshotRequestId.current) {
          setLoading(false)
          setSummaryLoading(false)
        }
      })
    return () => { active = false }
  }, [applySnapshot, fetchSnapshot])

  useEffect(() => () => {
    snapshotRequestId.current += 1
    dateRequestId.current += 1
  }, [])

  async function loadDateSummary(event) {
    event.preventDefault()
    if (dateLoading) return
    const { errors, params } = validateDateRange(startDate, endDate)
    setDateError(errors.join(' '))
    setDateSummary(null)
    setDateSuccess(false)
    if (errors.length) return
    const requestId = ++dateRequestId.current
    setDateLoading(true)
    try {
      const response = await api.get('/api/utilization/summary/date-range', { params })
      if (requestId === dateRequestId.current) {
        setDateSummary(response.data)
        setDateSuccess(true)
      }
    } catch (requestError) {
      if (requestId === dateRequestId.current) {
        setDateError(utilizationApiError(requestError, 'Unable to calculate date-range usage.'))
      }
    } finally {
      if (requestId === dateRequestId.current) setDateLoading(false)
    }
  }

  function beginCreate() {
    setFormErrors([])
    setMutationError('')
    setMutationSuccess('')
    setCreateForm(blankCreateForm)
    setSelectedRecordId(null)
    selectedRecordIdRef.current = null
    setFormMode('create')
  }

  function beginEdit(record) {
    setFormErrors([])
    setMutationError('')
    setMutationSuccess('')
    setSelectedRecordId(record.id)
    selectedRecordIdRef.current = record.id
    setEditForm({ endTime: inputDateTime(record.endTime), notes: record.notes ?? '' })
    setFormMode('edit')
  }

  function closeForm() {
    setFormMode(null)
    setSelectedRecordId(null)
    selectedRecordIdRef.current = null
    setFormErrors([])
  }

  function changeDateRange(nextStart, nextEnd) {
    dateRequestId.current += 1
    setStartDate(nextStart)
    setEndDate(nextEnd)
    setDateSummary(null)
    setDateSuccess(false)
    setDateError('')
  }

  function changeRecordFilter(field, value) {
    const nextEquipmentFilter = field === 'equipment' ? value : equipmentFilter
    const nextUserFilter = field === 'user' ? value : userFilter
    if (formMode === 'edit') {
      const selected = records.find((record) => String(record.id) === String(selectedRecordId))
      if (selected && ((nextEquipmentFilter && String(selected.equipmentId) !== nextEquipmentFilter)
        || (nextUserFilter && String(selected.userId) !== nextUserFilter))) {
        closeForm()
      }
    }
    if (field === 'equipment') setEquipmentFilter(value)
    else setUserFilter(value)
  }

  async function submitCreate(event) {
    event.preventDefault()
    if (mutationLock.current || deleteLock.current) return
    const { errors, body } = validateUtilizationCreate(createForm)
    const validEquipment = equipment.some((item) => String(item.id) === String(body.equipmentId))
    if (!validEquipment && body.equipmentId) errors.push('Select equipment returned by the equipment API.')
    setFormErrors(errors)
    if (errors.length) return
    mutationLock.current = true
    setMutationLoading(true)
    setMutationError('')
    setMutationSuccess('')
    try {
      await api.post('/api/utilization', body)
      setMutationSuccess('Utilization record created.')
      closeForm()
      await refreshData()
    } catch (requestError) {
      setMutationError(utilizationApiError(requestError, 'Unable to create the utilization record.'))
    } finally {
      mutationLock.current = false
      setMutationLoading(false)
    }
  }

  async function submitUpdate(event) {
    event.preventDefault()
    if (mutationLock.current || deleteLock.current) return
    const record = records.find((item) => String(item.id) === String(selectedRecordId))
    if (!record) {
      setMutationError('This utilization record is no longer available. Refresh the list and try again.')
      return
    }
    const { errors, body } = validateUtilizationUpdate(editForm, record)
    setFormErrors(errors)
    if (errors.length) return
    mutationLock.current = true
    setMutationLoading(true)
    setMutationError('')
    setMutationSuccess('')
    try {
      await api.put(`/api/utilization/${record.id}`, body)
      setMutationSuccess('Utilization record updated.')
      closeForm()
      await refreshData()
    } catch (requestError) {
      setMutationError(utilizationApiError(requestError, 'Unable to update the utilization record.'))
    } finally {
      mutationLock.current = false
      setMutationLoading(false)
    }
  }

  async function deleteRecord(record) {
    if (deleteLock.current || mutationLock.current) return
    if (!window.confirm(`Delete utilization record #${record.id}? This cannot be undone.`)) return
    deleteLock.current = true
    setDeletingId(record.id)
    setMutationError('')
    setMutationSuccess('')
    try {
      await api.delete(`/api/utilization/${record.id}`)
      if (String(selectedRecordIdRef.current) === String(record.id)) closeForm()
      setMutationSuccess('Utilization record deleted.')
      await refreshData()
    } catch (requestError) {
      setMutationError(utilizationApiError(requestError, 'Unable to delete the utilization record.'))
    } finally {
      deleteLock.current = false
      setDeletingId(null)
    }
  }

  const filteredRecords = useMemo(() => records.filter((record) => {
    const matchesEquipment = !equipmentFilter || String(record.equipmentId) === equipmentFilter
    const matchesUser = !userFilter || String(record.userId) === userFilter
    return matchesEquipment && matchesUser
  }), [records, equipmentFilter, userFilter])

  const totalRecords = summary?.totalRecords ?? '—'
  const totalHours = summary?.totalUsageHours ?? '—'

  return (
    <section className="page-container utilization-page">
      <div className="utilization-heading">
        <div>
          <p className="page-eyebrow">RESOURCE ANALYTICS</p>
          <h1>Equipment Utilization</h1>
          <p className="muted-text">Review recorded equipment usage and analyze utilization hours.</p>
        </div>
        <button type="button" className="primary-button" onClick={refreshData} disabled={loading || mutationLoading}>
          {loading ? 'Refreshing…' : '↻ Refresh'}
        </button>
      </div>

      {error && <div className="auth-alert" role="alert"><p>{error}</p><button type="button" className="secondary-button" onClick={refreshData} disabled={loading}>Retry</button></div>}
      {mutationSuccess && <p className="auth-success" role="status">{mutationSuccess}</p>}
      {mutationError && <p className="auth-alert" role="alert">{mutationError}</p>}

      <div className="utilization-summary-grid">
        <article className="app-surface utilization-stat"><span className="utilization-stat-icon">▤</span><p>Total records</p><h2>{summaryLoading ? '…' : summary ? totalRecords : 'Unavailable'}</h2><span className="muted-text">Records visible to your account</span></article>
        <article className="app-surface utilization-stat"><span className="utilization-stat-icon">◷</span><p>Total usage hours</p><h2>{summaryLoading ? '…' : summary ? totalHours : 'Unavailable'}</h2><span className="muted-text">Based on visible saved records</span></article>
        <article className="app-surface utilization-stat"><span className="utilization-stat-icon">▣</span><p>Equipment tracked</p><h2>{loading ? '…' : equipment.length}</h2><span className="muted-text">Equipment returned by the API</span></article>
      </div>

      <section className="app-surface utilization-panel">
        <div className="utilization-panel-heading"><div><h2>Usage by date range</h2><p className="muted-text">Calculate total recorded usage hours between two dates.</p></div></div>
        <form className="utilization-date-form" onSubmit={loadDateSummary}>
          <label>Start date<input type="date" value={startDate} max={endDate || undefined} onChange={(event) => changeDateRange(event.target.value, endDate)} required disabled={dateLoading} /></label>
          <label>End date<input type="date" value={endDate} min={startDate || undefined} onChange={(event) => changeDateRange(startDate, event.target.value)} required disabled={dateLoading} /></label>
          <button className="primary-button" type="submit" disabled={dateLoading}>{dateLoading ? 'Calculating…' : 'Calculate'}</button>
        </form>
        {dateError && <p className="utilization-inline-error" role="alert">{dateError}</p>}
        {dateSuccess && dateSummary && <div className="utilization-date-result" aria-live="polite"><span>Total usage hours for the selected period</span><strong>{dateSummary.totalUsageHours ?? '—'}</strong><span className="muted-text">{formatDate(dateSummary.start)} — {formatDate(dateSummary.end)}</span></div>}
      </section>

      {permissions.canCreate && <section className="app-surface utilization-panel">
        <div className="utilization-panel-heading"><div><h2>Record equipment use</h2><p className="muted-text">The server assigns the record to your authenticated account.</p></div></div>
        {formMode !== 'create' ? <button type="button" className="primary-button" onClick={beginCreate} disabled={mutationLoading || deletingId != null}>Create utilization record</button> : (
          <form className="utilization-date-form" onSubmit={submitCreate}>
            <label>Equipment *<select value={createForm.equipmentId} onChange={(event) => setCreateForm({ ...createForm, equipmentId: event.target.value })} required disabled={mutationLoading || loading}><option value="">Select equipment</option>{equipment.map((item) => <option key={item.id} value={item.id}>{item.name || `Equipment #${item.id}`} (ID: {item.id})</option>)}</select></label>
            <label>Start time *<input type="datetime-local" value={createForm.startTime} onChange={(event) => setCreateForm({ ...createForm, startTime: event.target.value })} required disabled={mutationLoading} /></label>
            <label>End time (optional)<input type="datetime-local" value={createForm.endTime} onChange={(event) => setCreateForm({ ...createForm, endTime: event.target.value })} disabled={mutationLoading} /></label>
            <label>Booking ID (optional)<input type="number" min="1" value={createForm.bookingId} onChange={(event) => setCreateForm({ ...createForm, bookingId: event.target.value })} disabled={mutationLoading} /></label>
            <label>Notes<textarea maxLength={500} value={createForm.notes} onChange={(event) => setCreateForm({ ...createForm, notes: event.target.value })} disabled={mutationLoading} /></label>
            {formErrors.length > 0 && <div className="utilization-inline-error" role="alert">{formErrors.map((message) => <p key={message}>{message}</p>)}</div>}
            <div><button className="primary-button" type="submit" disabled={mutationLoading}>{mutationLoading ? 'Saving…' : 'Save record'}</button>{' '}<button type="button" className="secondary-button" onClick={closeForm} disabled={mutationLoading}>Cancel</button></div>
          </form>
        )}
      </section>}

      {formMode === 'edit' && permissions.canEdit && (() => {
        const record = records.find((item) => String(item.id) === String(selectedRecordId))
        if (!record) return null
        return <section className="app-surface utilization-panel">
          <div className="utilization-panel-heading"><div><h2>Edit utilization record #{record.id}</h2><p className="muted-text">Equipment, user, booking, and start time are not editable through this API.</p></div></div>
          <form className="utilization-date-form" onSubmit={submitUpdate}>
            <label>End time (blank leaves the existing end time unchanged)<input type="datetime-local" value={editForm.endTime} onChange={(event) => setEditForm({ ...editForm, endTime: event.target.value })} disabled={mutationLoading} /></label>
            <label>Notes<textarea maxLength={500} value={editForm.notes} onChange={(event) => setEditForm({ ...editForm, notes: event.target.value })} disabled={mutationLoading} /></label>
            {formErrors.length > 0 && <div className="utilization-inline-error" role="alert">{formErrors.map((message) => <p key={message}>{message}</p>)}</div>}
            <div><button className="primary-button" type="submit" disabled={mutationLoading}>{mutationLoading ? 'Saving…' : 'Save changes'}</button>{' '}<button type="button" className="secondary-button" onClick={closeForm} disabled={mutationLoading}>Cancel</button></div>
          </form>
        </section>
      })()}

      <section className="app-surface utilization-panel">
        <div className="utilization-panel-heading"><div><h2>Utilization records</h2><p className="muted-text">Records are scoped by the backend to your role and institution.</p></div><span className="utilization-record-count">{filteredRecords.length} {filteredRecords.length === 1 ? 'record' : 'records'}</span></div>
        <div className="utilization-filters">
          <label>Equipment<select value={equipmentFilter} onChange={(event) => changeRecordFilter('equipment', event.target.value)}><option value="">All equipment</option>{equipment.map((item) => <option key={item.id} value={String(item.id)}>{item.name || `Equipment #${item.id}`} (ID: {item.id})</option>)}</select></label>
          <label>User ID<input type="number" min="1" placeholder="Filter visible records" value={userFilter} onChange={(event) => changeRecordFilter('user', event.target.value)} /></label>
          <button type="button" className="secondary-button" onClick={() => { changeRecordFilter('equipment', ''); changeRecordFilter('user', '') }} disabled={!equipmentFilter && !userFilter}>Clear filters</button>
        </div>
        {loading ? <div className="utilization-state" role="status"><span className="utilization-spinner" /><p>Loading utilization records…</p></div>
          : error && records.length === 0 ? <div className="utilization-state"><h3>Utilization records unavailable</h3><p className="muted-text">The request failed; this is not an empty result.</p></div>
            : filteredRecords.length === 0 ? <div className="utilization-state"><span className="utilization-empty-icon">◷</span><h3>No utilization records found</h3><p className="muted-text">Records will appear here when usage data has been saved.</p></div>
              : <div className="utilization-table-wrapper"><table className="utilization-table"><thead><tr><th>Record ID</th><th>Equipment ID</th><th>User ID</th><th>Booking ID</th><th>Start time</th><th>End time</th><th>Usage hours</th><th>Notes</th>{(permissions.canEdit || permissions.canDelete) && <th>Actions</th>}</tr></thead><tbody>{filteredRecords.map((record) => <tr key={record.id}><td>#{record.id}</td><td>{record.equipmentId ?? '—'}</td><td>{record.userId ?? '—'}</td><td>{record.bookingId ?? '—'}</td><td>{formatDate(record.startTime)}</td><td>{formatDate(record.endTime)}</td><td><strong>{record.actualHours ?? '—'}</strong></td><td>{record.notes || '—'}</td>{(permissions.canEdit || permissions.canDelete) && <td>{permissions.canEdit && <button type="button" className="secondary-button" disabled={mutationLoading || deletingId != null} onClick={() => beginEdit(record)}>Edit</button>}{' '}{permissions.canDelete && <button type="button" className="secondary-button" disabled={mutationLoading || deletingId != null} onClick={() => deleteRecord(record)}>{deletingId === record.id ? 'Deleting…' : 'Delete'}</button>}</td>}</tr>)}</tbody></table></div>}
      </section>
    </section>
  )
}
