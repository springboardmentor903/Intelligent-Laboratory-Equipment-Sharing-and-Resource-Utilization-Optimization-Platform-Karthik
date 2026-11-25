import { useState, useEffect, useCallback } from 'react'
import { Link } from 'react-router-dom'
import api from '../api/axiosInstance'
import { useAuth } from '../context/AuthContext'
import DocumentUploadModal from '../components/DocumentUploadModal'
import {
  getTechnicianTaskGroupBadge,
  isSystemAdminForbidden,
} from '../access/maintenanceWorkflow.js'

export default function MyTasks() {
  const { user } = useAuth()
  const roles = user?.roles || []
  const isSysAdmin = isSystemAdminForbidden(roles)

  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')

  // Task response data
  const [taskResponse, setTaskResponse] = useState(null)
  const [activeGroup, setActiveGroup] = useState('ALL') // ALL, OVERDUE, TODAY, THIS_WEEK, LATER
  const [statusFilter, setStatusFilter] = useState('')
  const [priorityFilter, setPriorityFilter] = useState('')

  // Action Modals
  const [startModalTask, setStartModalTask] = useState(null)
  const [expectedHours, setExpectedHours] = useState('2')
  const [startNotes, setStartNotes] = useState('')
  const [starting, setStarting] = useState(false)

  // Complete Modal
  const [completeModalTask, setCompleteModalTask] = useState(null)
  const [completeNotes, setCompleteNotes] = useState('')
  const [completeCost, setCompleteCost] = useState('')
  const [calDate, setCalDate] = useState('')
  const [calResult, setCalResult] = useState('PASS')
  const [calNextDue, setCalNextDue] = useState('')
  const [calCertNumber, setCalCertNumber] = useState('')
  const [completing, setCompleting] = useState(false)

  // Document upload modal
  const [docModalOpen, setDocModalOpen] = useState(false)
  const [selectedEntityId, setSelectedEntityId] = useState(null)
  const [selectedEntityType, setSelectedEntityType] = useState('MAINTENANCE_REPORT')
  const [docModalTitle, setDocModalTitle] = useState('')

  const fetchTasks = useCallback(async () => {
    if (isSysAdmin) return
    setLoading(true)
    setError('')
    try {
      const params = {}
      if (statusFilter) params.status = statusFilter
      if (priorityFilter) params.priority = priorityFilter
      if (activeGroup && activeGroup !== 'ALL') params.taskGroup = activeGroup

      const res = await api.get('/api/technicians/my-tasks', { params })
      setTaskResponse(res.data)
    } catch (err) {
      console.error('Failed to load technician tasks', err)
      setError('Could not load assigned tasks. Please try again.')
    } finally {
      setLoading(false)
    }
  }, [activeGroup, statusFilter, priorityFilter, isSysAdmin])

  useEffect(() => {
    if (!isSysAdmin) {
      fetchTasks()
    }
  }, [fetchTasks, isSysAdmin])

  function handleStartClick(task) {
    setStartModalTask(task)
    setExpectedHours('2')
    setStartNotes('')
  }

  async function handleConfirmStart(e) {
    e.preventDefault()
    if (!startModalTask) return

    setStarting(true)
    setError('')
    try {
      const hours = parseFloat(expectedHours) || 2
      const expectedEnd = new Date(Date.now() + hours * 3600000).toISOString().slice(0, 19)

      await api.post(`/api/maintenance/work-orders/${startModalTask.id}/start`, {
        expectedEndTime: expectedEnd,
        notes: startNotes.trim() || undefined,
      })

      setNotice(`Work order ${startModalTask.workOrderNumber} started! Equipment marked UNDER_MAINTENANCE.`)
      setStartModalTask(null)
      fetchTasks()
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to start work order.')
    } finally {
      setStarting(false)
    }
  }

  function handleCompleteClick(task) {
    setCompleteModalTask(task)
    setCompleteNotes('')
    setCompleteCost('')

    const todayStr = new Date().toISOString().split('T')[0]
    setCalDate(todayStr)
    setCalResult('PASS')
    // Default next due date 180 days from today
    const future = new Date(Date.now() + 180 * 86400000).toISOString().split('T')[0]
    setCalNextDue(future)
    setCalCertNumber('')
  }

  async function handleConfirmComplete(e) {
    e.preventDefault()
    if (!completeModalTask) return

    if (!completeNotes.trim()) {
      setError('Resolution notes are required.')
      return
    }

    setCompleting(true)
    setError('')
    try {
      const payload = {
        resolutionNotes: completeNotes.trim(),
        cost: completeCost ? parseFloat(completeCost) : undefined,
      }

      if (completeModalTask.workOrderType === 'CALIBRATION') {
        payload.calibrationDate = calDate || new Date().toISOString().split('T')[0]
        payload.result = calResult
        payload.nextDueDate = calNextDue
        payload.certificateNumber = calCertNumber.trim() || undefined
      }

      await api.post(`/api/maintenance/work-orders/${completeModalTask.id}/complete`, payload)

      setNotice(`Work order ${completeModalTask.workOrderNumber} completed successfully!`)
      setCompleteModalTask(null)
      fetchTasks()
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to complete work order.')
    } finally {
      setCompleting(false)
    }
  }

  function openReportUpload(task) {
    setSelectedEntityId(task.id)
    setSelectedEntityType(task.workOrderType === 'CALIBRATION' ? 'CALIBRATION_CERTIFICATE' : 'MAINTENANCE_REPORT')
    setDocModalTitle(`Attachments — ${task.workOrderNumber}`)
    setDocModalOpen(true)
  }

  if (isSysAdmin) {
    return (
      <div className="page-shell p-6">
        <div className="p-6 rounded-xl bg-amber-50 dark:bg-amber-950/40 border border-amber-200 dark:border-amber-800 text-amber-900 dark:text-amber-200">
          <h2 className="text-xl font-bold mb-2">Restricted Access</h2>
          <p>System Administrators do not have access to technician work orders.</p>
        </div>
      </div>
    )
  }

  const counts = taskResponse?.counts || { total: 0, assigned: 0, inProgress: 0, completed: 0, overdue: 0 }
  const tasks = taskResponse?.content || []

  return (
    <div className="page-shell max-w-7xl mx-auto p-4 md:p-6 space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-2 border-b border-slate-200 dark:border-slate-800">
        <div>
          <div className="flex items-center gap-2">
            <span className="text-2xl" aria-hidden="true">🛠️</span>
            <h1 className="text-2xl font-bold text-slate-900 dark:text-slate-100">
              My Assigned Tasks
            </h1>
          </div>
          <p className="text-sm text-slate-500 dark:text-slate-400 mt-1">
            Prioritized work queue, maintenance schedules, calibrations, and service logging.
          </p>
        </div>

        <div className="flex items-center gap-2">
          <button
            type="button"
            className="px-3.5 py-2 text-sm font-semibold rounded-lg bg-slate-100 dark:bg-slate-800 text-slate-700 dark:text-slate-300 hover:bg-slate-200 dark:hover:bg-slate-700 transition"
            onClick={fetchTasks}
            disabled={loading}
          >
            ↻ Refresh
          </button>
          <Link
            to="/calibration"
            className="px-3.5 py-2 text-sm font-semibold rounded-lg bg-indigo-600 text-white hover:bg-indigo-700 transition"
          >
            Calibration Overview →
          </Link>
        </div>
      </div>

      {notice && (
        <div className="p-4 rounded-xl bg-emerald-50 dark:bg-emerald-950/40 text-emerald-800 dark:text-emerald-200 border border-emerald-200 dark:border-emerald-800 text-sm font-medium flex justify-between items-center" role="status">
          <span>{notice}</span>
          <button type="button" onClick={() => setNotice('')} className="font-bold px-2 py-0.5 text-emerald-600 hover:text-emerald-900">✕</button>
        </div>
      )}

      {error && (
        <div className="p-4 rounded-xl bg-rose-50 dark:bg-rose-950/40 text-rose-800 dark:text-rose-200 border border-rose-200 dark:border-rose-800 text-sm font-medium flex justify-between items-center" role="alert">
          <span>{error}</span>
          <button type="button" onClick={fetchTasks} className="underline font-semibold ml-4">Retry</button>
        </div>
      )}

      {/* Summary Metrics Cards */}
      <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-5 gap-3">
        <div className="p-4 rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 shadow-sm">
          <span className="text-xs font-semibold text-slate-500 dark:text-slate-400 uppercase">Total Assigned</span>
          <p className="text-2xl font-black text-slate-900 dark:text-slate-100 mt-1">{counts.total || 0}</p>
        </div>

        <div className="p-4 rounded-xl border border-blue-200 dark:border-blue-900/60 bg-blue-50/40 dark:bg-blue-950/20 shadow-sm">
          <span className="text-xs font-semibold text-blue-700 dark:text-blue-300 uppercase">Queued / Assigned</span>
          <p className="text-2xl font-black text-blue-800 dark:text-blue-200 mt-1">{counts.assigned || 0}</p>
        </div>

        <div className="p-4 rounded-xl border border-amber-200 dark:border-amber-900/60 bg-amber-50/40 dark:bg-amber-950/20 shadow-sm">
          <span className="text-xs font-semibold text-amber-700 dark:text-amber-300 uppercase">In Progress</span>
          <p className="text-2xl font-black text-amber-800 dark:text-amber-200 mt-1">{counts.inProgress || 0}</p>
        </div>

        <div className="p-4 rounded-xl border border-emerald-200 dark:border-emerald-900/60 bg-emerald-50/40 dark:bg-emerald-950/20 shadow-sm">
          <span className="text-xs font-semibold text-emerald-700 dark:text-emerald-300 uppercase">Completed</span>
          <p className="text-2xl font-black text-emerald-800 dark:text-emerald-200 mt-1">{counts.completed || 0}</p>
        </div>

        <div className={`p-4 rounded-xl border shadow-sm ${
          (counts.overdue || 0) > 0
            ? 'border-rose-300 dark:border-rose-900 bg-rose-50/60 dark:bg-rose-950/30'
            : 'border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900'
        }`}>
          <span className={`text-xs font-semibold uppercase ${
            (counts.overdue || 0) > 0 ? 'text-rose-700 dark:text-rose-300' : 'text-slate-500'
          }`}>
            Overdue Tasks
          </span>
          <p className={`text-2xl font-black mt-1 ${
            (counts.overdue || 0) > 0 ? 'text-rose-800 dark:text-rose-200' : 'text-slate-700 dark:text-slate-300'
          }`}>
            {counts.overdue || 0}
          </p>
        </div>
      </div>

      {/* Task Group Filter Tabs */}
      <div className="flex flex-wrap items-center justify-between gap-3 bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 p-2 rounded-xl">
        <div className="flex flex-wrap gap-1.5" role="tablist" aria-label="Task Groups">
          {[
            { id: 'ALL', label: 'All Tasks' },
            { id: 'OVERDUE', label: `Overdue (${counts.overdue || 0})`, danger: (counts.overdue || 0) > 0 },
            { id: 'TODAY', label: 'Today' },
            { id: 'THIS_WEEK', label: 'This Week' },
            { id: 'LATER', label: 'Later' },
          ].map((tab) => (
            <button
              key={tab.id}
              type="button"
              role="tab"
              aria-selected={activeGroup === tab.id}
              className={`px-3.5 py-1.5 text-xs font-bold rounded-lg transition ${
                activeGroup === tab.id
                  ? 'bg-indigo-600 text-white shadow-sm'
                  : tab.danger
                  ? 'bg-rose-50 dark:bg-rose-950/40 text-rose-700 dark:text-rose-300 hover:bg-rose-100'
                  : 'text-slate-600 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-800'
              }`}
              onClick={() => setActiveGroup(tab.id)}
            >
              {tab.label}
            </button>
          ))}
        </div>

        <div className="flex items-center gap-2">
          <select
            className="text-xs p-1.5 rounded-lg border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-800 text-slate-800 dark:text-slate-200"
            value={statusFilter}
            onChange={(e) => setStatusFilter(e.target.value)}
          >
            <option value="">All Statuses</option>
            <option value="ASSIGNED">Assigned</option>
            <option value="IN_PROGRESS">In Progress</option>
            <option value="COMPLETED">Completed</option>
            <option value="CANCELLED">Cancelled</option>
          </select>

          <select
            className="text-xs p-1.5 rounded-lg border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-800 text-slate-800 dark:text-slate-200"
            value={priorityFilter}
            onChange={(e) => setPriorityFilter(e.target.value)}
          >
            <option value="">All Priorities</option>
            <option value="CRITICAL">Critical</option>
            <option value="HIGH">High</option>
            <option value="MEDIUM">Medium</option>
            <option value="LOW">Low</option>
          </select>
        </div>
      </div>

      {/* Task List */}
      {loading ? (
        <div className="py-16 text-center text-slate-500 text-sm">Loading assigned tasks…</div>
      ) : tasks.length === 0 ? (
        <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-xl p-12 text-center text-slate-500">
          <p className="text-3xl mb-2">🎉</p>
          <p className="font-semibold text-slate-800 dark:text-slate-200">No work orders in this view.</p>
          <p className="text-xs text-slate-400 mt-1">Select another group or adjust filters above.</p>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {tasks.map((task) => {
            const groupBadge = getTechnicianTaskGroupBadge(task.taskGroup)
            const isOverdue = Boolean(task.isOverdue)
            const isAssigned = task.status === 'ASSIGNED' || task.status === 'OPEN'
            const isInProgress = task.status === 'IN_PROGRESS'
            const isCal = task.workOrderType === 'CALIBRATION'

            return (
              <div
                key={task.id}
                className={`bg-white dark:bg-slate-900 border rounded-xl p-4 shadow-sm flex flex-col justify-between transition ${
                  isOverdue
                    ? 'border-rose-300 dark:border-rose-900/80 bg-rose-50/20 dark:bg-rose-950/10'
                    : 'border-slate-200 dark:border-slate-800'
                }`}
              >
                <div>
                  <div className="flex items-start justify-between gap-2">
                    <div>
                      <span className="text-xs font-mono font-bold text-indigo-600 dark:text-indigo-400">
                        {task.workOrderNumber}
                      </span>
                      <h3 className="font-bold text-slate-900 dark:text-slate-100 text-base leading-snug">
                        {task.title}
                      </h3>
                    </div>

                    <div className="flex flex-col items-end gap-1 shrink-0">
                      <span className={`text-xs font-extrabold px-2 py-0.5 rounded uppercase ${
                        task.priority === 'CRITICAL' || task.priority === 'HIGH'
                          ? 'bg-rose-100 text-rose-800 dark:bg-rose-950 dark:text-rose-300'
                          : task.priority === 'MEDIUM'
                          ? 'bg-amber-100 text-amber-800 dark:bg-amber-950 dark:text-amber-300'
                          : 'bg-slate-100 text-slate-700 dark:bg-slate-800 dark:text-slate-300'
                      }`}>
                        {task.priority}
                      </span>
                      <span className={`text-xs font-bold px-2 py-0.5 rounded-full ${
                        isInProgress
                          ? 'bg-amber-100 text-amber-800 dark:bg-amber-950 dark:text-amber-300'
                          : task.status === 'COMPLETED'
                          ? 'bg-emerald-100 text-emerald-800 dark:bg-emerald-950 dark:text-emerald-300'
                          : 'bg-blue-100 text-blue-800 dark:bg-blue-950 dark:text-blue-300'
                      }`}>
                        {task.status}
                      </span>
                    </div>
                  </div>

                  <p className="text-xs text-slate-600 dark:text-slate-300 mt-2 line-clamp-2">
                    {task.description}
                  </p>

                  <div className="mt-3 pt-2.5 border-t border-slate-100 dark:border-slate-800 flex flex-wrap gap-x-4 gap-y-1 text-xs text-slate-500 dark:text-slate-400">
                    <div>
                      <span className="font-semibold text-slate-700 dark:text-slate-300">Equipment: </span>
                      {task.equipmentName || `#${task.equipmentId}`}
                      {task.assetTag ? ` (${task.assetTag})` : ''}
                    </div>
                    <div>
                      <span className="font-semibold text-slate-700 dark:text-slate-300">Type: </span>
                      <span className="font-medium text-slate-800 dark:text-slate-200">{task.workOrderType}</span>
                    </div>
                    <div>
                      <span className="font-semibold text-slate-700 dark:text-slate-300">Scheduled: </span>
                      <span className={`font-semibold ${isOverdue ? 'text-rose-600 dark:text-rose-400' : ''}`}>
                        {task.scheduledDate ? new Date(task.scheduledDate).toLocaleDateString() : 'Unscheduled'}
                        {isOverdue && ' (OVERDUE)'}
                      </span>
                    </div>
                  </div>
                </div>

                {/* Actions Footer */}
                <div className="mt-4 pt-3 border-t border-slate-100 dark:border-slate-800 flex items-center justify-between gap-2">
                  <div className="flex items-center gap-1.5">
                    <span className={`text-xs font-semibold px-2 py-0.5 rounded ${
                      groupBadge.tone === 'danger'
                        ? 'bg-rose-100 text-rose-800 dark:bg-rose-950 dark:text-rose-300'
                        : groupBadge.tone === 'info'
                        ? 'bg-sky-100 text-sky-800 dark:bg-sky-950 dark:text-sky-300'
                        : 'bg-slate-100 text-slate-700 dark:bg-slate-800 dark:text-slate-400'
                    }`}>
                      {groupBadge.label}
                    </span>
                    <button
                      type="button"
                      className="text-xs font-medium text-slate-600 dark:text-slate-400 hover:text-indigo-600 dark:hover:text-indigo-400 transition"
                      onClick={() => openReportUpload(task)}
                      title="Attach documents or reports"
                    >
                      📎 Docs
                    </button>
                  </div>

                  <div className="flex items-center gap-2">
                    {isAssigned && (
                      <button
                        type="button"
                        className="px-3 py-1.5 text-xs font-bold rounded-lg bg-indigo-600 text-white hover:bg-indigo-700 transition"
                        onClick={() => handleStartClick(task)}
                      >
                        ▶ Start Work Order
                      </button>
                    )}

                    {isInProgress && (
                      <button
                        type="button"
                        className="px-3 py-1.5 text-xs font-bold rounded-lg bg-emerald-600 text-white hover:bg-emerald-700 transition"
                        onClick={() => handleCompleteClick(task)}
                      >
                        ✓ Complete {isCal ? 'Calibration' : 'Work Order'}
                      </button>
                    )}
                  </div>
                </div>
              </div>
            )
          })}
        </div>
      )}

      {/* Start Modal */}
      {startModalTask && (
        <div className="modal-backdrop" onClick={() => setStartModalTask(null)}>
          <div className="modal-card max-w-md w-full p-6" onClick={(e) => e.stopPropagation()}>
            <h3 className="text-lg font-bold text-slate-900 dark:text-slate-100 mb-2">
              Start Work Order: {startModalTask.workOrderNumber}
            </h3>
            <p className="text-xs text-slate-500 dark:text-slate-400 mb-4">
              Starting this work order will transition <strong>{startModalTask.equipmentName}</strong> into <code>UNDER_MAINTENANCE</code> and create an active downtime log.
            </p>

            <form onSubmit={handleConfirmStart} className="space-y-4 text-sm">
              <div>
                <label className="block text-xs font-semibold text-slate-700 dark:text-slate-300 mb-1">
                  Expected Duration (Hours) *
                </label>
                <input
                  type="number"
                  step="0.5"
                  min="0.5"
                  max="168"
                  className="w-full p-2 rounded-lg border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-800 text-slate-800 dark:text-slate-200"
                  value={expectedHours}
                  onChange={(e) => setExpectedHours(e.target.value)}
                  required
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-700 dark:text-slate-300 mb-1">
                  Initial Notes / Inspection Observations
                </label>
                <textarea
                  rows={3}
                  className="w-full p-2 rounded-lg border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-800 text-slate-800 dark:text-slate-200"
                  placeholder="e.g. Begun laser optical alignment. Cleaned mirror surfaces."
                  value={startNotes}
                  onChange={(e) => setStartNotes(e.target.value)}
                />
              </div>

              <div className="flex justify-end gap-2 pt-2">
                <button
                  type="button"
                  className="px-4 py-2 rounded-lg border border-slate-300 dark:border-slate-700"
                  onClick={() => setStartModalTask(null)}
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="px-4 py-2 rounded-lg bg-indigo-600 text-white font-semibold hover:bg-indigo-700 disabled:opacity-50"
                  disabled={starting}
                >
                  {starting ? 'Starting…' : 'Confirm & Start Work'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Complete Modal */}
      {completeModalTask && (
        <div className="modal-backdrop" onClick={() => setCompleteModalTask(null)}>
          <div className="modal-card max-w-lg w-full p-6" onClick={(e) => e.stopPropagation()}>
            <h3 className="text-lg font-bold text-slate-900 dark:text-slate-100 mb-2">
              Complete Work Order: {completeModalTask.workOrderNumber}
            </h3>
            <p className="text-xs text-slate-500 dark:text-slate-400 mb-4">
              Recording completion will close the downtime log and restore <strong>{completeModalTask.equipmentName}</strong> to <code>AVAILABLE</code> status.
            </p>

            <form onSubmit={handleConfirmComplete} className="space-y-4 text-sm">
              <div>
                <label className="block text-xs font-semibold text-slate-700 dark:text-slate-300 mb-1">
                  Resolution Notes *
                </label>
                <textarea
                  rows={3}
                  className="w-full p-2 rounded-lg border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-800 text-slate-800 dark:text-slate-200"
                  placeholder="Detail repairs, calibration results, or parts replaced…"
                  value={completeNotes}
                  onChange={(e) => setCompleteNotes(e.target.value)}
                  required
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-700 dark:text-slate-300 mb-1">
                  Service / Parts Cost (₹, optional)
                </label>
                <input
                  type="number"
                  step="0.01"
                  min="0"
                  className="w-full p-2 rounded-lg border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-800 text-slate-800 dark:text-slate-200"
                  placeholder="0.00"
                  value={completeCost}
                  onChange={(e) => setCompleteCost(e.target.value)}
                />
              </div>

              {/* Calibration Fields (automatic for CALIBRATION type) */}
              {completeModalTask.workOrderType === 'CALIBRATION' && (
                <div className="p-3.5 rounded-lg border border-indigo-200 dark:border-indigo-900 bg-indigo-50/40 dark:bg-indigo-950/20 space-y-3">
                  <div className="font-bold text-xs text-indigo-900 dark:text-indigo-200 flex items-center gap-1.5">
                    <span>📐</span>
                    <span>Calibration Record Details</span>
                  </div>

                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                    <div>
                      <label className="block text-xs font-semibold text-slate-700 dark:text-slate-300 mb-1">
                        Calibration Date *
                      </label>
                      <input
                        type="date"
                        className="w-full p-2 rounded-lg border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-800 text-slate-800 dark:text-slate-200"
                        value={calDate}
                        onChange={(e) => setCalDate(e.target.value)}
                        required
                      />
                    </div>

                    <div>
                      <label className="block text-xs font-semibold text-slate-700 dark:text-slate-300 mb-1">
                        Result *
                      </label>
                      <select
                        className="w-full p-2 rounded-lg border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-800 text-slate-800 dark:text-slate-200"
                        value={calResult}
                        onChange={(e) => setCalResult(e.target.value)}
                      >
                        <option value="PASS">PASS</option>
                        <option value="FAIL">FAIL</option>
                        <option value="CONDITIONAL">CONDITIONAL</option>
                      </select>
                    </div>

                    <div>
                      <label className="block text-xs font-semibold text-slate-700 dark:text-slate-300 mb-1">
                        Next Due Date *
                      </label>
                      <input
                        type="date"
                        className="w-full p-2 rounded-lg border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-800 text-slate-800 dark:text-slate-200"
                        value={calNextDue}
                        onChange={(e) => setCalNextDue(e.target.value)}
                        required
                      />
                    </div>

                    <div>
                      <label className="block text-xs font-semibold text-slate-700 dark:text-slate-300 mb-1">
                        Certificate Number
                      </label>
                      <input
                        type="text"
                        className="w-full p-2 rounded-lg border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-800 text-slate-800 dark:text-slate-200"
                        placeholder="e.g. CERT-2026-09"
                        value={calCertNumber}
                        onChange={(e) => setCalCertNumber(e.target.value)}
                      />
                    </div>
                  </div>
                </div>
              )}

              <div className="flex justify-end gap-2 pt-2">
                <button
                  type="button"
                  className="px-4 py-2 rounded-lg border border-slate-300 dark:border-slate-700"
                  onClick={() => setCompleteModalTask(null)}
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="px-4 py-2 rounded-lg bg-emerald-600 text-white font-semibold hover:bg-emerald-700 disabled:opacity-50"
                  disabled={completing}
                >
                  {completing ? 'Completing…' : 'Complete & Close Work Order'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Reusable Document Upload Modal */}
      <DocumentUploadModal
        isOpen={docModalOpen}
        onClose={() => setDocModalOpen(false)}
        entityType={selectedEntityType}
        entityId={selectedEntityId}
        title={docModalTitle}
        onSuccess={() => {
          fetchTasks()
        }}
      />
    </div>
  )
}
