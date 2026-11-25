import { useState, useEffect, useCallback } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import api from '../api/axiosInstance'
import {
  NOTIFICATION_TYPES,
  getNotificationIcon,
  getNotificationRoute,
  formatNotificationTime,
  isCriticalAlert,
} from '../access/notificationWorkflow.js'

export default function Notifications() {
  const navigate = useNavigate()
  const [notifications, setNotifications] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [unreadOnly, setUnreadOnly] = useState(false)
  const [typeFilter, setTypeFilter] = useState('')
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(1)
  const [totalElements, setTotalElements] = useState(0)
  const [markingAll, setMarkingAll] = useState(false)
  const pageSize = 15

  const fetchNotifications = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      const params = {
        page,
        size: pageSize,
      }
      if (unreadOnly) {
        params.unread = true
      }
      if (typeFilter) {
        params.type = typeFilter
      }

      const res = await api.get('/api/notifications', { params })
      const data = res.data
      setNotifications(Array.isArray(data?.content) ? data.content : [])
      setTotalPages(data?.totalPages || 1)
      setTotalElements(data?.totalElements || 0)
    } catch (err) {
      console.error('Failed to load notifications', err)
      setError('Unable to load notifications. Please try again.')
    } finally {
      setLoading(false)
    }
  }, [page, unreadOnly, typeFilter])

  useEffect(() => {
    fetchNotifications()
  }, [fetchNotifications])

  const notifyChange = () => {
    window.dispatchEvent(new CustomEvent('lab-notifications-updated'))
  }

  const handleMarkAsRead = async (e, id) => {
    e.stopPropagation()
    try {
      await api.patch(`/api/notifications/${id}/read`)
      setNotifications((prev) =>
        prev.map((n) => (n.id === id ? { ...n, isRead: true, read: true } : n))
      )
      notifyChange()
    } catch (err) {
      console.error('Failed to mark notification as read', err)
    }
  }

  const handleMarkAllAsRead = async () => {
    if (markingAll) return
    setMarkingAll(true)
    try {
      await api.patch('/api/notifications/read-all')
      setNotifications((prev) =>
        prev.map((n) => ({ ...n, isRead: true, read: true }))
      )
      notifyChange()
    } catch (err) {
      console.error('Failed to mark all as read', err)
      setError('Failed to mark all as read.')
    } finally {
      setMarkingAll(false)
    }
  }

  const handleNotificationClick = async (item) => {
    const isUnread = !(item.isRead ?? item.read)
    if (isUnread) {
      try {
        await api.patch(`/api/notifications/${item.id}/read`)
        notifyChange()
      } catch (err) {
        console.error('Failed to mark as read on click', err)
      }
    }
    const targetRoute = getNotificationRoute(item)
    navigate(targetRoute)
  }

  const hasUnread = notifications.some((n) => !(n.isRead ?? n.read))

  return (
    <div className="page-container" style={{ maxWidth: 1000, margin: '0 auto', padding: '24px 16px' }}>
      <header className="page-header" style={{ marginBottom: 24 }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 16 }}>
          <div>
            <h1 style={{ fontSize: '1.75rem', fontWeight: 700, margin: 0 }}>Notification Center</h1>
            <p className="muted-text" style={{ marginTop: 4, margin: 0, color: 'var(--text-muted, #64748b)' }}>
              Real-time activity, critical safety alerts, and booking updates
            </p>
          </div>
          <div style={{ display: 'flex', gap: 10, alignItems: 'center', flexWrap: 'wrap' }}>
            <Link
              to="/notifications/preferences"
              className="btn btn-secondary"
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: 6,
                padding: '8px 14px',
                borderRadius: 6,
                border: '1px solid var(--border, #cbd5e1)',
                textDecoration: 'none',
                color: 'var(--text, #334155)',
                fontWeight: 500,
                fontSize: '0.875rem',
              }}
            >
              <span>⚙️</span> Preferences
            </Link>
            <button
              type="button"
              className="btn btn-primary"
              onClick={handleMarkAllAsRead}
              disabled={markingAll || !hasUnread}
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: 6,
                padding: '8px 16px',
                borderRadius: 6,
                fontWeight: 600,
                fontSize: '0.875rem',
                cursor: markingAll || !hasUnread ? 'not-allowed' : 'pointer',
                opacity: markingAll || !hasUnread ? 0.6 : 1,
              }}
            >
              ✓ Mark All Read
            </button>
          </div>
        </div>
      </header>

      {error && (
        <div className="alert alert-danger" style={{ marginBottom: 20, padding: 12, borderRadius: 6, background: '#fee2e2', color: '#991b1b' }}>
          {error}
        </div>
      )}

      {/* Filter Toolbar */}
      <div
        className="filter-card"
        style={{
          background: 'var(--surface, #ffffff)',
          border: '1px solid var(--border, #e2e8f0)',
          borderRadius: 8,
          padding: '14px 16px',
          marginBottom: 20,
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          flexWrap: 'wrap',
          gap: 12,
        }}
      >
        <div style={{ display: 'flex', gap: 10, alignItems: 'center', flexWrap: 'wrap' }}>
          <label style={{ display: 'flex', alignItems: 'center', gap: 6, cursor: 'pointer', fontSize: '0.9rem', fontWeight: 500 }}>
            <input
              type="checkbox"
              checked={unreadOnly}
              onChange={(e) => {
                setUnreadOnly(e.target.checked)
                setPage(0)
              }}
            />
            <span>Unread Only</span>
          </label>

          <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginLeft: 8 }}>
            <span style={{ fontSize: '0.85rem', color: 'var(--text-muted, #64748b)' }}>Type:</span>
            <select
              value={typeFilter}
              onChange={(e) => {
                setTypeFilter(e.target.value)
                setPage(0)
              }}
              style={{
                padding: '6px 10px',
                borderRadius: 6,
                border: '1px solid var(--border, #cbd5e1)',
                background: 'var(--surface, #ffffff)',
                color: 'var(--text, #1e293b)',
                fontSize: '0.875rem',
              }}
            >
              <option value="">All Event Types</option>
              {NOTIFICATION_TYPES.map((t) => (
                <option key={t} value={t}>
                  {t.replaceAll('_', ' ')}
                </option>
              ))}
            </select>
          </div>
        </div>

        <div style={{ fontSize: '0.85rem', color: 'var(--text-muted, #64748b)' }}>
          Showing {notifications.length} of {totalElements} notifications
        </div>
      </div>

      {/* Notifications List */}
      {loading ? (
        <div style={{ textAlign: 'center', padding: '40px 0', color: 'var(--text-muted, #64748b)' }}>
          Loading notifications…
        </div>
      ) : notifications.length === 0 ? (
        <div
          style={{
            textAlign: 'center',
            padding: '48px 16px',
            background: 'var(--surface, #ffffff)',
            border: '1px dashed var(--border, #cbd5e1)',
            borderRadius: 8,
          }}
        >
          <div style={{ fontSize: '2.5rem', marginBottom: 8 }}>📭</div>
          <h3 style={{ margin: '0 0 6px 0', fontSize: '1.1rem', fontWeight: 600 }}>No notifications found</h3>
          <p style={{ margin: 0, color: 'var(--text-muted, #64748b)', fontSize: '0.9rem' }}>
            {unreadOnly ? 'You have no unread notifications.' : 'You have no notifications matching your filters.'}
          </p>
        </div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
          {notifications.map((item) => {
            const isUnread = !(item.isRead ?? item.read)
            const critical = isCriticalAlert(item)

            return (
              <div
                key={item.id}
                onClick={() => handleNotificationClick(item)}
                style={{
                  display: 'flex',
                  alignItems: 'flex-start',
                  gap: 14,
                  padding: '14px 16px',
                  borderRadius: 8,
                  cursor: 'pointer',
                  border: critical
                    ? '1.5px solid #ef4444'
                    : isUnread
                    ? '1.5px solid var(--primary, #0284c7)'
                    : '1px solid var(--border, #e2e8f0)',
                  background: isUnread
                    ? 'color-mix(in srgb, var(--primary, #0284c7) 5%, var(--surface, #ffffff))'
                    : 'var(--surface, #ffffff)',
                  transition: 'transform 0.1s ease, box-shadow 0.1s ease',
                  position: 'relative',
                }}
              >
                {/* Unread indicator badge */}
                {isUnread && (
                  <span
                    style={{
                      position: 'absolute',
                      top: 12,
                      right: 12,
                      width: 8,
                      height: 8,
                      borderRadius: '50%',
                      background: critical ? '#ef4444' : 'var(--primary, #0284c7)',
                    }}
                    title="Unread"
                  />
                )}

                {/* Left Icon */}
                <div
                  style={{
                    fontSize: '1.5rem',
                    width: 36,
                    height: 36,
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    flexShrink: 0,
                    background: isUnread ? 'rgba(2, 132, 199, 0.1)' : 'var(--surface-secondary, #f1f5f9)',
                    borderRadius: 8,
                  }}
                >
                  {getNotificationIcon(item.notificationType)}
                </div>

                {/* Main Content */}
                <div style={{ flex: 1, minWidth: 0, paddingRight: isUnread ? 24 : 0 }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap', marginBottom: 4 }}>
                    <span
                      style={{
                        fontWeight: isUnread ? 700 : 600,
                        fontSize: '0.95rem',
                        color: 'var(--text, #1e293b)',
                      }}
                    >
                      {item.title}
                    </span>
                    {critical && (
                      <span
                        style={{
                          fontSize: '0.7rem',
                          fontWeight: 700,
                          textTransform: 'uppercase',
                          padding: '2px 6px',
                          borderRadius: 4,
                          background: '#fee2e2',
                          color: '#dc2626',
                          border: '1px solid #fca5a5',
                        }}
                      >
                        Urgent Alert
                      </span>
                    )}
                    <span
                      style={{
                        fontSize: '0.75rem',
                        color: 'var(--text-muted, #64748b)',
                        marginLeft: 'auto',
                      }}
                    >
                      {formatNotificationTime(item.createdAt)}
                    </span>
                  </div>

                  <p
                    style={{
                      margin: '0 0 6px 0',
                      fontSize: '0.875rem',
                      color: 'var(--text, #334155)',
                      lineHeight: 1.4,
                      wordBreak: 'break-word',
                    }}
                  >
                    {item.message}
                  </p>

                  <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginTop: 6 }}>
                    {item.referenceType && (
                      <span
                        style={{
                          fontSize: '0.75rem',
                          color: 'var(--text-muted, #64748b)',
                          background: 'var(--surface-secondary, #f1f5f9)',
                          padding: '2px 8px',
                          borderRadius: 4,
                        }}
                      >
                        {item.referenceType} {item.referenceId ? `#${item.referenceId}` : ''}
                      </span>
                    )}

                    {isUnread && (
                      <button
                        type="button"
                        onClick={(e) => handleMarkAsRead(e, item.id)}
                        style={{
                          fontSize: '0.75rem',
                          color: 'var(--primary, #0284c7)',
                          background: 'none',
                          border: 'none',
                          padding: 0,
                          cursor: 'pointer',
                          textDecoration: 'underline',
                          fontWeight: 600,
                        }}
                      >
                        Mark read
                      </button>
                    )}

                    <span
                      style={{
                        fontSize: '0.75rem',
                        color: 'var(--primary, #0284c7)',
                        marginLeft: 'auto',
                        fontWeight: 500,
                      }}
                    >
                      Go to details →
                    </span>
                  </div>
                </div>
              </div>
            )
          })}
        </div>
      )}

      {/* Pagination Controls */}
      {totalPages > 1 && (
        <div
          style={{
            display: 'flex',
            justifyContent: 'center',
            alignItems: 'center',
            gap: 12,
            marginTop: 24,
          }}
        >
          <button
            type="button"
            className="btn btn-secondary"
            disabled={page === 0}
            onClick={() => setPage((p) => Math.max(0, p - 1))}
            style={{ padding: '6px 14px', borderRadius: 6, fontSize: '0.85rem' }}
          >
            ← Previous
          </button>
          <span style={{ fontSize: '0.85rem', color: 'var(--text-muted, #64748b)' }}>
            Page {page + 1} of {totalPages}
          </span>
          <button
            type="button"
            className="btn btn-secondary"
            disabled={page >= totalPages - 1}
            onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
            style={{ padding: '6px 14px', borderRadius: 6, fontSize: '0.85rem' }}
          >
            Next →
          </button>
        </div>
      )}
    </div>
  )
}
