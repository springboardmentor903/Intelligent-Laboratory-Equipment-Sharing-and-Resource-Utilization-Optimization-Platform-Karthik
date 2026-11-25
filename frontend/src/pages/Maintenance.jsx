import { useEffect, useMemo, useState } from 'react'
import api from '../api/axiosInstance'
import { useAuth } from '../context/AuthContext'
import {
  WORK_ORDER_STATUSES,
  WORK_ORDER_TYPES,
  MAINTENANCE_PRIORITIES,
  getWorkOrderStatusBadge,
  getScheduleStatusBadge,
  validateMaintenanceRequest,
  buildMaintenanceRequestPayload,
  validateWorkOrderCreate,
  buildWorkOrderCreatePayload,
  validatePreventiveSchedule,
  buildPreventiveSchedulePayload,
} from '../access/maintenanceWorkflow.js'

function errorMessage(error, fallback = 'Operation failed.') {
  if (error?.response?.status === 401) return 'Your session has expired. Sign in again to continue.'
  if (error?.response?.status === 403) return 'Access denied. Your role does not allow this maintenance operation.'
  return error?.response?.data?.message || error?.response?.data?.error || error?.message || fallback
}

function formatDate(val) {
  if (!val) return '—'
  const d = new Date(val)
  return Number.isNaN(d.getTime()) ? val : d.toLocaleString()
}

function formatDateOnly(val) {
  if (!val) return '—'
  const d = new Date(val)
  return Number.isNaN(d.getTime()) ? val : d.toLocaleDateString()
}

