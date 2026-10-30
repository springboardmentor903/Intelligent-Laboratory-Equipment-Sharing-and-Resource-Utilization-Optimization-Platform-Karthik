import { useEffect, useMemo, useRef, useState } from 'react'
import api from '../api/axiosInstance'
import { useAuth } from '../context/AuthContext'
import { getMaintenanceAccessPlan } from '../access/roleAccess.js'
import {
  buildAssignmentPayload,
  buildMaintenanceCreatePayload,
  buildMaintenanceDateRangeParams,
  buildResolutionPayload,
  canCreateMaintenance,
  getMaintenanceActionPlan,
  MAINTENANCE_PRIORITIES,
  validateMaintenanceCreate,
  validateMaintenanceDateRange,
  validateResolutionNotes,
  validateTechnicianId,
} from '../access/maintenanceWorkflow.js'

const STATUSES = ['REPORTED', 'ASSIGNED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED']

function errorMessage(error) {
  if (error.response?.status === 401) return 'Your session has expired. Sign in again to continue.'
  if (error.response?.status === 403) return 'Access denied. Your role or institution scope does not allow this maintenance action.'
  return error.response?.data?.message || error.response?.data?.error || error.message || 'Unable to load maintenance information.'
}

function displayValue(value) {
  return String(value || '—').replaceAll('_', ' ').toLowerCase().replace(/\b\w/g, (letter) => letter.toUpperCase())
}

function formatDate(value) {
  if (!value) return '—'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString()
}

