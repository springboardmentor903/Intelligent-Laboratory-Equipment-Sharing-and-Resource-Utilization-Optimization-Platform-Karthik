import { useState, useEffect, useCallback } from 'react'
import { Link } from 'react-router-dom'
import api from '../api/axiosInstance'
import { useAuth } from '../context/AuthContext'
import {
  canViewTechnicianWorkload,
  isSystemAdminForbidden,
} from '../access/maintenanceWorkflow.js'

export default function Technicians() {
  const { user } = useAuth()
  const roles = user?.roles || []
  const canView = canViewTechnicianWorkload(roles)
  const isSysAdmin = isSystemAdminForbidden(roles)

  const [technicians, setTechnicians] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [searchTerm, setSearchTerm] = useState('')

  const fetchTechnicians = useCallback(async () => {
    if (!canView || isSysAdmin) return
    setLoading(true)
    setError('')
    try {
      const res = await api.get('/api/technicians')
      setTechnicians(Array.isArray(res.data) ? res.data : [])
    } catch (err) {
      console.error('Failed to load technician workloads', err)
      setError('Could not load technician workload data. Please try again.')
    } finally {
      setLoading(false)
    }
  }, [canView, isSysAdmin])

  useEffect(() => {
    if (canView && !isSysAdmin) {
      fetchTechnicians()
    }
  }, [fetchTechnicians, canView, isSysAdmin])

  if (!canView || isSysAdmin) {
    return (
      <div className="page-shell p-6">
        <div className="p-6 rounded-xl bg-amber-50 dark:bg-amber-950/40 border border-amber-200 dark:border-amber-800 text-amber-900 dark:text-amber-200">
          <h2 className="text-xl font-bold mb-2">Restricted Access</h2>
          <p>
            Technician workload management is available exclusively to Lab Managers and Institution Administrators.
          </p>
        </div>
      </div>
    )
  }

  const filtered = technicians.filter((t) => {
    if (!searchTerm.trim()) return true
    const term = searchTerm.toLowerCase()
    return (
      (t.fullName && t.fullName.toLowerCase().includes(term)) ||
      (t.email && t.email.toLowerCase().includes(term)) ||
      (t.departmentName && t.departmentName.toLowerCase().includes(term))
    )
  })

  const totalActiveTasks = technicians.reduce((acc, t) => acc + (t.activeTasks || 0), 0)
  const totalOverdueTasks = technicians.reduce((acc, t) => acc + (t.overdueTasks || 0), 0)
  const totalInProgressTasks = technicians.reduce((acc, t) => acc + (t.inProgressTasks || 0), 0)

  return (
    <div className="page-shell max-w-7xl mx-auto p-4 md:p-6 space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-2 border-b border-slate-200 dark:border-slate-800">
        <div>
          <div className="flex items-center gap-2">
            <span className="text-2xl" aria-hidden="true">👷</span>
            <h1 className="text-2xl font-bold text-slate-900 dark:text-slate-100">
              Technician Management & Workload
            </h1>
          </div>
          <p className="text-sm text-slate-500 dark:text-slate-400 mt-1">
            Monitor technician task distribution, overdue maintenance, and balance workload allocations.
          </p>
        </div>

        <div className="flex items-center gap-2">
          <button
            type="button"
            className="px-3.5 py-2 text-sm font-semibold rounded-lg bg-slate-100 dark:bg-slate-800 text-slate-700 dark:text-slate-300 hover:bg-slate-200 dark:hover:bg-slate-700 transition"
            onClick={fetchTechnicians}
            disabled={loading}
          >
            ↻ Refresh
          </button>
          <Link
            to="/maintenance?tab=work-orders"
            className="px-3.5 py-2 text-sm font-semibold rounded-lg bg-indigo-600 text-white hover:bg-indigo-700 transition"
          >
            Work Orders Queue →
          </Link>
        </div>
      </div>

      {error && (
        <div className="p-4 rounded-xl bg-rose-50 dark:bg-rose-950/40 text-rose-800 dark:text-rose-200 border border-rose-200 dark:border-rose-800 text-sm font-medium flex justify-between items-center" role="alert">
          <span>{error}</span>
          <button type="button" onClick={fetchTechnicians} className="underline font-semibold ml-4">Retry</button>
        </div>
      )}

      {/* Overview Metric Cards */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
        <div className="p-4 rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 shadow-sm">
          <span className="text-xs font-semibold text-slate-500 dark:text-slate-400 uppercase">Technicians</span>
          <p className="text-2xl font-black text-slate-900 dark:text-slate-100 mt-1">{technicians.length}</p>
        </div>

        <div className="p-4 rounded-xl border border-blue-200 dark:border-blue-900/60 bg-blue-50/40 dark:bg-blue-950/20 shadow-sm">
          <span className="text-xs font-semibold text-blue-700 dark:text-blue-300 uppercase">Active Work Orders</span>
          <p className="text-2xl font-black text-blue-800 dark:text-blue-200 mt-1">{totalActiveTasks}</p>
        </div>

        <div className="p-4 rounded-xl border border-amber-200 dark:border-amber-900/60 bg-amber-50/40 dark:bg-amber-950/20 shadow-sm">
          <span className="text-xs font-semibold text-amber-700 dark:text-amber-300 uppercase">In Progress</span>
          <p className="text-2xl font-black text-amber-800 dark:text-amber-200 mt-1">{totalInProgressTasks}</p>
        </div>

        <div className={`p-4 rounded-xl border shadow-sm ${
          totalOverdueTasks > 0
            ? 'border-rose-300 dark:border-rose-900 bg-rose-50/60 dark:bg-rose-950/30'
            : 'border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900'
        }`}>
          <span className={`text-xs font-semibold uppercase ${
            totalOverdueTasks > 0 ? 'text-rose-700 dark:text-rose-300' : 'text-slate-500'
          }`}>
            Overdue Work Orders
          </span>
          <p className={`text-2xl font-black mt-1 ${
            totalOverdueTasks > 0 ? 'text-rose-800 dark:text-rose-200' : 'text-slate-700 dark:text-slate-300'
          }`}>
            {totalOverdueTasks}
          </p>
        </div>
      </div>

      {/* Filter / Search Bar */}
      <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-xl p-3 shadow-sm flex items-center justify-between">
        <input
          type="text"
          className="text-sm p-2 w-full max-w-sm rounded-lg border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-800 text-slate-800 dark:text-slate-200 focus:ring-2 focus:ring-indigo-500"
          placeholder="Search technicians by name, email, department…"
          value={searchTerm}
          onChange={(e) => setSearchTerm(e.target.value)}
        />
        <span className="text-xs text-slate-500">
          Showing {filtered.length} of {technicians.length} technicians
        </span>
      </div>

      {/* Technician Workload Table */}
      <section className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-xl overflow-hidden shadow-sm">
        {loading ? (
          <div className="py-16 text-center text-slate-500 text-sm">Calculating technician workloads…</div>
        ) : filtered.length === 0 ? (
          <div className="py-16 text-center text-slate-500 text-sm">
            No technicians registered for this institution.
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead className="bg-slate-50 dark:bg-slate-800/60 text-slate-600 dark:text-slate-300 text-xs uppercase font-semibold border-b border-slate-200 dark:border-slate-800">
                <tr>
                  <th className="py-3 px-4 whitespace-nowrap">Technician</th>
                  <th className="py-3 px-4 text-center whitespace-nowrap">Active Tasks</th>
                  <th className="py-3 px-4 text-center whitespace-nowrap">In Progress</th>
                  <th className="py-3 px-4 text-center whitespace-nowrap">Overdue</th>
                  <th className="py-3 px-4 whitespace-nowrap">Next Scheduled Date</th>
                  <th className="py-3 px-4 text-center whitespace-nowrap">Total Tasks</th>
                  <th className="py-3 px-4 text-right whitespace-nowrap">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 dark:divide-slate-800">
                {filtered.map((tech) => (
                  <tr key={tech.id} className="hover:bg-slate-50/50 dark:hover:bg-slate-800/40 transition">
                    <td className="py-3.5 px-4">
                      <div className="font-semibold text-slate-900 dark:text-slate-100">
                        {tech.fullName}
                      </div>
                      <div className="text-xs text-slate-500 dark:text-slate-400">
                        {tech.email} {tech.departmentName ? `• ${tech.departmentName}` : ''}
                      </div>
                    </td>

                    <td className="py-3.5 px-4 text-center whitespace-nowrap">
                      <span className={`px-2.5 py-1 text-xs font-bold rounded-full ${
                        (tech.activeTasks || 0) > 0
                          ? 'bg-blue-100 text-blue-800 dark:bg-blue-950 dark:text-blue-300'
                          : 'bg-slate-100 text-slate-600 dark:bg-slate-800 dark:text-slate-400'
                      }`}>
                        {tech.activeTasks || 0}
                      </span>
                    </td>

                    <td className="py-3.5 px-4 text-center whitespace-nowrap">
                      <span className={`px-2.5 py-1 text-xs font-bold rounded-full ${
                        (tech.inProgressTasks || 0) > 0
                          ? 'bg-amber-100 text-amber-800 dark:bg-amber-950 dark:text-amber-300'
                          : 'bg-slate-100 text-slate-600 dark:bg-slate-800 dark:text-slate-400'
                      }`}>
                        {tech.inProgressTasks || 0}
                      </span>
                    </td>

                    <td className="py-3.5 px-4 text-center whitespace-nowrap">
                      <span className={`px-2.5 py-1 text-xs font-bold rounded-full ${
                        (tech.overdueTasks || 0) > 0
                          ? 'bg-rose-100 text-rose-800 dark:bg-rose-950 dark:text-rose-300 font-extrabold'
                          : 'bg-slate-100 text-slate-600 dark:bg-slate-800 dark:text-slate-400'
                      }`}>
                        {tech.overdueTasks || 0}
                      </span>
                    </td>

                    <td className="py-3.5 px-4 text-slate-700 dark:text-slate-300 text-xs font-medium whitespace-nowrap">
                      {tech.nextScheduledDate ? (
                        new Date(tech.nextScheduledDate).toLocaleDateString()
                      ) : (
                        <span className="text-slate-400 italic">None scheduled</span>
                      )}
                    </td>

                    <td className="py-3.5 px-4 text-center text-slate-600 dark:text-slate-400 font-semibold text-xs whitespace-nowrap">
                      {tech.totalTasks || 0}
                    </td>

                    <td className="py-3.5 px-4 text-right whitespace-nowrap">
                      <Link
                        to={`/maintenance?tab=work-orders&technician=${tech.id}`}
                        className="px-3 py-1 text-xs font-semibold rounded bg-slate-100 dark:bg-slate-800 hover:bg-slate-200 dark:hover:bg-slate-700 text-indigo-600 dark:text-indigo-400 transition"
                      >
                        View Queue →
                      </Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </div>
  )
}
