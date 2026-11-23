import { useEffect, useMemo, useRef, useState } from 'react'
import api from '../api/axiosInstance'
import { useAuth } from '../context/AuthContext'
import { getMaintenanceAccessPlan } from '../access/roleAccess.js'
import {
  buildAssignmentPayload,
  buildCalibrationCompletePayload,
  buildCalibrationSchedulePayload,
  buildDowntimePayload,
  buildMaintenanceCreatePayload,
  buildMaintenanceDateRangeParams,
  buildResolutionPayload,
  CALIBRATION_RESULTS,
  canCompleteCalibration,
  canCreateMaintenance,
  canScheduleCalibration,
  getCalibrationStatusBadge,
  getMaintenanceActionPlan,
  MAINTENANCE_PRIORITIES,
  MAINTENANCE_TYPES,
  validateCalibrationComplete,
  validateCalibrationSchedule,
  validateDowntimeHours,
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
  const isTechnician = Array.isArray(user?.roles) && user.roles.includes('LAB_TECHNICIAN')
  const isManagerOrAdmin = Array.isArray(user?.roles) && user.roles.some((r) => ['LAB_MANAGER', 'INSTITUTION_ADMIN', 'SYSTEM_ADMIN'].includes(r))
  const accessPlan = getMaintenanceAccessPlan(user?.roles, user?.id)
  const canReport = canCreateMaintenance(user?.roles)
  const canSchedCal = canScheduleCalibration(user?.roles)
  const canCompCal = canCompleteCalibration(user?.roles)
  const staffDateRange = Boolean(accessPlan.summaryUrl)

  const [activeTab, setActiveTab] = useState(isTechnician ? 'my-tasks' : 'work-orders')

  useEffect(() => {
    if (activeTab === 'my-tasks' && !isTechnician) {
      setActiveTab('work-orders')
    } else if (activeTab === 'calibration' && !isTechnician && !isManagerOrAdmin) {
      setActiveTab('work-orders')
    }
  }, [activeTab, isTechnician, isManagerOrAdmin])
  const [records, setRecords] = useState([])
  const [equipment, setEquipment] = useState([])
  const [summary, setSummary] = useState(null)
  const [technicians, setTechnicians] = useState([])
  const [calibrations, setCalibrations] = useState([])
  const [calibrationSummary, setCalibrationSummary] = useState(null)
  const [myTasks, setMyTasks] = useState({ maintenance: [], calibrations: [] })

  const [statusFilter, setStatusFilter] = useState('')
  const [priorityFilter, setPriorityFilter] = useState('')
  const [typeFilter, setTypeFilter] = useState('')
  const [equipmentFilter, setEquipmentFilter] = useState('')
  const [calibrationStatusFilter, setCalibrationStatusFilter] = useState('')

  const [rangeStart, setRangeStart] = useState('')
  const [rangeEnd, setRangeEnd] = useState('')
  const [activeRange, setActiveRange] = useState(null)

  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [accessDenied, setAccessDenied] = useState(false)
  const [notice, setNotice] = useState('')
  const [revision, setRevision] = useState(0)

  // Modals & form state
  const [reportOpen, setReportOpen] = useState(false)
  const [reportForm, setReportForm] = useState({
    equipmentId: '',
    description: '',
    priority: 'MEDIUM',
    maintenanceType: 'CORRECTIVE',
    scheduledDate: '',
    assignedTo: '',
  })
  const [reportError, setReportError] = useState('')

  const [assignmentFor, setAssignmentFor] = useState(null)
  const [technicianId, setTechnicianId] = useState('')

  const [resolutionFor, setResolutionFor] = useState(null)
  const [resolutionNotes, setResolutionNotes] = useState('')

  const [completionModal, setCompletionModal] = useState(null) // { recordId, downtimeHours, resolutionNotes }
  const [downtimeModal, setDowntimeModal] = useState(null) // { recordId, downtimeHours }

  const [scheduleCalOpen, setScheduleCalOpen] = useState(false)
  const [calScheduleForm, setCalScheduleForm] = useState({
    equipmentId: '',
    scheduledDate: '',
    assignedTo: '',
    notes: '',
  })
  const [calScheduleError, setCalScheduleError] = useState('')

  const [completeCalModal, setCompleteCalModal] = useState(null)
  const [calCompleteForm, setCalCompleteForm] = useState({
    calibrationDate: new Date().toISOString().split('T')[0],
    result: 'PASS',
    nextDueDate: '',
    notes: '',
    certificateNumber: '',
  })
  const [calCompleteError, setCalCompleteError] = useState('')

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

      if (isTechnician || isManagerOrAdmin) {
        requests.push(['calibrations', api.get('/api/calibrations')])
        requests.push(['calSummary', api.get('/api/calibrations/summary')])
      }

      if (isManagerOrAdmin) {
        requests.push(['technicians', api.get('/api/users/technicians')])
      }

      if (isTechnician) {
        requests.push(['myMaintenance', api.get('/api/maintenance/my-tasks')])
        requests.push(['myCalibrations', api.get('/api/calibrations/my-tasks')])
      }
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
      if (recordsResult?.status === 'fulfilled') setRecords(Array.isArray(recordsResult.value.data) ? recordsResult.value.data : [])
      else if (recordsResult) {
        setRecords([])
        failures.push(`Maintenance records: ${errorMessage(recordsResult.reason)}`)
      }

      const equipmentResult = resultMap.equipment
      if (equipmentResult?.status === 'fulfilled') setEquipment(Array.isArray(equipmentResult.value.data) ? equipmentResult.value.data : [])
      else if (equipmentResult) {
        setEquipment([])
        failures.push(`Equipment: ${errorMessage(equipmentResult.reason)}`)
      }

      const summaryResult = resultMap.summary
      if (!summaryResult) setSummary(null)
      else if (summaryResult.status === 'fulfilled') setSummary(summaryResult.value.data)
      else setSummary(null)

      const calResult = resultMap.calibrations
      if (calResult?.status === 'fulfilled') setCalibrations(Array.isArray(calResult.value.data) ? calResult.value.data : [])
      else setCalibrations([])

      const calSummaryResult = resultMap.calSummary
      if (calSummaryResult?.status === 'fulfilled') setCalibrationSummary(calSummaryResult.value.data)
      else setCalibrationSummary(null)

      const techResult = resultMap.technicians
      if (techResult?.status === 'fulfilled') setTechnicians(Array.isArray(techResult.value.data) ? techResult.value.data : [])
      else setTechnicians([])

      const myMaint = resultMap.myMaintenance?.status === 'fulfilled' && Array.isArray(resultMap.myMaintenance.value.data)
        ? resultMap.myMaintenance.value.data : []
      const myCal = resultMap.myCalibrations?.status === 'fulfilled' && Array.isArray(resultMap.myCalibrations.value.data)
        ? resultMap.myCalibrations.value.data : []
      setMyTasks({ maintenance: myMaint, calibrations: myCal })

      setError(failures.join(' | '))
      setLoading(false)
    })

    return () => { current = false }
  }, [user?.id, rolesKey, accessPlan.canView, accessPlan.recordsUrl, accessPlan.summaryUrl, activeRange, staffDateRange, isManagerOrAdmin, isTechnician, revision])

  const filteredRecords = useMemo(() => records.filter((record) => (
    (!statusFilter || String(record.status || '').toUpperCase() === statusFilter)
    && (!priorityFilter || String(record.priority || '').toUpperCase() === priorityFilter)
    && (!typeFilter || String(record.maintenanceType || '').toUpperCase() === typeFilter)
    && (!equipmentFilter || String(record.equipmentId) === equipmentFilter)
  )), [records, statusFilter, priorityFilter, typeFilter, equipmentFilter])

  const filteredCalibrations = useMemo(() => calibrations.filter((record) => {
    if (equipmentFilter && String(record.equipmentId || record.equipment?.id) !== equipmentFilter) return false
    if (!calibrationStatusFilter) return true
    const badge = getCalibrationStatusBadge(record)
    if (calibrationStatusFilter === 'SCHEDULED') return String(record.status).toUpperCase() === 'SCHEDULED'
    if (calibrationStatusFilter === 'OVERDUE') return badge.tone === 'danger' && badge.label === 'Overdue'
    if (calibrationStatusFilter === 'DUE_SOON') return badge.label === 'Due soon'
    if (calibrationStatusFilter === 'PASS') return String(record.result).toUpperCase() === 'PASS'
    return true
  }), [calibrations, equipmentFilter, calibrationStatusFilter])

  const equipmentName = (equipmentId) => equipment.find((item) => String(item.id) === String(equipmentId))?.name || `Equipment #${equipmentId ?? '—'}`
  const technicianName = (techId) => {
    if (!techId) return 'Unassigned'
    const found = technicians.find((t) => String(t.id) === String(techId))
    return found ? `${found.fullName} (ID: ${found.id})` : `Technician #${techId}`
  }

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
      if (key === 'complete-work') setCompletionModal(null)
      if (key === 'log-downtime') setDowntimeModal(null)
      if (key === 'schedule-cal') setScheduleCalOpen(false)
      if (key === 'complete-cal') setCompleteCalModal(null)
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
    mutate(
      'report',
      () => api.post('/api/maintenance', buildMaintenanceCreatePayload(reportForm)),
      reportForm.maintenanceType === 'PREVENTIVE'
        ? 'Preventive maintenance work order scheduled.'
        : 'Maintenance work order created.'
    ).then((saved) => {
      if (saved) {
        setReportForm({
          equipmentId: '',
          description: '',
          priority: 'MEDIUM',
          maintenanceType: 'CORRECTIVE',
          scheduledDate: '',
          assignedTo: '',
        })
      }
    })
  }

  function submitAssignment(event, recordId) {
    event.preventDefault()
    const validation = validateTechnicianId(technicianId)
    if (validation) {
      setFormError(validation)
      return
    }
    mutate(`assign-${recordId}`, () => api.patch(`/api/maintenance/${recordId}/assign`, buildAssignmentPayload(technicianId)), 'Technician assigned successfully.')
  }

  function changeStatus(record, status) {
    if (status === 'COMPLETED') {
      setCompletionModal({
        recordId: record.id,
        downtimeHours: record.downtimeHours || '',
        resolutionNotes: record.resolutionNotes || '',
      })
      return
    }
    mutate(
      `status-${record.id}`,
      () => api.patch(`/api/maintenance/${record.id}/status`, { status }),
      `Maintenance status updated to ${displayValue(status)}.`
    )
  }

  function submitCompletion(event) {
    event.preventDefault()
    if (!completionModal) return
    const validation = validateResolutionNotes(completionModal.resolutionNotes)
    if (validation) {
      setFormError(validation)
      return
    }
    if (completionModal.downtimeHours) {
      const dtVal = validateDowntimeHours(completionModal.downtimeHours)
      if (dtVal) {
        setFormError(dtVal)
        return
      }
    }
    mutate(
      'complete-work',
      () => api.patch(`/api/maintenance/${completionModal.recordId}/status`, {
        status: 'COMPLETED',
        downtimeHours: completionModal.downtimeHours ? Number(completionModal.downtimeHours) : undefined,
        resolutionNotes: completionModal.resolutionNotes.trim(),
      }),
      'Work order completed and downtime logged.'
    )
  }

  function submitDowntime(event) {
    event.preventDefault()
    if (!downtimeModal) return
    const val = validateDowntimeHours(downtimeModal.downtimeHours)
    if (val) {
      setFormError(val)
      return
    }
    mutate(
      'log-downtime',
      () => api.patch(`/api/maintenance/${downtimeModal.recordId}/downtime`, buildDowntimePayload(downtimeModal.downtimeHours)),
      'Downtime logged successfully.'
    )
  }

  function submitCalibrationSchedule(event) {
    event.preventDefault()
    const validation = validateCalibrationSchedule(calScheduleForm)
    if (validation) {
      setCalScheduleError(validation)
      return
    }
    setCalScheduleError('')
    mutate(
      'schedule-cal',
      () => api.post('/api/calibrations/schedule', buildCalibrationSchedulePayload(calScheduleForm)),
      'Calibration task scheduled.'
    ).then((saved) => {
      if (saved) {
        setCalScheduleForm({ equipmentId: '', scheduledDate: '', assignedTo: '', notes: '' })
      }
    })
  }

  function openCompleteCalModal(record) {
    const defaultNext = new Date()
    defaultNext.setMonth(defaultNext.getMonth() + 6)
    setCompleteCalModal(record)
    setCalCompleteForm({
      calibrationDate: new Date().toISOString().split('T')[0],
      result: 'PASS',
      nextDueDate: defaultNext.toISOString().split('T')[0],
      notes: record.notes || '',
      certificateNumber: record.certificateNumber || '',
    })
    setCalCompleteError('')
  }

  function submitCalibrationComplete(event) {
    event.preventDefault()
    if (!completeCalModal) return
    const validation = validateCalibrationComplete(calCompleteForm)
    if (validation) {
      setCalCompleteError(validation)
      return
    }
    setCalCompleteError('')
    mutate(
      'complete-cal',
      () => api.patch(`/api/calibrations/${completeCalModal.id}/complete`, buildCalibrationCompletePayload(calCompleteForm)),
      'Calibration logged and equipment compliance record updated.'
    )
  }

  async function showEquipmentHistory() {
    if (!historyEquipmentId) return
    const requestId = historyRequest.current + 1
    historyRequest.current = requestId
    setHistoryLoading(true)
    setHistory(null)
    setError('')
    try {
      const [maintRes, calRes] = await Promise.all([
        api.get(`/api/maintenance/equipment/${encodeURIComponent(historyEquipmentId)}/history`),
        api.get(`/api/equipment/${encodeURIComponent(historyEquipmentId)}/calibrations`),
      ])
      if (historyRequest.current === requestId) {
        setHistory({
          maintenance: Array.isArray(maintRes.data) ? maintRes.data : [],
          calibrations: Array.isArray(calRes.data) ? calRes.data : [],
        })
      }
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
    ['Total Records', summary?.totalRecords],
    ['Reported', summary?.reported],
    ['Assigned', summary?.assigned],
    ['In Progress', summary?.inProgress],
    ['Completed', summary?.completed],
    ['Preventive', summary?.preventive ?? 0],
    ['Corrective', summary?.corrective ?? 0],
    ['High/Critical', (Number(summary?.highPriority || 0) + Number(summary?.criticalPriority || 0))],
  ]

  const calCards = [
    ['Total Calibrations', calibrationSummary?.total ?? calibrations.length],
    ['Passed / Valid', calibrationSummary?.passed ?? 0],
    ['Scheduled Tasks', calibrationSummary?.scheduled ?? 0],
    ['Due Within 30d', calibrationSummary?.dueSoon ?? 0],
    ['Overdue', calibrationSummary?.overdue ?? 0],
  ]

  return (
    <section className="page-container maintenance-page">
      <div className="maintenance-heading">
        <div>
          <p className="page-eyebrow">MILESTONE 3 · OPERATIONS &amp; RESOURCE RELIABILITY</p>
          <h1>Maintenance &amp; Calibration</h1>
          <p className="muted-text">Manage preventive maintenance schedules, work orders, technician task assignments, and equipment calibration records.</p>
        </div>
        <div className="maintenance-actions">
          {canReport && (
            <button
              type="button"
              className="primary-button"
              onClick={() => { setReportOpen((open) => !open); setReportError('') }}
            >
              + Create Work Order
            </button>
          )}
          {canSchedCal && (
            <button
              type="button"
              className="secondary-button"
              onClick={() => { setScheduleCalOpen((open) => !open); setCalScheduleError('') }}
            >
              ⚖️ Schedule Calibration
            </button>
          )}
          <button type="button" className="secondary-button" onClick={refreshData} disabled={loading}>
            {loading ? 'Refreshing…' : 'Refresh'}
          </button>
        </div>
      </div>

      {notice && <div className="auth-success" role="status">{notice}</div>}
      {error && <div className="auth-alert" role="alert">{error}</div>}
      {formError && <div className="auth-alert" role="alert">{formError}</div>}

      {/* Primary Section Tabs */}
      <nav className="sharing-tabs-nav" aria-label="Maintenance sections" style={{ marginBottom: '16px' }}>
        <button
          type="button"
          className={`sharing-tab-btn ${activeTab === 'work-orders' ? 'button-primary' : 'button-secondary'}`}
          onClick={() => setActiveTab('work-orders')}
        >
          🔧 Work Orders &amp; Schedules ({records.length})
        </button>
        {isTechnician && (
          <button
            type="button"
            className={`sharing-tab-btn ${activeTab === 'my-tasks' ? 'button-primary' : 'button-secondary'}`}
            onClick={() => setActiveTab('my-tasks')}
          >
            📋 My Assigned Tasks ({myTasks.maintenance.length + myTasks.calibrations.length})
          </button>
        )}
        {(isTechnician || isManagerOrAdmin) && (
          <button
            type="button"
            className={`sharing-tab-btn ${activeTab === 'calibration' ? 'button-primary' : 'button-secondary'}`}
            onClick={() => setActiveTab('calibration')}
          >
            ⚖️ Calibration &amp; Compliance ({calibrations.length})
          </button>
        )}
        <button
          type="button"
          className={`sharing-tab-btn ${activeTab === 'history' ? 'button-primary' : 'button-secondary'}`}
          onClick={() => setActiveTab('history')}
        >
          📜 Equipment History
        </button>
      </nav>

      {accessDenied ? (
        <div className="auth-alert" role="alert">
          Maintenance records are not available for this account. Sign in with a supported role to continue.
        </div>
      ) : (
        <>
          {/* TAB 1: WORK ORDERS & SCHEDULES */}
          {activeTab === 'work-orders' && (
            <>
              {reportOpen && canReport && (
                <form className="app-surface maintenance-panel" onSubmit={submitReport}>
                  <h2>Create Work Order or Schedule Maintenance</h2>
                  <p className="muted-text">Work orders can be preventive schedules or corrective repairs, assigned directly to technicians.</p>
                  <div className="maintenance-filters" style={{ gridTemplateColumns: 'repeat(4, minmax(0, 1fr))' }}>
                    <label>
                      Equipment *
                      <select
                        required
                        value={reportForm.equipmentId}
                        onChange={(event) => setReportForm({ ...reportForm, equipmentId: event.target.value })}
                      >
                        <option value="">Select equipment</option>
                        {equipment.map((item) => (
                          <option key={item.id} value={item.id}>
                            {item.name || `Equipment #${item.id}`} (ID: {item.id})
                          </option>
                        ))}
                      </select>
                    </label>

                    <label>
                      Type *
                      <select
                        value={reportForm.maintenanceType}
                        onChange={(event) => setReportForm({ ...reportForm, maintenanceType: event.target.value })}
                      >
                        {MAINTENANCE_TYPES.map((type) => (
                          <option key={type} value={type}>{displayValue(type)}</option>
                        ))}
                      </select>
                    </label>

                    <label>
                      Priority *
                      <select
                        value={reportForm.priority}
                        onChange={(event) => setReportForm({ ...reportForm, priority: event.target.value })}
                      >
                        {MAINTENANCE_PRIORITIES.map((priority) => (
                          <option key={priority} value={priority}>{displayValue(priority)}</option>
                        ))}
                      </select>
                    </label>

                    <label>
                      Scheduled Date &amp; Time
                      <input
                        type="datetime-local"
                        value={reportForm.scheduledDate}
                        onChange={(event) => setReportForm({ ...reportForm, scheduledDate: event.target.value })}
                      />
                    </label>

                    {isManagerOrAdmin && (
                      <label>
                        Assign Technician
                        <select
                          value={reportForm.assignedTo}
                          onChange={(event) => setReportForm({ ...reportForm, assignedTo: event.target.value })}
                        >
                          <option value="">Select technician (optional)</option>
                          {technicians.map((tech) => (
                            <option key={tech.id} value={tech.id}>
                              {tech.fullName} ({tech.email})
                            </option>
                          ))}
                        </select>
                      </label>
                    )}

                    <label className="maintenance-description" style={{ gridColumn: 'span 3' }}>
                      Work Order Description *
                      <textarea
                        required
                        maxLength={1000}
                        rows={2}
                        placeholder="Details of required maintenance, inspection points, or symptoms..."
                        value={reportForm.description}
                        onChange={(event) => setReportForm({ ...reportForm, description: event.target.value })}
                      />
                    </label>
                  </div>
                  {reportError && <p className="auth-alert" role="alert">{reportError}</p>}
                  <div style={{ display: 'flex', gap: '10px' }}>
                    <button className="primary-button" type="submit" disabled={busy !== ''}>
                      {busy === 'report' ? 'Submitting…' : 'Submit Work Order'}
                    </button>
                    <button className="secondary-button" type="button" onClick={() => setReportOpen(false)}>
                      Cancel
                    </button>
                  </div>
                </form>
              )}

              {staffDateRange && (
                <div className="maintenance-summary-grid" style={{ gridTemplateColumns: 'repeat(4, minmax(0, 1fr))' }}>
                  {summaryCards.map(([label, value]) => (
                    <article className="app-surface maintenance-stat" key={label}>
                      <span className="muted-text">{label}</span>
                      <h2>{loading && summary === null ? '…' : summary ? value ?? 0 : '0'}</h2>
                    </article>
                  ))}
                </div>
              )}

              <section className="app-surface maintenance-panel">
                <div className="maintenance-panel-heading">
                  <div>
                    <h2>Maintenance Work Orders</h2>
                    <p className="muted-text">Work orders track issue lifecycle from reported/assigned through completion and downtime logging.</p>
                  </div>
                  <span className="maintenance-record-count">
                    {filteredRecords.length} {filteredRecords.length === 1 ? 'record' : 'records'}
                  </span>
                </div>

                <div className="maintenance-filters" style={{ gridTemplateColumns: 'repeat(4, minmax(0, 1fr)) auto' }}>
                  <label>
                    Equipment
                    <select value={equipmentFilter} onChange={(event) => setEquipmentFilter(event.target.value)}>
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
                    <select value={statusFilter} onChange={(event) => setStatusFilter(event.target.value)}>
                      <option value="">All statuses</option>
                      {STATUSES.map((status) => (
                        <option key={status} value={status}>{displayValue(status)}</option>
                      ))}
                    </select>
                  </label>
                  <label>
                    Type
                    <select value={typeFilter} onChange={(event) => setTypeFilter(event.target.value)}>
                      <option value="">All types</option>
                      {MAINTENANCE_TYPES.map((type) => (
                        <option key={type} value={type}>{displayValue(type)}</option>
                      ))}
                    </select>
                  </label>
                  <label>
                    Priority
                    <select value={priorityFilter} onChange={(event) => setPriorityFilter(event.target.value)}>
                      <option value="">All priorities</option>
                      {MAINTENANCE_PRIORITIES.map((priority) => (
                        <option key={priority} value={priority}>{displayValue(priority)}</option>
                      ))}
                    </select>
                  </label>
                  <button
                    type="button"
                    className="secondary-button"
                    onClick={() => { setEquipmentFilter(''); setStatusFilter(''); setPriorityFilter(''); setTypeFilter('') }}
                    disabled={!equipmentFilter && !statusFilter && !priorityFilter && !typeFilter}
                  >
                    Clear filters
                  </button>
                </div>

                {loading ? (
                  <div className="maintenance-state" role="status"><span className="utilization-spinner" /><p>Loading work orders…</p></div>
                ) : filteredRecords.length === 0 ? (
                  <div className="maintenance-state">
                    <h3>No maintenance records found</h3>
                    <p className="muted-text">Work orders will appear here after creation or adjustment of filters.</p>
                  </div>
                ) : (
                  <div className="maintenance-table-wrapper">
                    <table className="maintenance-table">
                      <thead>
                        <tr>
                          <th>Order #</th>
                          <th>Equipment</th>
                          <th>Type</th>
                          <th>Description</th>
                          <th>Priority</th>
                          <th>Status</th>
                          <th>Assigned Tech</th>
                          <th>Scheduled / Reported</th>
                          <th>Downtime</th>
                          <th>Resolution</th>
                          <th>Actions</th>
                        </tr>
                      </thead>
                      <tbody>
                        {filteredRecords.map((record) => {
                          const actions = getMaintenanceActionPlan(user?.roles, record)
                          const actionBusy = busy.endsWith(`-${record.id}`)
                          return (
                            <tr key={record.id}>
                              <td>#{record.id}</td>
                              <td>
                                <strong>{equipmentName(record.equipmentId)}</strong>
                                <span className="maintenance-cell-subtitle">ID: {record.equipmentId ?? '—'}</span>
                              </td>
                              <td>
                                <span className="sharing-badge sharing-badge-forwarded">
                                  {record.maintenanceType || 'CORRECTIVE'}
                                </span>
                              </td>
                              <td className="maintenance-description">{record.description || '—'}</td>
                              <td>{displayValue(record.priority)}</td>
                              <td>
                                <span className={`maintenance-status maintenance-status-${String(record.status).toLowerCase()}`}>
                                  {displayValue(record.status)}
                                </span>
                              </td>
                              <td>{technicianName(record.assignedTo)}</td>
                              <td>{formatDate(record.scheduledDate || record.reportedAt)}</td>
                              <td>{record.downtimeHours != null ? `${record.downtimeHours}h` : '—'}</td>
                              <td>{record.resolutionNotes || '—'}</td>
                              <td>
                                <div className="maintenance-actions" style={{ flexWrap: 'wrap', gap: '6px' }}>
                                  {actions.canAssign && (
                                    <button
                                      className="secondary-button"
                                      type="button"
                                      onClick={() => { setAssignmentFor(assignmentFor === record.id ? null : record.id); setTechnicianId(''); setFormError('') }}
                                    >
                                      Assign
                                    </button>
                                  )}
                                  {actions.canChangeStatus && actions.transitions.map((status) => (
                                    <button
                                      className={status === 'COMPLETED' ? 'primary-button' : 'secondary-button'}
                                      type="button"
                                      key={status}
                                      disabled={actionBusy}
                                      onClick={() => changeStatus(record, status)}
                                    >
                                      {actionBusy ? 'Saving…' : status === 'COMPLETED' ? '✓ Complete' : displayValue(status)}
                                    </button>
                                  ))}
                                  {actions.canChangeStatus && ['IN_PROGRESS', 'COMPLETED'].includes(String(record.status).toUpperCase()) && (
                                    <button
                                      className="secondary-button"
                                      type="button"
                                      onClick={() => setDowntimeModal({ recordId: record.id, downtimeHours: record.downtimeHours || '' })}
                                    >
                                      ⏱ Log Downtime
                                    </button>
                                  )}
                                  {actions.canDelete && (
                                    <button
                                      className="secondary-button"
                                      type="button"
                                      disabled={actionBusy}
                                      onClick={() => {
                                        if (window.confirm(`Delete work order #${record.id}?`)) {
                                          mutate(`delete-${record.id}`, () => api.delete(`/api/maintenance/${record.id}`), 'Work order deleted.')
                                        }
                                      }}
                                    >
                                      Delete
                                    </button>
                                  )}

                                  {assignmentFor === record.id && (
                                    <form onSubmit={(event) => submitAssignment(event, record.id)} style={{ width: '100%', marginTop: '8px' }}>
                                      {technicians.length > 0 ? (
                                        <select
                                          required
                                          value={technicianId}
                                          onChange={(event) => setTechnicianId(event.target.value)}
                                        >
                                          <option value="">Select technician</option>
                                          {technicians.map((tech) => (
                                            <option key={tech.id} value={tech.id}>{tech.fullName} ({tech.email})</option>
                                          ))}
                                        </select>
                                      ) : (
                                        <input
                                          type="number"
                                          min="1"
                                          placeholder="Enter Technician ID"
                                          required
                                          value={technicianId}
                                          onChange={(event) => setTechnicianId(event.target.value)}
                                        />
                                      )}
                                      <button type="submit" className="primary-button" style={{ marginTop: '4px' }}>Save</button>
                                    </form>
                                  )}
                                </div>
                              </td>
                            </tr>
                          )
                        })}
                      </tbody>
                    </table>
                  </div>
                )}
              </section>
            </>
          )}

          {/* TAB 2: MY ASSIGNED TASKS */}
          {activeTab === 'my-tasks' && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
              <section className="app-surface maintenance-panel">
                <div className="maintenance-panel-heading">
                  <div>
                    <h2>My Assigned Maintenance Work Orders</h2>
                    <p className="muted-text">Work orders assigned to you for repair, inspection, or preventive maintenance.</p>
                  </div>
                  <span className="maintenance-record-count">
                    {myTasks.maintenance.length} {myTasks.maintenance.length === 1 ? 'task' : 'tasks'}
                  </span>
                </div>

                {myTasks.maintenance.length === 0 ? (
                  <p className="muted-text">No active maintenance tasks are currently assigned to you.</p>
                ) : (
                  <div className="maintenance-table-wrapper">
                    <table className="maintenance-table">
                      <thead>
                        <tr>
                          <th>Task #</th>
                          <th>Equipment</th>
                          <th>Type</th>
                          <th>Priority</th>
                          <th>Status</th>
                          <th>Description</th>
                          <th>Scheduled / Reported</th>
                          <th>Downtime</th>
                          <th>Quick Action</th>
                        </tr>
                      </thead>
                      <tbody>
                        {myTasks.maintenance.map((record) => {
                          const status = String(record.status).toUpperCase()
                          return (
                            <tr key={record.id}>
                              <td>#{record.id}</td>
                              <td><strong>{equipmentName(record.equipmentId)}</strong></td>
                              <td><span className="sharing-badge sharing-badge-forwarded">{record.maintenanceType || 'CORRECTIVE'}</span></td>
                              <td>{displayValue(record.priority)}</td>
                              <td>
                                <span className={`maintenance-status maintenance-status-${status.toLowerCase()}`}>
                                  {displayValue(status)}
                                </span>
                              </td>
                              <td className="maintenance-description">{record.description}</td>
                              <td>{formatDate(record.scheduledDate || record.reportedAt)}</td>
                              <td>{record.downtimeHours != null ? `${record.downtimeHours}h` : '—'}</td>
                              <td>
                                <div style={{ display: 'flex', gap: '6px' }}>
                                  {status === 'ASSIGNED' && (
                                    <button
                                      className="primary-button"
                                      type="button"
                                      onClick={() => changeStatus(record, 'IN_PROGRESS')}
                                    >
                                      ▶ Start Work
                                    </button>
                                  )}
                                  {status === 'IN_PROGRESS' && (
                                    <button
                                      className="primary-button"
                                      type="button"
                                      onClick={() => changeStatus(record, 'COMPLETED')}
                                    >
                                      ✓ Complete Work
                                    </button>
                                  )}
                                  {status === 'COMPLETED' && <span className="muted-text">Completed</span>}
                                </div>
                              </td>
                            </tr>
                          )
                        })}
                      </tbody>
                    </table>
                  </div>
                )}
              </section>

              <section className="app-surface maintenance-panel">
                <div className="maintenance-panel-heading">
                  <div>
                    <h2>My Assigned Calibration Tasks</h2>
                    <p className="muted-text">Equipment scheduled for calibration by you.</p>
                  </div>
                  <span className="maintenance-record-count">
                    {myTasks.calibrations.length} {myTasks.calibrations.length === 1 ? 'task' : 'tasks'}
                  </span>
                </div>

                {myTasks.calibrations.length === 0 ? (
                  <p className="muted-text">No calibration tasks are currently assigned to you.</p>
                ) : (
                  <div className="maintenance-table-wrapper">
                    <table className="maintenance-table">
                      <thead>
                        <tr>
                          <th>Task #</th>
                          <th>Equipment</th>
                          <th>Status</th>
                          <th>Scheduled Date</th>
                          <th>Notes</th>
                          <th>Action</th>
                        </tr>
                      </thead>
                      <tbody>
                        {myTasks.calibrations.map((cal) => (
                          <tr key={cal.id}>
                            <td>#{cal.id}</td>
                            <td><strong>{cal.equipmentName || equipmentName(cal.equipmentId || cal.equipment?.id)}</strong></td>
                            <td><span className="sharing-badge sharing-badge-pending">{displayValue(cal.status)}</span></td>
                            <td>{cal.scheduledDate || cal.nextDueDate || '—'}</td>
                            <td>{cal.notes || '—'}</td>
                            <td>
                              <button
                                className="primary-button"
                                type="button"
                                onClick={() => openCompleteCalModal(cal)}
                              >
                                ⚖️ Record Calibration
                              </button>
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </section>
            </div>
          )}

          {/* TAB 3: CALIBRATION & COMPLIANCE */}
          {activeTab === 'calibration' && (
            <>
              {scheduleCalOpen && canSchedCal && (
                <form className="app-surface maintenance-panel" onSubmit={submitCalibrationSchedule}>
                  <h2>Schedule Equipment Calibration</h2>
                  <p className="muted-text">Schedule periodic calibration to maintain laboratory regulatory standards.</p>
                  <div className="maintenance-filters" style={{ gridTemplateColumns: 'repeat(3, minmax(0, 1fr))' }}>
                    <label>
                      Equipment *
                      <select
                        required
                        value={calScheduleForm.equipmentId}
                        onChange={(event) => setCalScheduleForm({ ...calScheduleForm, equipmentId: event.target.value })}
                      >
                        <option value="">Select equipment</option>
                        {equipment.map((item) => (
                          <option key={item.id} value={item.id}>{item.name} (ID: {item.id})</option>
                        ))}
                      </select>
                    </label>

                    <label>
                      Scheduled Due Date *
                      <input
                        type="date"
                        required
                        value={calScheduleForm.scheduledDate}
                        onChange={(event) => setCalScheduleForm({ ...calScheduleForm, scheduledDate: event.target.value })}
                      />
                    </label>

                    <label>
                      Assigned Technician
                      <select
                        value={calScheduleForm.assignedTo}
                        onChange={(event) => setCalScheduleForm({ ...calScheduleForm, assignedTo: event.target.value })}
                      >
                        <option value="">Select technician (optional)</option>
                        {technicians.map((tech) => (
                          <option key={tech.id} value={tech.id}>{tech.fullName} ({tech.email})</option>
                        ))}
                      </select>
                    </label>

                    <label style={{ gridColumn: 'span 3' }}>
                      Calibration Notes / Standards
                      <textarea
                        rows={2}
                        placeholder="e.g. ISO/IEC 17025 standard alignment, optical recalibration..."
                        value={calScheduleForm.notes}
                        onChange={(event) => setCalScheduleForm({ ...calScheduleForm, notes: event.target.value })}
                      />
                    </label>
                  </div>
                  {calScheduleError && <p className="auth-alert" role="alert">{calScheduleError}</p>}
                  <div style={{ display: 'flex', gap: '10px' }}>
                    <button className="primary-button" type="submit" disabled={busy !== ''}>
                      {busy === 'schedule-cal' ? 'Scheduling…' : 'Schedule Calibration'}
                    </button>
                    <button className="secondary-button" type="button" onClick={() => setScheduleCalOpen(false)}>
                      Cancel
                    </button>
                  </div>
                </form>
              )}

              <div className="maintenance-summary-grid" style={{ gridTemplateColumns: 'repeat(5, minmax(0, 1fr))' }}>
                {calCards.map(([label, value]) => (
                  <article className="app-surface maintenance-stat" key={label}>
                    <span className="muted-text">{label}</span>
                    <h2>{value}</h2>
                  </article>
                ))}
              </div>

              <section className="app-surface maintenance-panel">
                <div className="maintenance-panel-heading">
                  <div>
                    <h2>Equipment Calibration Records &amp; Tasks</h2>
                    <p className="muted-text">Verified calibration logs, upcoming schedules, and compliance expiration tracking.</p>
                  </div>
                  <span className="maintenance-record-count">
                    {filteredCalibrations.length} {filteredCalibrations.length === 1 ? 'record' : 'records'}
                  </span>
                </div>

                <div className="maintenance-filters" style={{ gridTemplateColumns: 'repeat(3, minmax(0, 1fr)) auto' }}>
                  <label>
                    Equipment
                    <select value={equipmentFilter} onChange={(event) => setEquipmentFilter(event.target.value)}>
                      <option value="">All equipment</option>
                      {equipment.map((item) => (
                        <option key={item.id} value={String(item.id)}>{item.name} (ID: {item.id})</option>
                      ))}
                    </select>
                  </label>
                  <label>
                    Compliance Status
                    <select value={calibrationStatusFilter} onChange={(event) => setCalibrationStatusFilter(event.target.value)}>
                      <option value="">All statuses</option>
                      <option value="PASS">Valid / Passed</option>
                      <option value="DUE_SOON">Due Soon (&lt; 30d)</option>
                      <option value="OVERDUE">Overdue</option>
                      <option value="SCHEDULED">Scheduled</option>
                    </select>
                  </label>
                  <button
                    type="button"
                    className="secondary-button"
                    onClick={() => { setEquipmentFilter(''); setCalibrationStatusFilter('') }}
                    disabled={!equipmentFilter && !calibrationStatusFilter}
                  >
                    Clear filters
                  </button>
                </div>

                {filteredCalibrations.length === 0 ? (
                  <div className="maintenance-state">
                    <h3>No calibration records found</h3>
                    <p className="muted-text">Scheduled calibrations and completion logs will appear here.</p>
                  </div>
                ) : (
                  <div className="maintenance-table-wrapper">
                    <table className="maintenance-table">
                      <thead>
                        <tr>
                          <th>Record #</th>
                          <th>Equipment</th>
                          <th>Compliance Status</th>
                          <th>Calibration Date</th>
                          <th>Next Due Date</th>
                          <th>Result</th>
                          <th>Assigned Tech</th>
                          <th>Certificate #</th>
                          <th>Notes</th>
                          <th>Action</th>
                        </tr>
                      </thead>
                      <tbody>
                        {filteredCalibrations.map((cal) => {
                          const badge = getCalibrationStatusBadge(cal)
                          const isScheduled = String(cal.status).toUpperCase() === 'SCHEDULED'
                          return (
                            <tr key={cal.id}>
                              <td>#{cal.id}</td>
                              <td><strong>{cal.equipmentName || equipmentName(cal.equipmentId || cal.equipment?.id)}</strong></td>
                              <td>
                                <span className={`sharing-badge ${badge.tone === 'success' ? 'sharing-badge-active' : badge.tone === 'danger' ? 'sharing-badge-rejected' : badge.tone === 'warning' ? 'sharing-badge-forwarded' : 'sharing-badge-pending'}`}>
                                  {badge.label}
                                </span>
                              </td>
                              <td>{cal.calibrationDate || '—'}</td>
                              <td><strong>{cal.nextDueDate || '—'}</strong></td>
                              <td>{cal.result || '—'}</td>
                              <td>{technicianName(cal.assignedTo)}</td>
                              <td>{cal.certificateNumber || '—'}</td>
                              <td className="maintenance-description">{cal.notes || '—'}</td>
                              <td>
                                {isScheduled && canCompCal && (
                                  <button
                                    className="primary-button"
                                    type="button"
                                    onClick={() => openCompleteCalModal(cal)}
                                  >
                                    Record Results
                                  </button>
                                )}
                              </td>
                            </tr>
                          )
                        })}
                      </tbody>
                    </table>
                  </div>
                )}
              </section>
            </>
          )}

          {/* TAB 4: EQUIPMENT HISTORY */}
          {activeTab === 'history' && (
            <section className="app-surface maintenance-panel">
              <h2>Equipment Maintenance &amp; Calibration History</h2>
              <p className="muted-text">Inspect historical work orders and calibration logs for a specific instrument.</p>
              <div className="maintenance-filters" style={{ gridTemplateColumns: '1fr auto' }}>
                <label>
                  Equipment
                  <select
                    value={historyEquipmentId}
                    onChange={(event) => {
                      historyRequest.current += 1
                      setHistoryEquipmentId(event.target.value)
                      setHistory(null)
                      setHistoryLoading(false)
                    }}
                  >
                    <option value="">Select equipment to inspect history</option>
                    {equipment.map((item) => (
                      <option key={item.id} value={String(item.id)}>{item.name} (ID: {item.id})</option>
                    ))}
                  </select>
                </label>
                <button
                  className="secondary-button"
                  type="button"
                  disabled={!historyEquipmentId || historyLoading}
                  onClick={showEquipmentHistory}
                >
                  {historyLoading ? 'Loading…' : 'View History'}
                </button>
              </div>

              {historyLoading ? (
                <p role="status">Loading historical logs…</p>
              ) : history && (
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '20px', marginTop: '20px' }}>
                  <div>
                    <h3>Maintenance Records ({history.maintenance.length})</h3>
                    {history.maintenance.length === 0 ? (
                      <p className="muted-text">No maintenance history recorded for this equipment.</p>
                    ) : (
                      <ul>
                        {history.maintenance.map((r) => (
                          <li key={r.id} style={{ marginBottom: '8px' }}>
                            <strong>#{r.id} [{displayValue(r.status)}]</strong> · {r.maintenanceType || 'CORRECTIVE'} · {r.description} · {formatDate(r.reportedAt)}
                            {r.downtimeHours != null && ` (${r.downtimeHours}h downtime)`}
                          </li>
                        ))}
                      </ul>
                    )}
                  </div>
                  <div>
                    <h3>Calibration Records ({history.calibrations.length})</h3>
                    {history.calibrations.length === 0 ? (
                      <p className="muted-text">No calibration history recorded for this equipment.</p>
                    ) : (
                      <ul>
                        {history.calibrations.map((c) => (
                          <li key={c.id} style={{ marginBottom: '8px' }}>
                            <strong>#{c.id} [{c.result || c.status}]</strong> · Calibrated: {c.calibrationDate || '—'} · Next Due: {c.nextDueDate || '—'}
                            {c.certificateNumber && ` · Cert: ${c.certificateNumber}`}
                          </li>
                        ))}
                      </ul>
                    )}
                  </div>
                </div>
              )}
            </section>
          )}

          {/* MODAL: COMPLETE WORK ORDER WITH DOWNTIME */}
          {completionModal && (
            <div className="modal-backdrop" style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.5)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 100 }}>
              <div className="app-surface" style={{ padding: '24px', borderRadius: '12px', maxWidth: '500px', width: '100%', margin: '16px' }}>
                <h2>Complete Work Order #{completionModal.recordId}</h2>
                <p className="muted-text">Record actual downtime hours and technical resolution notes.</p>
                <form onSubmit={submitCompletion}>
                  <label style={{ display: 'block', marginBottom: '12px' }}>
                    Downtime Hours
                    <input
                      type="number"
                      step="0.1"
                      min="0"
                      placeholder="e.g. 2.5"
                      value={completionModal.downtimeHours}
                      onChange={(e) => setCompletionModal({ ...completionModal, downtimeHours: e.target.value })}
                      style={{ width: '100%', padding: '8px', marginTop: '4px' }}
                    />
                  </label>
                  <label style={{ display: 'block', marginBottom: '16px' }}>
                    Resolution Notes *
                    <textarea
                      required
                      maxLength={1000}
                      rows={3}
                      placeholder="Details of repair, replaced components, calibration performed..."
                      value={completionModal.resolutionNotes}
                      onChange={(e) => setCompletionModal({ ...completionModal, resolutionNotes: e.target.value })}
                      style={{ width: '100%', padding: '8px', marginTop: '4px' }}
                    />
                  </label>
                  <div style={{ display: 'flex', gap: '10px', justifyContent: 'flex-end' }}>
                    <button type="button" className="secondary-button" onClick={() => setCompletionModal(null)}>Cancel</button>
                    <button type="submit" className="primary-button" disabled={busy !== ''}>
                      {busy === 'complete-work' ? 'Saving…' : 'Complete & Log'}
                    </button>
                  </div>
                </form>
              </div>
            </div>
          )}

          {/* MODAL: LOG DOWNTIME */}
          {downtimeModal && (
            <div className="modal-backdrop" style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.5)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 100 }}>
              <div className="app-surface" style={{ padding: '24px', borderRadius: '12px', maxWidth: '420px', width: '100%', margin: '16px' }}>
                <h2>Log Equipment Downtime</h2>
                <p className="muted-text">Update logged downtime hours for work order #{downtimeModal.recordId}.</p>
                <form onSubmit={submitDowntime}>
                  <label style={{ display: 'block', marginBottom: '16px' }}>
                    Downtime Hours *
                    <input
                      type="number"
                      step="0.1"
                      min="0"
                      required
                      value={downtimeModal.downtimeHours}
                      onChange={(e) => setDowntimeModal({ ...downtimeModal, downtimeHours: e.target.value })}
                      style={{ width: '100%', padding: '8px', marginTop: '4px' }}
                    />
                  </label>
                  <div style={{ display: 'flex', gap: '10px', justifyContent: 'flex-end' }}>
                    <button type="button" className="secondary-button" onClick={() => setDowntimeModal(null)}>Cancel</button>
                    <button type="submit" className="primary-button" disabled={busy !== ''}>Save Downtime</button>
                  </div>
                </form>
              </div>
            </div>
          )}

          {/* MODAL: COMPLETE CALIBRATION */}
          {completeCalModal && (
            <div className="modal-backdrop" style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.5)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 100 }}>
              <div className="app-surface" style={{ padding: '24px', borderRadius: '12px', maxWidth: '520px', width: '100%', margin: '16px' }}>
                <h2>Record Calibration Results</h2>
                <p className="muted-text">Equipment: {completeCalModal.equipmentName || equipmentName(completeCalModal.equipmentId || completeCalModal.equipment?.id)}</p>
                <form onSubmit={submitCalibrationComplete}>
                  <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px', marginBottom: '12px' }}>
                    <label>
                      Calibration Date *
                      <input
                        type="date"
                        required
                        value={calCompleteForm.calibrationDate}
                        onChange={(e) => setCalCompleteForm({ ...calCompleteForm, calibrationDate: e.target.value })}
                        style={{ width: '100%', padding: '8px', marginTop: '4px' }}
                      />
                    </label>
                    <label>
                      Result *
                      <select
                        value={calCompleteForm.result}
                        onChange={(e) => setCalCompleteForm({ ...calCompleteForm, result: e.target.value })}
                        style={{ width: '100%', padding: '8px', marginTop: '4px' }}
                      >
                        {CALIBRATION_RESULTS.map((r) => (
                          <option key={r} value={r}>{r}</option>
                        ))}
                      </select>
                    </label>
                  </div>

                  <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px', marginBottom: '12px' }}>
                    <label>
                      Next Due Date *
                      <input
                        type="date"
                        required
                        value={calCompleteForm.nextDueDate}
                        onChange={(e) => setCalCompleteForm({ ...calCompleteForm, nextDueDate: e.target.value })}
                        style={{ width: '100%', padding: '8px', marginTop: '4px' }}
                      />
                    </label>
                    <label>
                      Certificate Number
                      <input
                        type="text"
                        placeholder="e.g. CAL-2026-081"
                        value={calCompleteForm.certificateNumber}
                        onChange={(e) => setCalCompleteForm({ ...calCompleteForm, certificateNumber: e.target.value })}
                        style={{ width: '100%', padding: '8px', marginTop: '4px' }}
                      />
                    </label>
                  </div>

                  <label style={{ display: 'block', marginBottom: '16px' }}>
                    Calibration Notes
                    <textarea
                      rows={2}
                      placeholder="Calibration tolerances, standard reference instruments used..."
                      value={calCompleteForm.notes}
                      onChange={(e) => setCalCompleteForm({ ...calCompleteForm, notes: e.target.value })}
                      style={{ width: '100%', padding: '8px', marginTop: '4px' }}
                    />
                  </label>

                  {calCompleteError && <p className="auth-alert" role="alert">{calCompleteError}</p>}

                  <div style={{ display: 'flex', gap: '10px', justifyContent: 'flex-end' }}>
                    <button type="button" className="secondary-button" onClick={() => setCompleteCalModal(null)}>Cancel</button>
                    <button type="submit" className="primary-button" disabled={busy !== ''}>
                      {busy === 'complete-cal' ? 'Saving…' : 'Record Calibration'}
                    </button>
                  </div>
                </form>
              </div>
            </div>
          )}
        </>
      )}
    </section>
  )
}