export default function Maintenance() {
  const { user } = useAuth()
  const rolesKey = Array.isArray(user?.roles) ? user.roles.join('|') : ''
  const accessPlan = getMaintenanceAccessPlan(user?.roles, user?.id)
  const canReport = canCreateMaintenance(user?.roles)
  const staffDateRange = Boolean(accessPlan.summaryUrl)
  const [records, setRecords] = useState([])
  const [equipment, setEquipment] = useState([])
  const [summary, setSummary] = useState(null)
  const [statusFilter, setStatusFilter] = useState('')
  const [priorityFilter, setPriorityFilter] = useState('')
  const [equipmentFilter, setEquipmentFilter] = useState('')
  const [rangeStart, setRangeStart] = useState('')
  const [rangeEnd, setRangeEnd] = useState('')
  const [activeRange, setActiveRange] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [accessDenied, setAccessDenied] = useState(false)
  const [notice, setNotice] = useState('')
  const [revision, setRevision] = useState(0)
  const [reportOpen, setReportOpen] = useState(false)
  const [reportForm, setReportForm] = useState({ equipmentId: '', description: '', priority: 'MEDIUM' })
  const [reportError, setReportError] = useState('')
  const [assignmentFor, setAssignmentFor] = useState(null)
  const [technicianId, setTechnicianId] = useState('')
  const [resolutionFor, setResolutionFor] = useState(null)
  const [resolutionNotes, setResolutionNotes] = useState('')
  const [formError, setFormError] = useState('')
  const [busy, setBusy] = useState('')
  const [historyEquipmentId, setHistoryEquipmentId] = useState('')
  const [history, setHistory] = useState(null)
  const [historyLoading, setHistoryLoading] = useState(false)
  const actionLock = useRef(false)
  const historyRequest = useRef(0)

  useEffect(() => {
    let current = true
    const requests = []
    if (accessPlan.canView) {
      const recordRequest = activeRange && staffDateRange
        ? api.get('/api/maintenance/date-range', { params: buildMaintenanceDateRangeParams(activeRange.start, activeRange.end) })
        : api.get(accessPlan.recordsUrl)
      requests.push(['records', recordRequest])
      requests.push(['equipment', api.get('/api/equipment')])
      if (accessPlan.summaryUrl) requests.push(['summary', api.get(accessPlan.summaryUrl)])
    }

    Promise.allSettled(requests.map(([, request]) => request)).then((results) => {
      if (!current) return
      if (!accessPlan.canView) {
        setRecords([])
        setEquipment([])
        setSummary(null)
        setAccessDenied(true)
        setLoading(false)
        return
      }
      setAccessDenied(false)
      const resultMap = Object.fromEntries(requests.map(([name], index) => [name, results[index]]))
      const failures = []
      const recordsResult = resultMap.records
      if (recordsResult.status === 'fulfilled') setRecords(Array.isArray(recordsResult.value.data) ? recordsResult.value.data : [])
      else {
        setRecords([])
        failures.push(`Maintenance records: ${errorMessage(recordsResult.reason)}`)
      }
      const equipmentResult = resultMap.equipment
      if (equipmentResult.status === 'fulfilled') setEquipment(Array.isArray(equipmentResult.value.data) ? equipmentResult.value.data : [])
      else {
        setEquipment([])
        failures.push(`Equipment: ${errorMessage(equipmentResult.reason)}`)
      }
      const summaryResult = resultMap.summary
      if (!summaryResult) setSummary(null)
      else if (summaryResult.status === 'fulfilled') setSummary(summaryResult.value.data)
      else {
        setSummary(null)
        failures.push(`Summary: ${errorMessage(summaryResult.reason)}`)
      }
      setError(failures.join(' | '))
      setLoading(false)
    })

    return () => { current = false }
  }, [user?.id, rolesKey, accessPlan.canView, accessPlan.recordsUrl, accessPlan.summaryUrl, activeRange, staffDateRange, revision])

  const filteredRecords = useMemo(() => records.filter((record) => (
    (!statusFilter || String(record.status || '').toUpperCase() === statusFilter)
    && (!priorityFilter || String(record.priority || '').toUpperCase() === priorityFilter)
    && (!equipmentFilter || String(record.equipmentId) === equipmentFilter)
  )), [records, statusFilter, priorityFilter, equipmentFilter])

  const equipmentName = (equipmentId) => equipment.find((item) => String(item.id) === String(equipmentId))?.name || `Equipment #${equipmentId ?? '—'}`

  function refreshData() {
    setLoading(true)
    setError('')
    setNotice('')
    setRevision((value) => value + 1)
  }

  async function mutate(key, request, success) {
    if (actionLock.current) return false
    actionLock.current = true
    setBusy(key)
    setFormError('')
    setError('')
    setNotice('')
    try {
      await request()
      setNotice(success)
      if (key === 'report') setReportOpen(false)
      if (key.startsWith('assign-')) setAssignmentFor(null)
      if (key.startsWith('resolution-')) setResolutionFor(null)
      setFormError('')
      refreshData()
      return true
    } catch (requestError) {
      setFormError(errorMessage(requestError))
      return false
    } finally {
      actionLock.current = false
      setBusy('')
    }
  }

  function submitReport(event) {
    event.preventDefault()
    const validation = validateMaintenanceCreate(reportForm)
    if (validation) {
      setReportError(validation)
      return
    }
    setReportError('')
    mutate('report', () => api.post('/api/maintenance', buildMaintenanceCreatePayload(reportForm)), 'Maintenance issue reported.')
      .then((saved) => { if (saved) setReportForm({ equipmentId: '', description: '', priority: 'MEDIUM' }) })
  }

  function submitAssignment(event, recordId) {
    event.preventDefault()
    const validation = validateTechnicianId(technicianId)
    if (validation) {
      setFormError(validation)
      return
    }
    mutate(`assign-${recordId}`, () => api.patch(`/api/maintenance/${recordId}/assign`, buildAssignmentPayload(technicianId)), 'Technician assigned.')
  }

  function changeStatus(record, status) {
    mutate(`status-${record.id}`, () => api.patch(`/api/maintenance/${record.id}/status`, { status }), `Maintenance status updated to ${displayValue(status)}.`)
  }

  function saveResolution(event, recordId) {
    event.preventDefault()
    const validation = validateResolutionNotes(resolutionNotes)
    if (validation) {
      setFormError(validation)
      return
    }
    mutate(`resolution-${recordId}`, () => api.patch(`/api/maintenance/${recordId}/resolution`, buildResolutionPayload(resolutionNotes)), 'Resolution notes saved.')
  }

  async function showEquipmentHistory() {
    if (!historyEquipmentId) return
    const requestId = historyRequest.current + 1
    historyRequest.current = requestId
    setHistoryLoading(true)
    setHistory(null)
    setError('')
    try {
      const response = await api.get(`/api/maintenance/equipment/${encodeURIComponent(historyEquipmentId)}/history`)
      if (historyRequest.current === requestId) setHistory(Array.isArray(response.data) ? response.data : [])
    } catch (requestError) {
      if (historyRequest.current === requestId) setError(errorMessage(requestError))
    } finally {
      if (historyRequest.current === requestId) setHistoryLoading(false)
    }
  }

  function applyDateRange(event) {
    event.preventDefault()
    const validation = validateMaintenanceDateRange(rangeStart, rangeEnd)
    if (validation) {
      setError(validation)
      return
    }
    setError('')
    setLoading(true)
    setActiveRange(buildMaintenanceDateRangeParams(rangeStart, rangeEnd))
  }

  const summaryCards = [
    ['Total records', summary?.totalRecords], ['Reported', summary?.reported],
    ['Assigned', summary?.assigned], ['In progress', summary?.inProgress],
    ['Completed', summary?.completed], ['Cancelled', summary?.cancelled],
    ['High priority', summary?.highPriority], ['Critical priority', summary?.criticalPriority],
  ]

  return (
    <section className="page-container maintenance-page">
      <div className="maintenance-heading">
        <div>
          <p className="page-eyebrow">OPERATIONS &amp; EQUIPMENT CARE</p>
          <h1>Maintenance Management</h1>
          <p className="muted-text">Report equipment issues and follow their maintenance progress.</p>
        </div>
        <div className="maintenance-actions">
          {canReport && <button type="button" className="primary-button" onClick={() => { setReportOpen((open) => !open); setReportError('') }}>Report issue</button>}
          <button type="button" className="secondary-button" onClick={refreshData} disabled={loading}>{loading ? 'Refreshing…' : 'Refresh'}</button>
        </div>
      </div>

      {notice && <div className="auth-success" role="status">{notice}</div>}
      {error && <div className="auth-alert" role="alert">{error}</div>}
      {formError && <div className="auth-alert" role="alert">{formError}</div>}
      {accessDenied ? <div className="auth-alert" role="alert">Maintenance records are not available for this account. Sign in with a supported role to continue.</div> : <>
        {reportOpen && canReport && <form className="app-surface maintenance-panel" onSubmit={submitReport}>
          <h2>Report maintenance issue</h2>
          <p className="muted-text">Your identity is taken from your authenticated session.</p>
          <div className="maintenance-filters">
            <label>Equipment<select required value={reportForm.equipmentId} onChange={(event) => setReportForm({ ...reportForm, equipmentId: event.target.value })}><option value="">Select equipment</option>{equipment.map((item) => <option key={item.id} value={item.id}>{item.name || `Equipment #${item.id}`} (ID: {item.id})</option>)}</select></label>
            <label>Priority<select value={reportForm.priority} onChange={(event) => setReportForm({ ...reportForm, priority: event.target.value })}>{MAINTENANCE_PRIORITIES.map((priority) => <option key={priority} value={priority}>{displayValue(priority)}</option>)}</select></label>
            <label className="maintenance-description">Description<textarea required maxLength={1000} value={reportForm.description} onChange={(event) => setReportForm({ ...reportForm, description: event.target.value })} /></label>
          </div>
          {reportError && <p className="auth-alert" role="alert">{reportError}</p>}
          <button className="primary-button" type="submit" disabled={busy !== ''}>{busy === 'report' ? 'Submitting…' : 'Submit report'}</button>
        </form>}

        {staffDateRange && <form className="app-surface maintenance-panel maintenance-filters" onSubmit={applyDateRange}>
          <label>Reported from<input type="datetime-local" value={rangeStart} onChange={(event) => setRangeStart(event.target.value)} /></label>
          <label>Reported to<input type="datetime-local" value={rangeEnd} onChange={(event) => setRangeEnd(event.target.value)} /></label>
          <button className="secondary-button" type="submit" disabled={loading}>Apply date range</button>
          {activeRange && <button className="secondary-button" type="button" onClick={() => { setLoading(true); setActiveRange(null) }}>Clear date range</button>}
        </form>}

        {staffDateRange && <div className="maintenance-summary-grid">{summaryCards.map(([label, value]) => <article className="app-surface maintenance-stat" key={label}><span className="muted-text">{label}</span><h2>{loading && summary === null ? '…' : summary ? value ?? 0 : 'Unavailable'}</h2></article>)}</div>}
        {!staffDateRange && <div className="auth-success" role="status">Showing maintenance requests reported by you. Aggregate summaries and institution-wide date filters are available to staff roles.</div>}

        <section className="app-surface maintenance-panel">
          <div className="maintenance-panel-heading"><div><h2>Maintenance records</h2><p className="muted-text">Records follow your account’s ownership and institution scope.</p></div><span className="maintenance-record-count">{filteredRecords.length} {filteredRecords.length === 1 ? 'record' : 'records'}</span></div>
          <div className="maintenance-filters">
            <label>Equipment<select value={equipmentFilter} onChange={(event) => setEquipmentFilter(event.target.value)}><option value="">All equipment</option>{equipment.map((item) => <option key={item.id} value={String(item.id)}>{item.name || `Equipment #${item.id}`} (ID: {item.id})</option>)}</select></label>
            <label>Status<select value={statusFilter} onChange={(event) => setStatusFilter(event.target.value)}><option value="">All statuses</option>{STATUSES.map((status) => <option key={status} value={status}>{displayValue(status)}</option>)}</select></label>
            <label>Priority<select value={priorityFilter} onChange={(event) => setPriorityFilter(event.target.value)}><option value="">All priorities</option>{MAINTENANCE_PRIORITIES.map((priority) => <option key={priority} value={priority}>{displayValue(priority)}</option>)}</select></label>
            <button type="button" className="secondary-button" onClick={() => { setEquipmentFilter(''); setStatusFilter(''); setPriorityFilter('') }} disabled={!equipmentFilter && !statusFilter && !priorityFilter}>Clear filters</button>
          </div>
          {loading ? <div className="maintenance-state" role="status"><span className="utilization-spinner" /><p>Loading maintenance records…</p></div> : error && records.length === 0 ? <div className="maintenance-state"><h3>Maintenance records unavailable</h3></div> : filteredRecords.length === 0 ? <div className="maintenance-state"><h3>No maintenance records found</h3><p className="muted-text">{records.length === 0 ? 'Maintenance requests will appear here after they are recorded.' : 'Try changing or clearing the selected filters.'}</p></div> : <div className="maintenance-table-wrapper"><table className="maintenance-table"><thead><tr><th>Record</th><th>Equipment</th><th>Description</th><th>Priority</th><th>Status</th><th>Reported by</th><th>Assigned technician</th><th>Reported at</th><th>Started at</th><th>Completed at</th><th>Resolution notes</th><th>Actions</th></tr></thead><tbody>{filteredRecords.map((record) => {
            const actions = getMaintenanceActionPlan(user?.roles, record)
            const actionBusy = busy.endsWith(`-${record.id}`)
            return <tr key={record.id}>
              <td>#{record.id}</td><td><strong>{equipmentName(record.equipmentId)}</strong><span className="maintenance-cell-subtitle">ID: {record.equipmentId ?? '—'}</span></td><td className="maintenance-description">{record.description || '—'}</td><td>{displayValue(record.priority)}</td><td>{displayValue(record.status)}</td><td>{record.reportedBy ?? '—'}</td><td>{record.assignedTo ?? 'Unassigned'}</td><td>{formatDate(record.reportedAt)}</td><td>{formatDate(record.startedAt)}</td><td>{formatDate(record.completedAt)}</td><td>{record.resolutionNotes || '—'}</td><td><div className="maintenance-actions">
                {actions.canAssign && <button className="secondary-button" type="button" onClick={() => { setAssignmentFor(assignmentFor === record.id ? null : record.id); setTechnicianId(''); setFormError('') }}>Assign</button>}
                {actions.canChangeStatus && actions.transitions.map((status) => <button className="secondary-button" type="button" key={status} disabled={actionBusy} onClick={() => changeStatus(record, status)}>{actionBusy ? 'Saving…' : displayValue(status)}</button>)}
                {actions.canUpdateResolution && ['IN_PROGRESS', 'COMPLETED'].includes(String(record.status).toUpperCase()) && <button className="secondary-button" type="button" onClick={() => { setResolutionFor(resolutionFor === record.id ? null : record.id); setResolutionNotes(record.resolutionNotes || ''); setFormError('') }}>Resolution</button>}
                {actions.canDelete && <button className="secondary-button" type="button" disabled={actionBusy} onClick={() => { if (window.confirm(`Delete maintenance record #${record.id}?`)) mutate(`delete-${record.id}`, () => api.delete(`/api/maintenance/${record.id}`), 'Maintenance record deleted.') }}>Delete</button>}
                {assignmentFor === record.id && <form onSubmit={(event) => submitAssignment(event, record.id)}><label>Technician ID<input type="number" min="1" step="1" required value={technicianId} onChange={(event) => setTechnicianId(event.target.value)} /></label><small>The server verifies technician role and institution access; no user directory endpoint is available.</small><button type="submit" className="primary-button" disabled={busy !== ''}>{busy === `assign-${record.id}` ? 'Assigning…' : 'Save assignment'}</button></form>}
                {resolutionFor === record.id && <form onSubmit={(event) => saveResolution(event, record.id)}><label>Resolution notes<textarea maxLength={1000} required value={resolutionNotes} onChange={(event) => setResolutionNotes(event.target.value)} /></label><button type="submit" className="primary-button" disabled={busy !== ''}>{busy === `resolution-${record.id}` ? 'Saving…' : 'Save resolution'}</button></form>}
                {!actions.canAssign && !actions.canChangeStatus && !actions.canUpdateResolution && !actions.canDelete && <span className="muted-text">View only</span>}
              </div></td>
            </tr>
          })}</tbody></table></div>}
        </section>

        <section className="app-surface maintenance-panel"><h2>Equipment maintenance history</h2><p className="muted-text">History uses the equipment-scoped endpoint and remains filtered by backend access rules.</p><div className="maintenance-filters"><label>Equipment<select value={historyEquipmentId} onChange={(event) => { historyRequest.current += 1; setHistoryEquipmentId(event.target.value); setHistory(null); setHistoryLoading(false) }}><option value="">Select equipment</option>{equipment.map((item) => <option key={item.id} value={String(item.id)}>{item.name || `Equipment #${item.id}`} (ID: {item.id})</option>)}</select></label><button className="secondary-button" type="button" disabled={!historyEquipmentId || historyLoading} onClick={showEquipmentHistory}>{historyLoading ? 'Loading…' : 'View history'}</button></div>{historyLoading ? <p role="status">Loading history…</p> : history && (history.length ? <ul>{history.map((record) => <li key={record.id}>#{record.id} · {displayValue(record.status)} · {record.description} · {formatDate(record.reportedAt)}</li>)}</ul> : <p>No maintenance history for this equipment.</p>)}</section>
      </>}
    </section>
  )
}