export default function Maintenance() {
  const { user } = useAuth()
  const roles = Array.isArray(user?.roles) ? user.roles : []
  const isSystemAdmin = roles.includes('SYSTEM_ADMIN')
  const isLabManager = roles.includes('LAB_MANAGER')
  const isInstitutionAdmin = roles.includes('INSTITUTION_ADMIN')
  const isTechnician = roles.includes('LAB_TECHNICIAN')
  const isStaff = isLabManager || isInstitutionAdmin

  // Initial active tab
  const defaultTab = isStaff ? 'overview' : (isTechnician ? 'work-orders' : 'requests')
  const [activeTab, setActiveTab] = useState(defaultTab)

  // Shared Data
  const [equipmentList, setEquipmentList] = useState([])
  const [technicians, setTechnicians] = useState([])
  const [loading, setLoading] = useState(false)
  const [notice, setNotice] = useState('')
  const [error, setError] = useState('')

  // 1. Overview & Analytics State
  const [downtimeSummary, setDowntimeSummary] = useState(null)
  const [startDate, setStartDate] = useState(new Date(Date.now() - 30 * 86400000).toISOString().split('T')[0])
  const [endDate, setEndDate] = useState(new Date().toISOString().split('T')[0])
  const [analyticsLoading, setAnalyticsLoading] = useState(false)

  // 2. Work Orders State
  const [workOrders, setWorkOrders] = useState([])
  const [woStatusFilter, setWoStatusFilter] = useState('')
  const [woTypeFilter, setWoTypeFilter] = useState('')
  const [woPriorityFilter, setWoPriorityFilter] = useState('')
  const [woEquipmentFilter, setWoEquipmentFilter] = useState('')
  const [assignedToMe, setAssignedToMe] = useState(isTechnician)
  const [selectedWorkOrder, setSelectedWorkOrder] = useState(null)
  const [serviceLogs, setServiceLogs] = useState([])
  const [newLogNote, setNewLogNote] = useState('')
  const [logSubmitting, setLogSubmitting] = useState(false)

  // Work Order Modals
  const [createWoOpen, setCreateWoOpen] = useState(false)
  const [createWoForm, setCreateWoForm] = useState({
    equipmentId: '',
    title: '',
    description: '',
    type: 'CORRECTIVE',
    priority: 'MEDIUM',
    assignedTo: '',
    expectedEndTime: '',
  })
  const [createWoError, setCreateWoError] = useState('')

  const [assignModalWo, setAssignModalWo] = useState(null)
  const [assignTechId, setAssignTechId] = useState('')

  const [startModalWo, setStartModalWo] = useState(null)
  const [startExpectedEnd, setStartExpectedEnd] = useState('')
  const [startPreview, setStartPreview] = useState(null)
  const [previewLoading, setPreviewLoading] = useState(false)

  const [extendModalWo, setExtendModalWo] = useState(null)
  const [extendEndTime, setExtendEndTime] = useState('')
  const [extendReason, setExtendReason] = useState('')

  const [completeModalWo, setCompleteModalWo] = useState(null)
  const [completeNotes, setCompleteNotes] = useState('')
  const [completeCost, setCompleteCost] = useState('')

  const [cancelModalWo, setCancelModalWo] = useState(null)
  const [cancelReason, setCancelReason] = useState('')

  // 3. Maintenance Requests State
  const [requestSubTab, setRequestSubTab] = useState(isStaff ? 'queue' : 'mine')
  const [myRequests, setMyRequests] = useState([])
  const [queueRequests, setQueueRequests] = useState([])
  const [reportModalOpen, setReportModalOpen] = useState(false)
  const [reportForm, setReportForm] = useState({
    equipmentId: '',
    title: '',
    description: '',
    priority: 'MEDIUM',
  })
  const [reportError, setReportError] = useState('')

  const [approveModalReq, setApproveModalReq] = useState(null)
  const [approveRemarks, setApproveRemarks] = useState('')

  const [rejectModalReq, setRejectModalReq] = useState(null)
  const [rejectRemarks, setRejectRemarks] = useState('')

  // 4. Preventive Schedules State
  const [schedules, setSchedules] = useState([])
  const [createSchedOpen, setCreateSchedOpen] = useState(false)
  const [editingSchedule, setEditingSchedule] = useState(null)
  const [scheduleForm, setScheduleForm] = useState({
    equipmentId: '',
    title: '',
    description: '',
    repeatPeriodDays: '30',
    nextDueDate: '',
    active: true,
  })
  const [scheduleError, setScheduleError] = useState('')

  // -------------------------------------------------------------
  // Data Loaders
  // -------------------------------------------------------------
  useEffect(() => {
    if (isSystemAdmin) return
    loadEquipment()
    if (isStaff) {
      loadTechnicians()
    }
  }, [isSystemAdmin, isStaff])

  useEffect(() => {
    if (isSystemAdmin) return
    if (activeTab === 'overview' && isStaff) {
      loadDowntimeSummary()
    } else if (activeTab === 'work-orders') {
      loadWorkOrders()
    } else if (activeTab === 'requests') {
      if (requestSubTab === 'mine') loadMyRequests()
      else if (isStaff) loadQueueRequests()
    } else if (activeTab === 'schedules' && isStaff) {
      loadSchedules()
    }
  }, [activeTab, requestSubTab, isSystemAdmin, isStaff, assignedToMe, woStatusFilter, woTypeFilter, woPriorityFilter, woEquipmentFilter])

  async function loadEquipment() {
    try {
      const res = await api.get('/api/equipment')
      setEquipmentList(Array.isArray(res.data) ? res.data : [])
    } catch (err) {
      console.warn('Could not load equipment', err)
    }
  }

  async function loadTechnicians() {
    try {
      const res = await api.get('/api/users')
      if (Array.isArray(res.data)) {
        setTechnicians(res.data.filter((u) => Array.isArray(u.roles) && u.roles.includes('LAB_TECHNICIAN')))
      }
    } catch (err) {
      console.warn('Could not load technicians', err)
    }
  }

  async function loadDowntimeSummary() {
    setAnalyticsLoading(true)
    setError('')
    try {
      const params = {}
      if (startDate) params.startDate = startDate
      if (endDate) params.endDate = endDate
      const res = await api.get('/api/maintenance/downtime/summary', { params })
      setDowntimeSummary(res.data)
    } catch (err) {
      setError(errorMessage(err, 'Unable to load downtime summary.'))
    } finally {
      setAnalyticsLoading(false)
    }
  }

  async function loadWorkOrders() {
    setLoading(true)
    setError('')
    try {
      const params = { page: 0, size: 50, sort: 'createdAt,desc' }
      if (woStatusFilter) params.status = woStatusFilter
      if (woTypeFilter) params.type = woTypeFilter
      if (woPriorityFilter) params.priority = woPriorityFilter
      if (woEquipmentFilter) params.equipmentId = woEquipmentFilter
      if (assignedToMe) params.assignedToMe = true

      const res = await api.get('/api/maintenance/work-orders', { params })
      setWorkOrders(res.data?.content || res.data || [])
    } catch (err) {
      setError(errorMessage(err, 'Unable to load work orders.'))
    } finally {
      setLoading(false)
    }
  }

  async function loadWorkOrderDetail(id) {
    try {
      const [woRes, logsRes] = await Promise.all([
        api.get(`/api/maintenance/work-orders/${id}`),
        api.get(`/api/maintenance/work-orders/${id}/service-logs`),
      ])
      setSelectedWorkOrder(woRes.data)
      setServiceLogs(Array.isArray(logsRes.data) ? logsRes.data : [])
    } catch (err) {
      setError(errorMessage(err, 'Unable to load work order details.'))
    }
  }

  async function loadMyRequests() {
    setLoading(true)
    setError('')
    try {
      const res = await api.get('/api/maintenance/requests/mine')
      setMyRequests(res.data?.content || res.data || [])
    } catch (err) {
      setError(errorMessage(err, 'Unable to load your requests.'))
    } finally {
      setLoading(false)
    }
  }

  async function loadQueueRequests() {
    setLoading(true)
    setError('')
    try {
      const res = await api.get('/api/maintenance/requests/queue')
      setQueueRequests(res.data?.content || res.data || [])
    } catch (err) {
      setError(errorMessage(err, 'Unable to load request queue.'))
    } finally {
      setLoading(false)
    }
  }

  async function loadSchedules() {
    setLoading(true)
    setError('')
    try {
      const res = await api.get('/api/maintenance/schedules')
      setSchedules(res.data?.content || res.data || [])
    } catch (err) {
      setError(errorMessage(err, 'Unable to load preventive schedules.'))
    } finally {
      setLoading(false)
    }
  }

  // -------------------------------------------------------------
  // Work Order Handlers
  // -------------------------------------------------------------
  async function handleCreateWorkOrder(e) {
    e.preventDefault()
    setCreateWoError('')
    const valErr = validateWorkOrderCreate(createWoForm)
    if (valErr) {
      setCreateWoError(valErr)
      return
    }

    try {
      const payload = buildWorkOrderCreatePayload(createWoForm)
      await api.post('/api/maintenance/work-orders', payload)
      setCreateWoOpen(false)
      setNotice('Work order created successfully.')
      setCreateWoForm({
        equipmentId: '',
        title: '',
        description: '',
        type: 'CORRECTIVE',
        priority: 'MEDIUM',
        assignedTo: '',
        expectedEndTime: '',
      })
      loadWorkOrders()
    } catch (err) {
      setCreateWoError(errorMessage(err, 'Failed to create work order.'))
    }
  }

  async function handleAssignTechnician(e) {
    e.preventDefault()
    if (!assignModalWo || !assignTechId) return
    try {
      await api.post(`/api/maintenance/work-orders/${assignModalWo.id}/assign`, { technicianId: assignTechId })
      setNotice(`Technician assigned to work order ${assignModalWo.workOrderNumber}.`)
      setAssignModalWo(null)
      loadWorkOrders()
      if (selectedWorkOrder?.id === assignModalWo.id) {
        loadWorkOrderDetail(assignModalWo.id)
      }
    } catch (err) {
      setError(errorMessage(err, 'Failed to assign technician.'))
    }
  }

  async function fetchStartPreview(woId, expectedEnd) {
    if (!expectedEnd) return
    setPreviewLoading(true)
    try {
      const res = await api.get(`/api/maintenance/work-orders/${woId}/start-preview`, {
        params: { expectedEndTime: expectedEnd },
      })
      setStartPreview(res.data)
    } catch (err) {
      console.warn('Start preview error', err)
    } finally {
      setPreviewLoading(false)
    }
  }

  async function handleStartWorkOrder(e) {
    e.preventDefault()
    if (!startModalWo || !startExpectedEnd) return
    try {
      await api.post(`/api/maintenance/work-orders/${startModalWo.id}/start`, {
        expectedEndTime: startExpectedEnd,
      })
      setNotice(`Work order ${startModalWo.workOrderNumber} started. Equipment placed under maintenance.`)
      setStartModalWo(null)
      setStartPreview(null)
      loadWorkOrders()
      if (selectedWorkOrder?.id === startModalWo.id) {
        loadWorkOrderDetail(startModalWo.id)
      }
    } catch (err) {
      setError(errorMessage(err, 'Failed to start work order.'))
    }
  }

  async function handleExtendWorkOrder(e) {
    e.preventDefault()
    if (!extendModalWo || !extendEndTime) return
    try {
      await api.post(`/api/maintenance/work-orders/${extendModalWo.id}/extend`, {
        newExpectedEndTime: extendEndTime,
        reason: extendReason,
      })
      setNotice(`Work order ${extendModalWo.workOrderNumber} extended to ${formatDate(extendEndTime)}.`)
      setExtendModalWo(null)
      loadWorkOrders()
      if (selectedWorkOrder?.id === extendModalWo.id) {
        loadWorkOrderDetail(extendModalWo.id)
      }
    } catch (err) {
      setError(errorMessage(err, 'Failed to extend work order.'))
    }
  }

  async function handleCompleteWorkOrder(e) {
    e.preventDefault()
    if (!completeModalWo || !completeNotes.trim()) return
    try {
      await api.post(`/api/maintenance/work-orders/${completeModalWo.id}/complete`, {
        resolutionNotes: completeNotes.trim(),
        cost: completeCost ? Number(completeCost) : null,
      })
      setNotice(`Work order ${completeModalWo.workOrderNumber} completed. Equipment restored to Available.`)
      setCompleteModalWo(null)
      loadWorkOrders()
      if (selectedWorkOrder?.id === completeModalWo.id) {
        loadWorkOrderDetail(completeModalWo.id)
      }
    } catch (err) {
      setError(errorMessage(err, 'Failed to complete work order.'))
    }
  }

  async function handleCancelWorkOrder(e) {
    e.preventDefault()
    if (!cancelModalWo || !cancelReason.trim()) return
    try {
      await api.post(`/api/maintenance/work-orders/${cancelModalWo.id}/cancel`, {
        reason: cancelReason.trim(),
      })
      setNotice(`Work order ${cancelModalWo.workOrderNumber} cancelled.`)
      setCancelModalWo(null)
      loadWorkOrders()
      if (selectedWorkOrder?.id === cancelModalWo.id) {
        loadWorkOrderDetail(cancelModalWo.id)
      }
    } catch (err) {
      setError(errorMessage(err, 'Failed to cancel work order.'))
    }
  }

  async function handleAddServiceLogNote(e) {
    e.preventDefault()
    if (!selectedWorkOrder || !newLogNote.trim() || logSubmitting) return
    setLogSubmitting(true)
    try {
      await api.post(`/api/maintenance/work-orders/${selectedWorkOrder.id}/service-logs`, {
        notes: newLogNote.trim(),
      })
      setNewLogNote('')
      const logsRes = await api.get(`/api/maintenance/work-orders/${selectedWorkOrder.id}/service-logs`)
      setServiceLogs(Array.isArray(logsRes.data) ? logsRes.data : [])
    } catch (err) {
      setError(errorMessage(err, 'Failed to record service log note.'))
    } finally {
      setLogSubmitting(false)
    }
  }

  // -------------------------------------------------------------
  // Maintenance Request Handlers
  // -------------------------------------------------------------
  async function handleSubmitRequest(e) {
    e.preventDefault()
    setReportError('')
    const valErr = validateMaintenanceRequest(reportForm)
    if (valErr) {
      setReportError(valErr)
      return
    }

    try {
      const payload = buildMaintenanceRequestPayload(reportForm)
      await api.post('/api/maintenance/requests', payload)
      setReportModalOpen(false)
      setNotice('Maintenance problem reported successfully.')
      setReportForm({ equipmentId: '', title: '', description: '', priority: 'MEDIUM' })
      if (requestSubTab === 'mine') loadMyRequests()
      else loadQueueRequests()
    } catch (err) {
      setReportError(errorMessage(err, 'Failed to submit request.'))
    }
  }

  async function handleApproveRequest(e) {
    e.preventDefault()
    if (!approveModalReq) return
    try {
      const res = await api.post(`/api/maintenance/requests/${approveModalReq.id}/approve`, {
        remarks: approveRemarks.trim() || 'Approved for corrective maintenance',
      })
      setNotice(`Request approved. Corrective Work Order ${res.data?.workOrderNumber || ''} created automatically.`)
      setApproveModalReq(null)
      setApproveRemarks('')
      loadQueueRequests()
    } catch (err) {
      setError(errorMessage(err, 'Failed to approve request.'))
    }
  }

  async function handleRejectRequest(e) {
    e.preventDefault()
    if (!rejectModalReq || !rejectRemarks.trim()) return
    try {
      await api.post(`/api/maintenance/requests/${rejectModalReq.id}/reject`, {
        remarks: rejectRemarks.trim(),
      })
      setNotice(`Request #${rejectModalReq.id} rejected.`)
      setRejectModalReq(null)
      setRejectRemarks('')
      loadQueueRequests()
    } catch (err) {
      setError(errorMessage(err, 'Failed to reject request.'))
    }
  }

  // -------------------------------------------------------------
  // Preventive Schedule Handlers
  // -------------------------------------------------------------
  async function handleSaveSchedule(e) {
    e.preventDefault()
    setScheduleError('')
    const valErr = validatePreventiveSchedule(scheduleForm)
    if (valErr) {
      setScheduleError(valErr)
      return
    }

    try {
      const payload = buildPreventiveSchedulePayload(scheduleForm)
      if (editingSchedule) {
        await api.put(`/api/maintenance/schedules/${editingSchedule.id}`, payload)
        setNotice('Preventive schedule updated successfully.')
      } else {
        await api.post('/api/maintenance/schedules', payload)
        setNotice('New preventive maintenance schedule created.')
      }
      setCreateSchedOpen(false)
      setEditingSchedule(null)
      loadSchedules()
    } catch (err) {
      setScheduleError(errorMessage(err, 'Failed to save schedule.'))
    }
  }

  async function handleToggleScheduleActive(sched) {
    try {
      await api.patch(`/api/maintenance/schedules/${sched.id}/activate`, {
        active: !sched.active,
      })
      setNotice(`Schedule ${sched.title} is now ${!sched.active ? 'Active' : 'Inactive'}.`)
      loadSchedules()
    } catch (err) {
      setError(errorMessage(err, 'Failed to update schedule status.'))
    }
  }

  async function handleDeleteSchedule(sched) {
    if (!window.confirm(`Delete preventive schedule "${sched.title}"?`)) return
    try {
      await api.delete(`/api/maintenance/schedules/${sched.id}`)
      setNotice(`Schedule "${sched.title}" deleted.`)
      loadSchedules()
    } catch (err) {
      setError(errorMessage(err, 'Failed to delete schedule.'))
    }
  }

  // -------------------------------------------------------------
  // SYSTEM_ADMIN Access View
  // -------------------------------------------------------------
  if (isSystemAdmin) {
    return (
      <div className="module-page">
        <section className="module-page-heading">
          <div>
            <span className="eyebrow">ACCESS RESTRICTED</span>
            <h1>Maintenance Management</h1>
            <p>Maintenance and work orders are scoped to Institution Staff (Lab Managers, Technicians, Admins, and Researchers). System Administrators do not have access to institutional maintenance workflows.</p>
          </div>
        </section>
      </div>
    )
  }

  return (
    <div className="module-page">
      <section className="module-page-heading">
        <div>
          <span className="eyebrow">EQUIPMENT & ASSET CARE</span>
          <h1>Maintenance Management</h1>
          <p>Work orders, preventive schedules, problem requests, and equipment downtime tracking.</p>
        </div>
        <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap' }}>
          <button
            type="button"
            className="secondary-button"
            onClick={() => {
              setReportForm({ equipmentId: '', title: '', description: '', priority: 'MEDIUM' })
              setReportError('')
              setReportModalOpen(true)
            }}
          >
            ⚠️ Report a Problem
          </button>
          {isLabManager && (
            <button
              type="button"
              className="button button-primary"
              onClick={() => {
                setCreateWoForm({
                  equipmentId: '',
                  title: '',
                  description: '',
                  type: 'CORRECTIVE',
                  priority: 'MEDIUM',
                  assignedTo: '',
                  expectedEndTime: '',
                })
                setCreateWoError('')
                setCreateWoOpen(true)
              }}
            >
              + Create Work Order
            </button>
          )}
        </div>
      </section>

      {notice && <div className="auth-success" role="status">{notice}</div>}
      {error && <div className="module-alert" role="alert"><span>{error}</span><button type="button" onClick={() => setError('')}>Dismiss</button></div>}

      {/* Tabs Navigation */}
      <div className="analytics-tabs-bar" style={{ display: 'flex', gap: '8px', borderBottom: '2px solid var(--border)', marginBottom: '20px' }}>
        {isStaff && (
          <button
            type="button"
            className={`analytics-tab ${activeTab === 'overview' ? 'active' : ''}`}
            onClick={() => setActiveTab('overview')}
            style={{ padding: '8px 16px', fontWeight: 600, background: 'none', border: 'none', borderBottom: activeTab === 'overview' ? '3px solid var(--primary)' : '3px solid transparent', cursor: 'pointer', color: activeTab === 'overview' ? 'var(--primary)' : 'var(--text-muted)' }}
          >
            📊 Downtime Overview
          </button>
        )}
        <button
          type="button"
          className={`analytics-tab ${activeTab === 'work-orders' ? 'active' : ''}`}
          onClick={() => setActiveTab('work-orders')}
          style={{ padding: '8px 16px', fontWeight: 600, background: 'none', border: 'none', borderBottom: activeTab === 'work-orders' ? '3px solid var(--primary)' : '3px solid transparent', cursor: 'pointer', color: activeTab === 'work-orders' ? 'var(--primary)' : 'var(--text-muted)' }}
        >
          🔧 Work Orders
        </button>
        <button
          type="button"
          className={`analytics-tab ${activeTab === 'requests' ? 'active' : ''}`}
          onClick={() => setActiveTab('requests')}
          style={{ padding: '8px 16px', fontWeight: 600, background: 'none', border: 'none', borderBottom: activeTab === 'requests' ? '3px solid var(--primary)' : '3px solid transparent', cursor: 'pointer', color: activeTab === 'requests' ? 'var(--primary)' : 'var(--text-muted)' }}
        >
          📝 Maintenance Requests
        </button>
        {isStaff && (
          <button
            type="button"
            className={`analytics-tab ${activeTab === 'schedules' ? 'active' : ''}`}
            onClick={() => setActiveTab('schedules')}
            style={{ padding: '8px 16px', fontWeight: 600, background: 'none', border: 'none', borderBottom: activeTab === 'schedules' ? '3px solid var(--primary)' : '3px solid transparent', cursor: 'pointer', color: activeTab === 'schedules' ? 'var(--primary)' : 'var(--text-muted)' }}
          >
            ⏱️ Preventive Schedules
          </button>
        )}
      </div>

      {/* ============================================================= */}
      {/* 1. OVERVIEW TAB */}
      {/* ============================================================= */}
      {activeTab === 'overview' && isStaff && (
        <div>
          {/* Date range filter card */}
          <div className="booking-form-card" style={{ marginBottom: '20px' }}>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: '12px' }}>
              <div>
                <h3 style={{ margin: '0 0 4px', fontSize: '1.05rem', fontWeight: 700 }}>Analytics Date Range</h3>
                <p style={{ margin: 0, fontSize: '13px', color: 'var(--text-muted)' }}>Calculate equipment operating hours downtime against facility schedules.</p>
              </div>
              <div style={{ display: 'flex', alignItems: 'center', gap: '10px', flexWrap: 'wrap' }}>
                <input
                  type="date"
                  value={startDate}
                  onChange={(e) => setStartDate(e.target.value)}
                  style={{ padding: '6px 10px', borderRadius: '6px', border: '1px solid var(--border)', background: 'var(--surface)', color: 'var(--text)' }}
                />
                <span>to</span>
                <input
                  type="date"
                  value={endDate}
                  onChange={(e) => setEndDate(e.target.value)}
                  style={{ padding: '6px 10px', borderRadius: '6px', border: '1px solid var(--border)', background: 'var(--surface)', color: 'var(--text)' }}
                />
                <button
                  type="button"
                  className="button button-primary"
                  onClick={loadDowntimeSummary}
                  disabled={analyticsLoading}
                >
                  {analyticsLoading ? 'Calculating…' : 'Apply Range'}
                </button>
              </div>
            </div>
          </div>

          {/* Metric cards */}
          {downtimeSummary && (
            <div className="analytics-metrics-grid" style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '16px', marginBottom: '24px' }}>
              <div className="analytics-metric-card" style={{ background: 'var(--surface)', border: '1px solid var(--border)', borderRadius: '12px', padding: '16px', boxShadow: 'var(--shadow)' }}>
                <span style={{ fontSize: '12px', fontWeight: 700, color: 'var(--text-muted)', textTransform: 'uppercase' }}>Total Downtime</span>
                <div style={{ fontSize: '1.8rem', fontWeight: 800, marginTop: '6px', color: 'var(--text)' }}>
                  {downtimeSummary.totalRawDowntimeHours} <small style={{ fontSize: '13px', fontWeight: 600 }}>hrs</small>
                </div>
                <small style={{ color: 'var(--text-muted)' }}>Raw calendar hours</small>
              </div>
              <div className="analytics-metric-card" style={{ background: 'var(--surface)', border: '1px solid var(--border)', borderRadius: '12px', padding: '16px', boxShadow: 'var(--shadow)' }}>
                <span style={{ fontSize: '12px', fontWeight: 700, color: 'var(--color-danger, #ef4444)', textTransform: 'uppercase' }}>Operating Hours Lost</span>
                <div style={{ fontSize: '1.8rem', fontWeight: 800, marginTop: '6px', color: 'var(--color-danger, #ef4444)' }}>
                  {downtimeSummary.totalOperatingDowntimeHours} <small style={{ fontSize: '13px', fontWeight: 600 }}>hrs</small>
                </div>
                <small style={{ color: 'var(--text-muted)' }}>Downtime inside open hours</small>
              </div>
              <div className="analytics-metric-card" style={{ background: 'var(--surface)', border: '1px solid var(--border)', borderRadius: '12px', padding: '16px', boxShadow: 'var(--shadow)' }}>
                <span style={{ fontSize: '12px', fontWeight: 700, color: 'var(--text-muted)', textTransform: 'uppercase' }}>Completed Work Orders</span>
                <div style={{ fontSize: '1.8rem', fontWeight: 800, marginTop: '6px', color: 'var(--text)' }}>
                  {downtimeSummary.totalWorkOrders}
                </div>
                <small style={{ color: 'var(--text-muted)' }}>Incidents resolved in period</small>
              </div>
              <div className="analytics-metric-card" style={{ background: 'var(--surface)', border: '1px solid var(--border)', borderRadius: '12px', padding: '16px', boxShadow: 'var(--shadow)' }}>
                <span style={{ fontSize: '12px', fontWeight: 700, color: 'var(--text-muted)', textTransform: 'uppercase' }}>Avg Incident Duration</span>
                <div style={{ fontSize: '1.8rem', fontWeight: 800, marginTop: '6px', color: 'var(--text)' }}>
                  {downtimeSummary.averageDowntimeHours} <small style={{ fontSize: '13px', fontWeight: 600 }}>hrs</small>
                </div>
                <small style={{ color: 'var(--text-muted)' }}>Mean downtime per incident</small>
              </div>
              <div className="analytics-metric-card" style={{ background: 'var(--surface)', border: '1px solid var(--border)', borderRadius: '12px', padding: '16px', boxShadow: 'var(--shadow)' }}>
                <span style={{ fontSize: '12px', fontWeight: 700, color: 'var(--text-muted)', textTransform: 'uppercase' }}>Facility Impact</span>
                <div style={{ fontSize: '1.8rem', fontWeight: 800, marginTop: '6px', color: 'var(--text)' }}>
                  {downtimeSummary.openHoursLostPercentage}%
                </div>
                <small style={{ color: 'var(--text-muted)' }}>Of available open hours lost</small>
              </div>
            </div>
          )}

          {/* Equipment breakdown table */}
          {downtimeSummary?.equipmentBreakdown && (
            <div className="booking-form-card">
              <h3 style={{ margin: '0 0 12px', fontSize: '1.05rem', fontWeight: 700 }}>Equipment Downtime Breakdown</h3>
              <div style={{ overflowX: 'auto' }}>
                <table className="module-table" style={{ width: '100%', borderCollapse: 'collapse' }}>
                  <thead>
                    <tr style={{ textAlign: 'left', borderBottom: '2px solid var(--border)', fontSize: '13px' }}>
                      <th style={{ padding: '10px' }}>Equipment</th>
                      <th style={{ padding: '10px' }}>Asset Tag</th>
                      <th style={{ padding: '10px' }}>Incidents</th>
                      <th style={{ padding: '10px' }}>Raw Downtime</th>
                      <th style={{ padding: '10px' }}>Operating Downtime</th>
                      <th style={{ padding: '10px' }}>% Hours Lost</th>
                    </tr>
                  </thead>
                  <tbody>
                    {downtimeSummary.equipmentBreakdown.length === 0 ? (
                      <tr><td colSpan="6" style={{ padding: '16px', textAlign: 'center', color: 'var(--text-muted)' }}>No equipment downtime recorded in this date range.</td></tr>
                    ) : (
                      downtimeSummary.equipmentBreakdown.map((eq) => (
                        <tr key={eq.equipmentId} style={{ borderBottom: '1px solid var(--border)', fontSize: '13.5px' }}>
                          <td style={{ padding: '10px', fontWeight: 600 }}>{eq.equipmentName}</td>
                          <td style={{ padding: '10px', color: 'var(--text-muted)' }}>{eq.assetTag || '—'}</td>
                          <td style={{ padding: '10px' }}>{eq.workOrderCount}</td>
                          <td style={{ padding: '10px' }}>{eq.rawDowntimeHours} hrs</td>
                          <td style={{ padding: '10px', color: 'var(--color-danger, #ef4444)', fontWeight: 600 }}>{eq.operatingDowntimeHours} hrs</td>
                          <td style={{ padding: '10px' }}>
                            <span style={{
                              padding: '2px 8px',
                              borderRadius: '4px',
                              fontSize: '12px',
                              fontWeight: 700,
                              background: eq.percentageLost > 15 ? 'rgba(239, 68, 68, 0.15)' : 'rgba(16, 185, 129, 0.15)',
                              color: eq.percentageLost > 15 ? '#ef4444' : '#10b981',
                            }}>
                              {eq.percentageLost}%
                            </span>
                          </td>
                        </tr>
                      ))
                    )}
                  </tbody>
                </table>
              </div>
            </div>
          )}
        </div>
      )}

      {/* ============================================================= */}
      {/* 2. WORK ORDERS TAB */}
      {/* ============================================================= */}
      {activeTab === 'work-orders' && (
        <div>
          {/* Filters card */}
          <div className="booking-form-card" style={{ marginBottom: '20px' }}>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: '12px', alignItems: 'center' }}>
              <div>
                <label style={{ fontSize: '12px', fontWeight: 600, display: 'block', marginBottom: '4px' }}>Status</label>
                <select
                  value={woStatusFilter}
                  onChange={(e) => setWoStatusFilter(e.target.value)}
                  style={{ width: '100%', padding: '6px 10px', borderRadius: '6px', border: '1px solid var(--border)', background: 'var(--surface)', color: 'var(--text)' }}
                >
                  <option value="">All Statuses</option>
                  {WORK_ORDER_STATUSES.map((s) => <option key={s} value={s}>{s}</option>)}
                </select>
              </div>
              <div>
                <label style={{ fontSize: '12px', fontWeight: 600, display: 'block', marginBottom: '4px' }}>Type</label>
                <select
                  value={woTypeFilter}
                  onChange={(e) => setWoTypeFilter(e.target.value)}
                  style={{ width: '100%', padding: '6px 10px', borderRadius: '6px', border: '1px solid var(--border)', background: 'var(--surface)', color: 'var(--text)' }}
                >
                  <option value="">All Types</option>
                  {WORK_ORDER_TYPES.map((t) => <option key={t} value={t}>{t}</option>)}
                </select>
              </div>
              <div>
                <label style={{ fontSize: '12px', fontWeight: 600, display: 'block', marginBottom: '4px' }}>Priority</label>
                <select
                  value={woPriorityFilter}
                  onChange={(e) => setWoPriorityFilter(e.target.value)}
                  style={{ width: '100%', padding: '6px 10px', borderRadius: '6px', border: '1px solid var(--border)', background: 'var(--surface)', color: 'var(--text)' }}
                >
                  <option value="">All Priorities</option>
                  {MAINTENANCE_PRIORITIES.map((p) => <option key={p} value={p}>{p}</option>)}
                </select>
              </div>
              <div>
                <label style={{ fontSize: '12px', fontWeight: 600, display: 'block', marginBottom: '4px' }}>Equipment</label>
                <select
                  value={woEquipmentFilter}
                  onChange={(e) => setWoEquipmentFilter(e.target.value)}
                  style={{ width: '100%', padding: '6px 10px', borderRadius: '6px', border: '1px solid var(--border)', background: 'var(--surface)', color: 'var(--text)' }}
                >
                  <option value="">All Equipment</option>
                  {equipmentList.map((eq) => <option key={eq.id} value={eq.id}>{eq.name}</option>)}
                </select>
              </div>
              {isTechnician && (
                <div style={{ paddingTop: '18px' }}>
                  <label style={{ display: 'flex', alignItems: 'center', gap: '8px', cursor: 'pointer', fontWeight: 600, fontSize: '13.5px' }}>
                    <input
                      type="checkbox"
                      checked={assignedToMe}
                      onChange={(e) => setAssignedToMe(e.target.checked)}
                    />
                    <span>Assigned to Me</span>
                  </label>
                </div>
              )}
            </div>
          </div>

          {/* Work Orders Table */}
          <div className="booking-form-card">
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '12px' }}>
              <h3 style={{ margin: 0, fontSize: '1.05rem', fontWeight: 700 }}>
                Work Orders ({workOrders.length})
              </h3>
            </div>
            {loading ? (
              <p>Loading work orders…</p>
            ) : workOrders.length === 0 ? (
              <p style={{ color: 'var(--text-muted)', textAlign: 'center', padding: '24px' }}>No work orders match the selected filters.</p>
            ) : (
              <div style={{ overflowX: 'auto' }}>
                <table className="module-table" style={{ width: '100%', borderCollapse: 'collapse' }}>
                  <thead>
                    <tr style={{ textAlign: 'left', borderBottom: '2px solid var(--border)', fontSize: '13px' }}>
                      <th style={{ padding: '10px' }}>Order #</th>
                      <th style={{ padding: '10px' }}>Title</th>
                      <th style={{ padding: '10px' }}>Equipment</th>
                      <th style={{ padding: '10px' }}>Type</th>
                      <th style={{ padding: '10px' }}>Priority</th>
                      <th style={{ padding: '10px' }}>Status</th>
                      <th style={{ padding: '10px' }}>Assigned To</th>
                      <th style={{ padding: '10px' }}>Scheduled / End</th>
                      <th style={{ padding: '10px' }}>Actions</th>
                    </tr>
                  </thead>
                  <tbody>
                    {workOrders.map((wo) => {
                      const statusBadge = getWorkOrderStatusBadge(wo.status)
                      return (
                        <tr key={wo.id} style={{ borderBottom: '1px solid var(--border)', fontSize: '13.5px' }}>
                          <td style={{ padding: '10px', fontWeight: 700, fontFamily: 'monospace' }}>
                            <button
                              type="button"
                              style={{ background: 'none', border: 'none', color: 'var(--primary)', cursor: 'pointer', textDecoration: 'underline', font: 'inherit', padding: 0 }}
                              onClick={() => loadWorkOrderDetail(wo.id)}
                            >
                              {wo.workOrderNumber}
                            </button>
                          </td>
                          <td style={{ padding: '10px', fontWeight: 600 }}>{wo.title}</td>
                          <td style={{ padding: '10px' }}>{wo.equipmentName || `#${wo.equipmentId}`}</td>
                          <td style={{ padding: '10px' }}>
                            <span style={{ fontSize: '11.5px', padding: '2px 6px', borderRadius: '4px', background: wo.workOrderType === 'PREVENTIVE' ? '#dbeafe' : '#fef3c7', color: wo.workOrderType === 'PREVENTIVE' ? '#1e40af' : '#92400e', fontWeight: 600 }}>
                              {wo.workOrderType}
                            </span>
                          </td>
                          <td style={{ padding: '10px' }}>
                            <span style={{ fontSize: '11.5px', padding: '2px 6px', borderRadius: '4px', background: wo.priority === 'CRITICAL' || wo.priority === 'HIGH' ? '#fee2e2' : '#f3f4f6', color: wo.priority === 'CRITICAL' || wo.priority === 'HIGH' ? '#b91c1c' : '#374151', fontWeight: 600 }}>
                              {wo.priority}
                            </span>
                          </td>
                          <td style={{ padding: '10px' }}>
                            <span style={{
                              fontSize: '11.5px',
                              padding: '2px 6px',
                              borderRadius: '4px',
                              fontWeight: 700,
                              background: wo.status === 'COMPLETED' ? '#d1fae5' : (wo.status === 'IN_PROGRESS' ? '#fed7aa' : (wo.status === 'CANCELLED' ? '#fee2e2' : '#f1f5f9')),
                              color: wo.status === 'COMPLETED' ? '#065f46' : (wo.status === 'IN_PROGRESS' ? '#9a3412' : (wo.status === 'CANCELLED' ? '#991b1b' : '#334155')),
                            }}>
                              {statusBadge.label}
                            </span>
                          </td>
                          <td style={{ padding: '10px' }}>{wo.assignedTechnicianName || '—'}</td>
                          <td style={{ padding: '10px', fontSize: '12px' }}>
                            {wo.status === 'IN_PROGRESS' ? (
                              <span>Due: {formatDate(wo.expectedEndTime)}</span>
                            ) : (
                              <span>{formatDate(wo.scheduledDate)}</span>
                            )}
                          </td>
                          <td style={{ padding: '10px' }}>
                            <button
                              type="button"
                              className="secondary-button"
                              style={{ fontSize: '12px', padding: '3px 8px' }}
                              onClick={() => loadWorkOrderDetail(wo.id)}
                            >
                              View
                            </button>
                          </td>
                        </tr>
                      )
                    })}
                  </tbody>
                </table>
              </div>
            )}
          </div>

          {/* Work Order Details Drawer / Modal */}
          {selectedWorkOrder && (
            <div className="modal-overlay" style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.5)', zIndex: 1000, display: 'flex', justifyContent: 'flex-end' }}>
              <div style={{ background: 'var(--surface)', width: '100%', maxWidth: '640px', height: '100%', overflowY: 'auto', padding: '24px', boxShadow: 'var(--shadow)', borderLeft: '1px solid var(--border)' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '16px' }}>
                  <div>
                    <span style={{ fontSize: '12px', fontFamily: 'monospace', fontWeight: 700, color: 'var(--primary)' }}>{selectedWorkOrder.workOrderNumber}</span>
                    <h2 style={{ margin: '4px 0 0', fontSize: '1.3rem', fontWeight: 800 }}>{selectedWorkOrder.title}</h2>
                  </div>
                  <button type="button" className="secondary-button" onClick={() => setSelectedWorkOrder(null)}>✕ Close</button>
                </div>

                <div style={{ display: 'flex', gap: '8px', marginBottom: '16px', flexWrap: 'wrap' }}>
                  <span style={{ fontSize: '12px', padding: '2px 8px', borderRadius: '4px', fontWeight: 700, background: '#f1f5f9', color: '#1e293b' }}>
                    Status: {selectedWorkOrder.status}
                  </span>
                  <span style={{ fontSize: '12px', padding: '2px 8px', borderRadius: '4px', fontWeight: 700, background: '#f1f5f9', color: '#1e293b' }}>
                    Type: {selectedWorkOrder.workOrderType}
                  </span>
                  <span style={{ fontSize: '12px', padding: '2px 8px', borderRadius: '4px', fontWeight: 700, background: '#f1f5f9', color: '#1e293b' }}>
                    Priority: {selectedWorkOrder.priority}
                  </span>
                </div>

                {/* Metadata grid */}
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px', background: 'var(--surface-muted, #f8fafc)', padding: '14px', borderRadius: '8px', fontSize: '13px', marginBottom: '16px' }}>
                  <div><span>Equipment:</span> <strong>{selectedWorkOrder.equipmentName}</strong></div>
                  <div><span>Asset Tag:</span> <strong>{selectedWorkOrder.assetTag || '—'}</strong></div>
                  <div><span>Assigned Tech:</span> <strong>{selectedWorkOrder.assignedTechnicianName || 'Unassigned'}</strong></div>
                  <div><span>Scheduled:</span> <strong>{formatDate(selectedWorkOrder.scheduledDate)}</strong></div>
                  <div><span>Started:</span> <strong>{formatDate(selectedWorkOrder.startedAt)}</strong></div>
                  <div><span>Expected End:</span> <strong>{formatDate(selectedWorkOrder.expectedEndTime)}</strong></div>
                  <div><span>Completed:</span> <strong>{formatDate(selectedWorkOrder.completedAt)}</strong></div>
                  <div><span>Cost:</span> <strong>{selectedWorkOrder.cost ? `₹${selectedWorkOrder.cost}` : '—'}</strong></div>
                </div>

                {selectedWorkOrder.description && (
                  <div style={{ marginBottom: '16px' }}>
                    <h4 style={{ margin: '0 0 4px', fontSize: '13px', color: 'var(--text-muted)' }}>Description</h4>
                    <p style={{ margin: 0, fontSize: '13.5px' }}>{selectedWorkOrder.description}</p>
                  </div>
                )}

                {selectedWorkOrder.resolutionNotes && (
                  <div style={{ marginBottom: '16px', background: '#ecfdf5', padding: '12px', borderRadius: '6px', border: '1px solid #a7f3d0' }}>
                    <h4 style={{ margin: '0 0 4px', fontSize: '13px', color: '#065f46' }}>Resolution Notes</h4>
                    <p style={{ margin: 0, fontSize: '13.5px', color: '#064e3b' }}>{selectedWorkOrder.resolutionNotes}</p>
                  </div>
                )}

                {selectedWorkOrder.cancellationReason && (
                  <div style={{ marginBottom: '16px', background: '#fef2f2', padding: '12px', borderRadius: '6px', border: '1px solid #fecaca' }}>
                    <h4 style={{ margin: '0 0 4px', fontSize: '13px', color: '#991b1b' }}>Cancellation Reason</h4>
                    <p style={{ margin: 0, fontSize: '13.5px', color: '#7f1d1d' }}>{selectedWorkOrder.cancellationReason}</p>
                  </div>
                )}

                {/* Workflow Action Buttons */}
                <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap', borderTop: '1px solid var(--border)', paddingTop: '16px', marginBottom: '20px' }}>
                  {isLabManager && ['OPEN', 'ASSIGNED'].includes(selectedWorkOrder.status) && (
                    <button
                      type="button"
                      className="secondary-button"
                      onClick={() => {
                        setAssignModalWo(selectedWorkOrder)
                        setAssignTechId(selectedWorkOrder.assignedTo ? String(selectedWorkOrder.assignedTo) : '')
                      }}
                    >
                      👤 Assign Technician
                    </button>
                  )}

                  {(isLabManager || (isTechnician && selectedWorkOrder.assignedTo === user?.id)) &&
                    ['OPEN', 'ASSIGNED'].includes(selectedWorkOrder.status) && (
                      <button
                        type="button"
                        className="button button-primary"
                        onClick={() => {
                          setStartModalWo(selectedWorkOrder)
                          const defaultEnd = new Date(Date.now() + 4 * 3600000).toISOString().slice(0, 16)
                          setStartExpectedEnd(defaultEnd)
                          fetchStartPreview(selectedWorkOrder.id, defaultEnd)
                        }}
                      >
                        ▶️ Start Work Order (Preview Overlaps)
                      </button>
                  )}

                  {(isLabManager || (isTechnician && selectedWorkOrder.assignedTo === user?.id)) &&
                    selectedWorkOrder.status === 'IN_PROGRESS' && (
                      <>
                        <button
                          type="button"
                          className="secondary-button"
                          onClick={() => {
                            setExtendModalWo(selectedWorkOrder)
                            setExtendEndTime('')
                            setExtendReason('')
                          }}
                        >
                          ⏱️ Extend Downtime
                        </button>
                        <button
                          type="button"
                          className="button button-primary"
                          style={{ background: '#10b981', borderColor: '#059669' }}
                          onClick={() => {
                            setCompleteModalWo(selectedWorkOrder)
                            setCompleteNotes('')
                            setCompleteCost('')
                          }}
                        >
                          ✓ Complete Work Order
                        </button>
                      </>
                  )}

                  {isLabManager && !['COMPLETED', 'CANCELLED'].includes(selectedWorkOrder.status) && (
                    <button
                      type="button"
                      className="secondary-button"
                      style={{ color: '#ef4444' }}
                      onClick={() => {
                        setCancelModalWo(selectedWorkOrder)
                        setCancelReason('')
                      }}
                    >
                      ✕ Cancel Work Order
                    </button>
                  )}
                </div>

                {/* Service Logs Timeline */}
                <div>
                  <h3 style={{ margin: '0 0 10px', fontSize: '1.05rem', fontWeight: 700 }}>Service Logs & Notes</h3>

                  {/* Add Note form */}
                  {(isLabManager || (isTechnician && selectedWorkOrder.assignedTo === user?.id)) && (
                    <form onSubmit={handleAddServiceLogNote} style={{ display: 'flex', gap: '8px', marginBottom: '16px' }}>
                      <input
                        type="text"
                        placeholder="Add a service note or observation…"
                        value={newLogNote}
                        onChange={(e) => setNewLogNote(e.target.value)}
                        style={{ flex: 1, padding: '6px 10px', borderRadius: '6px', border: '1px solid var(--border)', background: 'var(--surface)', color: 'var(--text)' }}
                      />
                      <button type="submit" className="button button-primary" disabled={logSubmitting || !newLogNote.trim()}>
                        {logSubmitting ? 'Posting…' : 'Add Note'}
                      </button>
                    </form>
                  )}

                  <div className="sharing-timeline-steps">
                    {serviceLogs.length === 0 ? (
                      <p style={{ color: 'var(--text-muted)', fontSize: '13px' }}>No service logs recorded yet.</p>
                    ) : (
                      serviceLogs.map((log) => (
                        <div key={log.id} className="sharing-timeline-step">
                          <div>
                            <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                              <strong>{log.authorName || 'Staff Member'}</strong>
                              <span style={{ fontSize: '11px', color: 'var(--text-muted)' }}>{formatDate(log.createdAt)}</span>
                              <span style={{ fontSize: '10.5px', padding: '1px 5px', borderRadius: '3px', background: '#e2e8f0', color: '#475569', fontWeight: 600 }}>{log.logType}</span>
                            </div>
                            <p style={{ margin: '3px 0 0', fontSize: '13px' }}>{log.notes}</p>
                          </div>
                        </div>
                      ))
                    )}
                  </div>
                </div>
              </div>
            </div>
          )}
        </div>
      )}

      {/* ============================================================= */}
      {/* 3. MAINTENANCE REQUESTS TAB */}
      {/* ============================================================= */}
      {activeTab === 'requests' && (
        <div>
          {isStaff && (
            <div style={{ display: 'flex', gap: '10px', marginBottom: '16px' }}>
              <button
                type="button"
                className={`secondary-button ${requestSubTab === 'queue' ? 'active' : ''}`}
                style={{ fontWeight: requestSubTab === 'queue' ? 700 : 500 }}
                onClick={() => setRequestSubTab('queue')}
              >
                📥 Manager Review Queue
              </button>
              <button
                type="button"
                className={`secondary-button ${requestSubTab === 'mine' ? 'active' : ''}`}
                style={{ fontWeight: requestSubTab === 'mine' ? 700 : 500 }}
                onClick={() => setRequestSubTab('mine')}
              >
                👤 My Submitted Requests
              </button>
            </div>
          )}

          <div className="booking-form-card">
            <h3 style={{ margin: '0 0 12px', fontSize: '1.05rem', fontWeight: 700 }}>
              {requestSubTab === 'queue' ? 'Maintenance Request Review Queue' : 'My Reported Equipment Problems'}
            </h3>

            {loading ? (
              <p>Loading requests…</p>
            ) : (requestSubTab === 'queue' ? queueRequests : myRequests).length === 0 ? (
              <p style={{ color: 'var(--text-muted)', textAlign: 'center', padding: '24px' }}>No maintenance requests found.</p>
            ) : (
              <div style={{ overflowX: 'auto' }}>
                <table className="module-table" style={{ width: '100%', borderCollapse: 'collapse' }}>
                  <thead>
                    <tr style={{ textAlign: 'left', borderBottom: '2px solid var(--border)', fontSize: '13px' }}>
                      <th style={{ padding: '10px' }}>Request Title</th>
                      <th style={{ padding: '10px' }}>Equipment</th>
                      <th style={{ padding: '10px' }}>Requester</th>
                      <th style={{ padding: '10px' }}>Priority</th>
                      <th style={{ padding: '10px' }}>Status</th>
                      <th style={{ padding: '10px' }}>Date</th>
                      <th style={{ padding: '10px' }}>Remarks / WO</th>
                      {isLabManager && requestSubTab === 'queue' && <th style={{ padding: '10px' }}>Review Actions</th>}
                    </tr>
                  </thead>
                  <tbody>
                    {(requestSubTab === 'queue' ? queueRequests : myRequests).map((req) => (
                      <tr key={req.id} style={{ borderBottom: '1px solid var(--border)', fontSize: '13.5px' }}>
                        <td style={{ padding: '10px', fontWeight: 600 }}>
                          <div>{req.title}</div>
                          <small style={{ color: 'var(--text-muted)' }}>{req.description}</small>
                        </td>
                        <td style={{ padding: '10px' }}>{req.equipmentName || `#${req.equipmentId}`}</td>
                        <td style={{ padding: '10px' }}>{req.requesterName || 'User'}</td>
                        <td style={{ padding: '10px' }}>
                          <span style={{ fontSize: '11.5px', padding: '2px 6px', borderRadius: '4px', background: req.priority === 'HIGH' || req.priority === 'CRITICAL' ? '#fee2e2' : '#f3f4f6', color: req.priority === 'HIGH' || req.priority === 'CRITICAL' ? '#b91c1c' : '#374151', fontWeight: 600 }}>
                            {req.priority}
                          </span>
                        </td>
                        <td style={{ padding: '10px' }}>
                          <span style={{
                            fontSize: '11.5px',
                            padding: '2px 6px',
                            borderRadius: '4px',
                            fontWeight: 700,
                            background: req.status === 'APPROVED' ? '#d1fae5' : (req.status === 'REJECTED' ? '#fee2e2' : '#fef3c7'),
                            color: req.status === 'APPROVED' ? '#065f46' : (req.status === 'REJECTED' ? '#991b1b' : '#92400e'),
                          }}>
                            {req.status}
                          </span>
                        </td>
                        <td style={{ padding: '10px', fontSize: '12px' }}>{formatDate(req.createdAt)}</td>
                        <td style={{ padding: '10px', fontSize: '12px' }}>
                          {req.workOrderNumber ? (
                            <span style={{ fontWeight: 600, color: 'var(--primary)' }}>WO: {req.workOrderNumber}</span>
                          ) : (
                            <span>{req.reviewRemarks || '—'}</span>
                          )}
                        </td>
                        {isLabManager && requestSubTab === 'queue' && (
                          <td style={{ padding: '10px' }}>
                            {req.status === 'PENDING' ? (
                              <div style={{ display: 'flex', gap: '6px' }}>
                                <button
                                  type="button"
                                  className="button button-primary"
                                  style={{ fontSize: '12px', padding: '3px 8px', background: '#10b981', borderColor: '#059669' }}
                                  onClick={() => {
                                    setApproveModalReq(req)
                                    setApproveRemarks('')
                                  }}
                                >
                                  Approve & Create WO
                                </button>
                                <button
                                  type="button"
                                  className="secondary-button"
                                  style={{ fontSize: '12px', padding: '3px 8px', color: '#ef4444' }}
                                  onClick={() => {
                                    setRejectModalReq(req)
                                    setRejectRemarks('')
                                  }}
                                >
                                  Reject
                                </button>
                              </div>
                            ) : (
                              <span style={{ color: 'var(--text-muted)', fontSize: '12px' }}>Reviewed</span>
                            )}
                          </td>
                        )}
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        </div>
      )}

      {/* ============================================================= */}
      {/* 4. PREVENTIVE SCHEDULES TAB */}
      {/* ============================================================= */}
      {activeTab === 'schedules' && isStaff && (
        <div>
          <div className="booking-form-card">
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '14px' }}>
              <div>
                <h3 style={{ margin: '0 0 4px', fontSize: '1.05rem', fontWeight: 700 }}>Preventive Maintenance Schedules</h3>
                <p style={{ margin: 0, fontSize: '13px', color: 'var(--text-muted)' }}>Automated background jobs create work orders 7 days ahead of due dates.</p>
              </div>
              {isLabManager && (
                <button
                  type="button"
                  className="button button-primary"
                  onClick={() => {
                    setEditingSchedule(null)
                    setScheduleForm({
                      equipmentId: '',
                      title: '',
                      description: '',
                      repeatPeriodDays: '30',
                      nextDueDate: new Date(Date.now() + 30 * 86400000).toISOString().split('T')[0],
                      active: true,
                    })
                    setScheduleError('')
                    setCreateSchedOpen(true)
                  }}
                >
                  + New Schedule
                </button>
              )}
            </div>

            {loading ? (
              <p>Loading schedules…</p>
            ) : schedules.length === 0 ? (
              <p style={{ color: 'var(--text-muted)', textAlign: 'center', padding: '24px' }}>No preventive schedules defined yet.</p>
            ) : (
              <div style={{ overflowX: 'auto' }}>
                <table className="module-table" style={{ width: '100%', borderCollapse: 'collapse' }}>
                  <thead>
                    <tr style={{ textAlign: 'left', borderBottom: '2px solid var(--border)', fontSize: '13px' }}>
                      <th style={{ padding: '10px' }}>Schedule Title</th>
                      <th style={{ padding: '10px' }}>Equipment</th>
                      <th style={{ padding: '10px' }}>Repeat Cycle</th>
                      <th style={{ padding: '10px' }}>Last Completed</th>
                      <th style={{ padding: '10px' }}>Next Due</th>
                      <th style={{ padding: '10px' }}>Status</th>
                      <th style={{ padding: '10px' }}>Active</th>
                      {isLabManager && <th style={{ padding: '10px' }}>Actions</th>}
                    </tr>
                  </thead>
                  <tbody>
                    {schedules.map((s) => {
                      const badge = getScheduleStatusBadge(s.status)
                      return (
                        <tr key={s.id} style={{ borderBottom: '1px solid var(--border)', fontSize: '13.5px' }}>
                          <td style={{ padding: '10px', fontWeight: 600 }}>
                            <div>{s.title}</div>
                            <small style={{ color: 'var(--text-muted)' }}>{s.description || 'No description'}</small>
                          </td>
                          <td style={{ padding: '10px' }}>{s.equipmentName || `#${s.equipmentId}`}</td>
                          <td style={{ padding: '10px' }}>Every {s.repeatPeriodDays} days</td>
                          <td style={{ padding: '10px' }}>{formatDateOnly(s.lastCompletedDate)}</td>
                          <td style={{ padding: '10px', fontWeight: 600 }}>{formatDateOnly(s.nextDueDate)}</td>
                          <td style={{ padding: '10px' }}>
                            <span style={{
                              fontSize: '11.5px',
                              padding: '2px 8px',
                              borderRadius: '4px',
                              fontWeight: 700,
                              background: badge.tone === 'success' ? '#d1fae5' : (badge.tone === 'warning' ? '#fef3c7' : (badge.tone === 'danger' ? '#fee2e2' : '#f1f5f9')),
                              color: badge.tone === 'success' ? '#065f46' : (badge.tone === 'warning' ? '#92400e' : (badge.tone === 'danger' ? '#991b1b' : '#475569')),
                            }}>
                              {badge.label}
                            </span>
                          </td>
                          <td style={{ padding: '10px' }}>
                            {isLabManager ? (
                              <button
                                type="button"
                                style={{
                                  background: s.active ? '#10b981' : '#94a3b8',
                                  color: '#fff',
                                  border: 'none',
                                  borderRadius: '12px',
                                  padding: '2px 10px',
                                  fontSize: '11.5px',
                                  fontWeight: 600,
                                  cursor: 'pointer',
                                }}
                                onClick={() => handleToggleScheduleActive(s)}
                              >
                                {s.active ? 'Active' : 'Inactive'}
                              </button>
                            ) : (
                              <span>{s.active ? 'Yes' : 'No'}</span>
                            )}
                          </td>
                          {isLabManager && (
                            <td style={{ padding: '10px' }}>
                              <div style={{ display: 'flex', gap: '6px' }}>
                                <button
                                  type="button"
                                  className="secondary-button"
                                  style={{ fontSize: '12px', padding: '3px 8px' }}
                                  onClick={() => {
                                    setEditingSchedule(s)
                                    setScheduleForm({
                                      equipmentId: String(s.equipmentId),
                                      title: s.title,
                                      description: s.description || '',
                                      repeatPeriodDays: String(s.repeatPeriodDays),
                                      nextDueDate: s.nextDueDate || '',
                                      active: s.active,
                                    })
                                    setScheduleError('')
                                    setCreateSchedOpen(true)
                                  }}
                                >
                                  Edit
                                </button>
                                <button
                                  type="button"
                                  className="secondary-button"
                                  style={{ fontSize: '12px', padding: '3px 8px', color: '#ef4444' }}
                                  onClick={() => handleDeleteSchedule(s)}
                                >
                                  Delete
                                </button>
                              </div>
                            </td>
                          )}
                        </tr>
                      )
                    })}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        </div>
      )}

      {/* ============================================================= */}
      {/* MODALS */}
      {/* ============================================================= */}

      {/* Report a Problem Modal */}
      {reportModalOpen && (
        <div className="modal-overlay" style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.5)', zIndex: 1100, display: 'flex', alignItems: 'center', justifyContent: 'center', padding: '16px' }}>
          <div style={{ background: 'var(--surface)', width: '100%', maxWidth: '520px', borderRadius: '12px', padding: '24px', boxShadow: 'var(--shadow)' }}>
            <h3 style={{ margin: '0 0 14px', fontSize: '1.2rem', fontWeight: 700 }}>Report Equipment Problem</h3>
            {reportError && <div className="module-alert" style={{ marginBottom: '12px' }}>{reportError}</div>}
            <form onSubmit={handleSubmitRequest} className="booking-form">
              <div className="form-field">
                <label>Equipment *</label>
                <select
                  required
                  value={reportForm.equipmentId}
                  onChange={(e) => setReportForm({ ...reportForm, equipmentId: e.target.value })}
                >
                  <option value="">Select Equipment</option>
                  {equipmentList.map((eq) => <option key={eq.id} value={eq.id}>{eq.name}</option>)}
                </select>
              </div>
              <div className="form-field">
                <label>Problem Summary (Title) *</label>
                <input
                  type="text"
                  required
                  placeholder="e.g. Laser flickering, temperature calibration drift"
                  value={reportForm.title}
                  onChange={(e) => setReportForm({ ...reportForm, title: e.target.value })}
                />
              </div>
              <div className="form-field">
                <label>Detailed Description *</label>
                <textarea
                  rows={3}
                  required
                  placeholder="Explain the symptom, when it occurred, or error codes shown…"
                  value={reportForm.description}
                  onChange={(e) => setReportForm({ ...reportForm, description: e.target.value })}
                />
              </div>
              <div className="form-field">
                <label>Priority</label>
                <select
                  value={reportForm.priority}
                  onChange={(e) => setReportForm({ ...reportForm, priority: e.target.value })}
                >
                  {MAINTENANCE_PRIORITIES.map((p) => <option key={p} value={p}>{p}</option>)}
                </select>
              </div>
              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px', marginTop: '16px' }}>
                <button type="button" className="secondary-button" onClick={() => setReportModalOpen(false)}>Cancel</button>
                <button type="submit" className="button button-primary">Submit Problem Report</button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Create Work Order Modal */}
      {createWoOpen && (
        <div className="modal-overlay" style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.5)', zIndex: 1100, display: 'flex', alignItems: 'center', justifyContent: 'center', padding: '16px' }}>
          <div style={{ background: 'var(--surface)', width: '100%', maxWidth: '560px', borderRadius: '12px', padding: '24px', boxShadow: 'var(--shadow)' }}>
            <h3 style={{ margin: '0 0 14px', fontSize: '1.2rem', fontWeight: 700 }}>Create New Work Order</h3>
            {createWoError && <div className="module-alert" style={{ marginBottom: '12px' }}>{createWoError}</div>}
            <form onSubmit={handleCreateWorkOrder} className="booking-form">
              <div className="form-field">
                <label>Equipment *</label>
                <select
                  required
                  value={createWoForm.equipmentId}
                  onChange={(e) => setCreateWoForm({ ...createWoForm, equipmentId: e.target.value })}
                >
                  <option value="">Select Equipment</option>
                  {equipmentList.map((eq) => <option key={eq.id} value={eq.id}>{eq.name}</option>)}
                </select>
              </div>
              <div className="form-field">
                <label>Title *</label>
                <input
                  type="text"
                  required
                  placeholder="e.g. Quarterly optical alignment & cleaning"
                  value={createWoForm.title}
                  onChange={(e) => setCreateWoForm({ ...createWoForm, title: e.target.value })}
                />
              </div>
              <div className="form-field">
                <label>Description *</label>
                <textarea
                  rows={3}
                  required
                  placeholder="Service requirements and maintenance scope…"
                  value={createWoForm.description}
                  onChange={(e) => setCreateWoForm({ ...createWoForm, description: e.target.value })}
                />
              </div>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px' }}>
                <div className="form-field">
                  <label>Type</label>
                  <select
                    value={createWoForm.type}
                    onChange={(e) => setCreateWoForm({ ...createWoForm, type: e.target.value })}
                  >
                    {WORK_ORDER_TYPES.map((t) => <option key={t} value={t}>{t}</option>)}
                  </select>
                </div>
                <div className="form-field">
                  <label>Priority</label>
                  <select
                    value={createWoForm.priority}
                    onChange={(e) => setCreateWoForm({ ...createWoForm, priority: e.target.value })}
                  >
                    {MAINTENANCE_PRIORITIES.map((p) => <option key={p} value={p}>{p}</option>)}
                  </select>
                </div>
              </div>
              <div className="form-field">
                <label>Assign Technician (Optional)</label>
                <select
                  value={createWoForm.assignedTo}
                  onChange={(e) => setCreateWoForm({ ...createWoForm, assignedTo: e.target.value })}
                >
                  <option value="">Leave Unassigned (Open)</option>
                  {technicians.map((t) => <option key={t.id} value={t.id}>{t.fullName || t.email}</option>)}
                </select>
              </div>
              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px', marginTop: '16px' }}>
                <button type="button" className="secondary-button" onClick={() => setCreateWoOpen(false)}>Cancel</button>
                <button type="submit" className="button button-primary">Create Work Order</button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Assign Technician Modal */}
      {assignModalWo && (
        <div className="modal-overlay" style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.5)', zIndex: 1200, display: 'flex', alignItems: 'center', justifyContent: 'center', padding: '16px' }}>
          <div style={{ background: 'var(--surface)', width: '100%', maxWidth: '420px', borderRadius: '12px', padding: '24px', boxShadow: 'var(--shadow)' }}>
            <h3 style={{ margin: '0 0 12px', fontSize: '1.15rem', fontWeight: 700 }}>Assign Technician</h3>
            <p style={{ margin: '0 0 14px', fontSize: '13px', color: 'var(--text-muted)' }}>Work Order: {assignModalWo.workOrderNumber}</p>
            <form onSubmit={handleAssignTechnician} className="booking-form">
              <div className="form-field">
                <label>Select Technician *</label>
                <select
                  required
                  value={assignTechId}
                  onChange={(e) => setAssignTechId(e.target.value)}
                >
                  <option value="">Select Technician</option>
                  {technicians.map((t) => <option key={t.id} value={t.id}>{t.fullName || t.email}</option>)}
                </select>
              </div>
              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px', marginTop: '16px' }}>
                <button type="button" className="secondary-button" onClick={() => setAssignModalWo(null)}>Cancel</button>
                <button type="submit" className="button button-primary">Confirm Assignment</button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Start Work Order & Live Start-Preview Modal */}
      {startModalWo && (
        <div className="modal-overlay" style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.5)', zIndex: 1200, display: 'flex', alignItems: 'center', justifyContent: 'center', padding: '16px' }}>
          <div style={{ background: 'var(--surface)', width: '100%', maxWidth: '540px', borderRadius: '12px', padding: '24px', boxShadow: 'var(--shadow)' }}>
            <h3 style={{ margin: '0 0 8px', fontSize: '1.2rem', fontWeight: 700 }}>Start Work Order & Downtime</h3>
            <p style={{ margin: '0 0 14px', fontSize: '13px', color: 'var(--text-muted)' }}>
              Starting this work order moves <strong>{startModalWo.equipmentName}</strong> into <strong>UNDER_MAINTENANCE</strong> status. Any overlapping bookings will be automatically cancelled.
            </p>
            <form onSubmit={handleStartWorkOrder} className="booking-form">
              <div className="form-field">
                <label>Expected Maintenance Completion Time *</label>
                <input
                  type="datetime-local"
                  required
                  value={startExpectedEnd}
                  onChange={(e) => {
                    setStartExpectedEnd(e.target.value)
                    fetchStartPreview(startModalWo.id, e.target.value)
                  }}
                />
              </div>

              {/* Overlapping Bookings Preview Box */}
              <div style={{ marginTop: '12px', background: 'var(--surface-muted, #f8fafc)', border: '1px solid var(--border)', borderRadius: '8px', padding: '12px' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '6px' }}>
                  <strong style={{ fontSize: '13px' }}>Overlapping Bookings Impact:</strong>
                  {previewLoading ? (
                    <span style={{ fontSize: '12px', color: 'var(--text-muted)' }}>Checking…</span>
                  ) : (
                    <span style={{
                      fontSize: '12px',
                      padding: '2px 8px',
                      borderRadius: '4px',
                      fontWeight: 700,
                      background: (startPreview?.affectedCount || 0) > 0 ? '#fee2e2' : '#d1fae5',
                      color: (startPreview?.affectedCount || 0) > 0 ? '#b91c1c' : '#065f46',
                    }}>
                      {startPreview?.affectedCount || 0} booking(s) affected
                    </span>
                  )}
                </div>
                {startPreview?.overlappingBookings && startPreview.overlappingBookings.length > 0 ? (
                  <ul style={{ margin: '6px 0 0', paddingLeft: '20px', fontSize: '12.5px', color: '#b91c1c' }}>
                    {startPreview.overlappingBookings.map((b) => (
                      <li key={b.bookingId}>
                        Booking #{b.bookingId}: {formatDate(b.startTime)} to {formatDate(b.endTime)} ({b.userName || 'User'})
                      </li>
                    ))}
                  </ul>
                ) : (
                  <p style={{ margin: '4px 0 0', fontSize: '12px', color: 'var(--text-muted)' }}>No confirmed bookings overlap with this maintenance window.</p>
                )}
              </div>

              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px', marginTop: '18px' }}>
                <button type="button" className="secondary-button" onClick={() => setStartModalWo(null)}>Cancel</button>
                <button type="submit" className="button button-primary">Confirm & Start Work Order</button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Extend Work Order Modal */}
      {extendModalWo && (
        <div className="modal-overlay" style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.5)', zIndex: 1200, display: 'flex', alignItems: 'center', justifyContent: 'center', padding: '16px' }}>
          <div style={{ background: 'var(--surface)', width: '100%', maxWidth: '440px', borderRadius: '12px', padding: '24px', boxShadow: 'var(--shadow)' }}>
            <h3 style={{ margin: '0 0 12px', fontSize: '1.15rem', fontWeight: 700 }}>Extend Maintenance Window</h3>
            <form onSubmit={handleExtendWorkOrder} className="booking-form">
              <div className="form-field">
                <label>New Expected Completion Time *</label>
                <input
                  type="datetime-local"
                  required
                  value={extendEndTime}
                  onChange={(e) => setExtendEndTime(e.target.value)}
                />
              </div>
              <div className="form-field">
                <label>Reason for Extension *</label>
                <textarea
                  rows={2}
                  required
                  placeholder="e.g. Awaiting replacement laser diode delivery…"
                  value={extendReason}
                  onChange={(e) => setExtendReason(e.target.value)}
                />
              </div>
              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px', marginTop: '16px' }}>
                <button type="button" className="secondary-button" onClick={() => setExtendModalWo(null)}>Cancel</button>
                <button type="submit" className="button button-primary">Confirm Extension</button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Complete Work Order Modal */}
      {completeModalWo && (
        <div className="modal-overlay" style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.5)', zIndex: 1200, display: 'flex', alignItems: 'center', justifyContent: 'center', padding: '16px' }}>
          <div style={{ background: 'var(--surface)', width: '100%', maxWidth: '460px', borderRadius: '12px', padding: '24px', boxShadow: 'var(--shadow)' }}>
            <h3 style={{ margin: '0 0 12px', fontSize: '1.15rem', fontWeight: 700 }}>Complete Work Order</h3>
            <p style={{ margin: '0 0 14px', fontSize: '13px', color: 'var(--text-muted)' }}>
              Completing this order calculates downtime and restores <strong>{completeModalWo.equipmentName}</strong> to Available status.
            </p>
            <form onSubmit={handleCompleteWorkOrder} className="booking-form">
              <div className="form-field">
                <label>Resolution Notes *</label>
                <textarea
                  rows={3}
                  required
                  placeholder="Detail the repairs, calibrations, or inspections conducted…"
                  value={completeNotes}
                  onChange={(e) => setCompleteNotes(e.target.value)}
                />
              </div>
              <div className="form-field">
                <label>Cost Incurred (₹, optional)</label>
                <input
                  type="number"
                  min="0"
                  step="0.01"
                  placeholder="e.g. 2500"
                  value={completeCost}
                  onChange={(e) => setCompleteCost(e.target.value)}
                />
              </div>
              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px', marginTop: '16px' }}>
                <button type="button" className="secondary-button" onClick={() => setCompleteModalWo(null)}>Cancel</button>
                <button type="submit" className="button button-primary" style={{ background: '#10b981', borderColor: '#059669' }}>
                  Confirm & Mark Completed
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Cancel Work Order Modal */}
      {cancelModalWo && (
        <div className="modal-overlay" style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.5)', zIndex: 1200, display: 'flex', alignItems: 'center', justifyContent: 'center', padding: '16px' }}>
          <div style={{ background: 'var(--surface)', width: '100%', maxWidth: '420px', borderRadius: '12px', padding: '24px', boxShadow: 'var(--shadow)' }}>
            <h3 style={{ margin: '0 0 12px', fontSize: '1.15rem', fontWeight: 700, color: '#ef4444' }}>Cancel Work Order</h3>
            <form onSubmit={handleCancelWorkOrder} className="booking-form">
              <div className="form-field">
                <label>Cancellation Reason *</label>
                <textarea
                  rows={2}
                  required
                  placeholder="Why is this work order being cancelled?"
                  value={cancelReason}
                  onChange={(e) => setCancelReason(e.target.value)}
                />
              </div>
              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px', marginTop: '16px' }}>
                <button type="button" className="secondary-button" onClick={() => setCancelModalWo(null)}>Close</button>
                <button type="submit" className="button button-primary" style={{ background: '#ef4444', borderColor: '#dc2626' }}>
                  Confirm Cancellation
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Approve Request Modal */}
      {approveModalReq && (
        <div className="modal-overlay" style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.5)', zIndex: 1200, display: 'flex', alignItems: 'center', justifyContent: 'center', padding: '16px' }}>
          <div style={{ background: 'var(--surface)', width: '100%', maxWidth: '440px', borderRadius: '12px', padding: '24px', boxShadow: 'var(--shadow)' }}>
            <h3 style={{ margin: '0 0 10px', fontSize: '1.15rem', fontWeight: 700 }}>Approve Maintenance Request</h3>
            <p style={{ margin: '0 0 14px', fontSize: '13px', color: 'var(--text-muted)' }}>
              Approving request "#{approveModalReq.id} - {approveModalReq.title}" will automatically generate a new <strong>CORRECTIVE</strong> work order.
            </p>
            <form onSubmit={handleApproveRequest} className="booking-form">
              <div className="form-field">
                <label>Approval Remarks (Optional)</label>
                <textarea
                  rows={2}
                  placeholder="Remarks for the technician or work order…"
                  value={approveRemarks}
                  onChange={(e) => setApproveRemarks(e.target.value)}
                />
              </div>
              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px', marginTop: '16px' }}>
                <button type="button" className="secondary-button" onClick={() => setApproveModalReq(null)}>Cancel</button>
                <button type="submit" className="button button-primary" style={{ background: '#10b981', borderColor: '#059669' }}>
                  Approve & Create Work Order
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Reject Request Modal */}
      {rejectModalReq && (
        <div className="modal-overlay" style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.5)', zIndex: 1200, display: 'flex', alignItems: 'center', justifyContent: 'center', padding: '16px' }}>
          <div style={{ background: 'var(--surface)', width: '100%', maxWidth: '440px', borderRadius: '12px', padding: '24px', boxShadow: 'var(--shadow)' }}>
            <h3 style={{ margin: '0 0 10px', fontSize: '1.15rem', fontWeight: 700, color: '#ef4444' }}>Reject Maintenance Request</h3>
            <form onSubmit={handleRejectRequest} className="booking-form">
              <div className="form-field">
                <label>Rejection Remarks *</label>
                <textarea
                  rows={3}
                  required
                  placeholder="Explain why this request is being rejected (e.g. user error, operator issue)…"
                  value={rejectRemarks}
                  onChange={(e) => setRejectRemarks(e.target.value)}
                />
              </div>
              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px', marginTop: '16px' }}>
                <button type="button" className="secondary-button" onClick={() => setRejectModalReq(null)}>Cancel</button>
                <button type="submit" className="button button-primary" style={{ background: '#ef4444', borderColor: '#dc2626' }}>
                  Reject Request
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Create / Edit Schedule Modal */}
      {createSchedOpen && (
        <div className="modal-overlay" style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.5)', zIndex: 1100, display: 'flex', alignItems: 'center', justifyContent: 'center', padding: '16px' }}>
          <div style={{ background: 'var(--surface)', width: '100%', maxWidth: '520px', borderRadius: '12px', padding: '24px', boxShadow: 'var(--shadow)' }}>
            <h3 style={{ margin: '0 0 14px', fontSize: '1.2rem', fontWeight: 700 }}>
              {editingSchedule ? 'Edit Preventive Schedule' : 'Create Preventive Schedule'}
            </h3>
            {scheduleError && <div className="module-alert" style={{ marginBottom: '12px' }}>{scheduleError}</div>}
            <form onSubmit={handleSaveSchedule} className="booking-form">
              {!editingSchedule && (
                <div className="form-field">
                  <label>Equipment *</label>
                  <select
                    required
                    value={scheduleForm.equipmentId}
                    onChange={(e) => setScheduleForm({ ...scheduleForm, equipmentId: e.target.value })}
                  >
                    <option value="">Select Equipment</option>
                    {equipmentList.map((eq) => <option key={eq.id} value={eq.id}>{eq.name}</option>)}
                  </select>
                </div>
              )}
              <div className="form-field">
                <label>Schedule Title *</label>
                <input
                  type="text"
                  required
                  placeholder="e.g. Bi-monthly Laser Power Calibration"
                  value={scheduleForm.title}
                  onChange={(e) => setScheduleForm({ ...scheduleForm, title: e.target.value })}
                />
              </div>
              <div className="form-field">
                <label>Description</label>
                <textarea
                  rows={2}
                  placeholder="Calibration procedures or preventive maintenance instructions…"
                  value={scheduleForm.description}
                  onChange={(e) => setScheduleForm({ ...scheduleForm, description: e.target.value })}
                />
              </div>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px' }}>
                <div className="form-field">
                  <label>Repeat Cycle (Days) *</label>
                  <input
                    type="number"
                    min="1"
                    max="3650"
                    required
                    value={scheduleForm.repeatPeriodDays}
                    onChange={(e) => setScheduleForm({ ...scheduleForm, repeatPeriodDays: e.target.value })}
                  />
                </div>
                <div className="form-field">
                  <label>Next Due Date *</label>
                  <input
                    type="date"
                    required
                    value={scheduleForm.nextDueDate}
                    onChange={(e) => setScheduleForm({ ...scheduleForm, nextDueDate: e.target.value })}
                  />
                </div>
              </div>
              <div className="form-field">
                <label style={{ display: 'flex', alignItems: 'center', gap: '8px', cursor: 'pointer', fontWeight: 600 }}>
                  <input
                    type="checkbox"
                    checked={scheduleForm.active}
                    onChange={(e) => setScheduleForm({ ...scheduleForm, active: e.target.checked })}
                  />
                  <span>Active schedule (generates work orders 7 days ahead)</span>
                </label>
              </div>
              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px', marginTop: '16px' }}>
                <button type="button" className="secondary-button" onClick={() => setCreateSchedOpen(false)}>Cancel</button>
                <button type="submit" className="button button-primary">Save Schedule</button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  )
}
