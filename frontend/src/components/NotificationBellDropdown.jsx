import { useRef, useEffect } from 'react'
import { Link } from 'react-router-dom'
import {
  formatUnreadCount,
  formatNotificationTime,
  getNotificationIcon,
  isCriticalAlert,
} from '../access/notificationWorkflow.js'

function BellIcon() {
  return (
    <svg
      width="18"
      height="18"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9" />
      <path d="M13.73 21a2 2 0 0 1-3.46 0" />
    </svg>
  )
}

export default function NotificationBellDropdown({
  unreadCount = 0,
  isOpen = false,
  onToggle = () => {},
  onClose = () => {},
  notifications = [],
  loading = false,
  onNotificationClick = () => {},
}) {
  const containerRef = useRef(null)
  const badgeText = formatUnreadCount(unreadCount)

  useEffect(() => {
    function handleClickOutside(event) {
      if (containerRef.current && !containerRef.current.contains(event.target)) {
        onClose()
      }
    }

    if (isOpen) {
      document.addEventListener('mousedown', handleClickOutside)
    }
    return () => {
      document.removeEventListener('mousedown', handleClickOutside)
    }
  }, [isOpen, onClose])

  return (
    <div ref={containerRef} style={{ position: 'relative', display: 'inline-block' }}>
      <button
        type="button"
        onClick={onToggle}
        aria-label={`Notifications${unreadCount > 0 ? `, ${unreadCount} unread` : ''}`}
        aria-expanded={isOpen}
        title={`Notifications${unreadCount > 0 ? ` (${unreadCount} unread)` : ''}`}
        style={{
          position: 'relative',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          width: 36,
          height: 36,
          borderRadius: 8,
          border: '1px solid var(--border, #cbd5e1)',
          background: isOpen ? 'var(--surface-secondary, #f1f5f9)' : 'var(--surface, #ffffff)',
          color: unreadCount > 0 ? 'var(--primary, #0284c7)' : 'var(--text-muted, #64748b)',
          cursor: 'pointer',
          padding: 0,
          transition: 'all 0.15s ease',
        }}
      >
        <BellIcon />
        {badgeText && (
          <span
            style={{
              position: 'absolute',
              top: -5,
              right: -5,
              background: '#ef4444',
              color: '#ffffff',
              fontSize: '0.65rem',
              fontWeight: 700,
              minWidth: 16,
              height: 16,
              borderRadius: 10,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              padding: '0 4px',
              lineHeight: 1,
              boxShadow: '0 0 0 2px var(--surface, #ffffff)',
            }}
          >
            {badgeText}
          </span>
        )}
      </button>

      {isOpen && (
        <div
          style={{
            position: 'absolute',
            top: 'calc(100% + 8px)',
            right: 0,
            width: 320,
            maxWidth: '90vw',
            background: 'var(--surface, #ffffff)',
            border: '1px solid var(--border, #e2e8f0)',
            borderRadius: 10,
            boxShadow: '0 10px 25px rgba(0, 0, 0, 0.12)',
            zIndex: 300,
            display: 'flex',
            flexDirection: 'column',
            overflow: 'hidden',
          }}
        >
          {/* Header */}
          <div
            style={{
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              padding: '12px 14px',
              borderBottom: '1px solid var(--border, #f1f5f9)',
              background: 'var(--surface, #ffffff)',
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
              <span style={{ fontWeight: 700, fontSize: '0.9rem', color: 'var(--text, #1e293b)' }}>
                Notifications
              </span>
              {badgeText && (
                <span
                  style={{
                    background: '#fee2e2',
                    color: '#dc2626',
                    fontSize: '0.7rem',
                    fontWeight: 700,
                    borderRadius: 10,
                    padding: '1px 6px',
                  }}
                >
                  {badgeText} unread
                </span>
              )}
            </div>
            <Link
              to="/notifications/preferences"
              onClick={onClose}
              style={{
                fontSize: '0.75rem',
                color: 'var(--text-muted, #64748b)',
                textDecoration: 'none',
              }}
              title="Notification preferences"
            >
              ⚙️ Settings
            </Link>
          </div>

          {/* Body */}
          <div style={{ maxHeight: 320, overflowY: 'auto' }}>
            {loading ? (
              <div style={{ padding: 20, textAlign: 'center', color: 'var(--text-muted, #64748b)', fontSize: '0.85rem' }}>
                Loading notifications…
              </div>
            ) : notifications.length === 0 ? (
              <div style={{ padding: 24, textAlign: 'center', color: 'var(--text-muted, #64748b)', fontSize: '0.85rem' }}>
                No notifications right now
              </div>
            ) : (
              notifications.map((item) => {
                const isUnread = !(item.isRead ?? item.read)
                const critical = isCriticalAlert(item)

                return (
                  <div
                    key={item.id}
                    onClick={() => onNotificationClick(item)}
                    style={{
                      display: 'flex',
                      alignItems: 'flex-start',
                      gap: 10,
                      padding: '10px 12px',
                      borderBottom: '1px solid var(--border, #f8fafc)',
                      cursor: 'pointer',
                      background: isUnread
                        ? 'color-mix(in srgb, var(--primary, #0284c7) 6%, var(--surface, #ffffff))'
                        : 'var(--surface, #ffffff)',
                      transition: 'background 0.1s ease',
                    }}
                  >
                    <span style={{ fontSize: '1.2rem', lineHeight: 1, flexShrink: 0 }}>
                      {getNotificationIcon(item.notificationType)}
                    </span>
                    <div style={{ flex: 1, minWidth: 0 }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: 4, justifyContent: 'space-between' }}>
                        <span
                          style={{
                            fontWeight: isUnread ? 700 : 500,
                            fontSize: '0.825rem',
                            color: 'var(--text, #1e293b)',
                            overflow: 'hidden',
                            textOverflow: 'ellipsis',
                            whiteSpace: 'nowrap',
                          }}
                        >
                          {item.title}
                        </span>
                        <span style={{ fontSize: '0.7rem', color: 'var(--text-muted, #94a3b8)', flexShrink: 0 }}>
                          {formatNotificationTime(item.createdAt)}
                        </span>
                      </div>
                      <p
                        style={{
                          margin: '2px 0 0 0',
                          fontSize: '0.75rem',
                          color: 'var(--text-muted, #64748b)',
                          overflow: 'hidden',
                          textOverflow: 'ellipsis',
                          whiteSpace: 'nowrap',
                        }}
                      >
                        {item.message}
                      </p>
                      {critical && (
                        <span
                          style={{
                            display: 'inline-block',
                            marginTop: 2,
                            fontSize: '0.65rem',
                            fontWeight: 700,
                            color: '#dc2626',
                            textTransform: 'uppercase',
                          }}
                        >
                          Urgent
                        </span>
                      )}
                    </div>
                    {isUnread && (
                      <span
                        style={{
                          width: 6,
                          height: 6,
                          borderRadius: '50%',
                          background: critical ? '#ef4444' : 'var(--primary, #0284c7)',
                          flexShrink: 0,
                          marginTop: 4,
                        }}
                      />
                    )}
                  </div>
                )
              })
            )}
          </div>

          {/* Footer */}
          <div
            style={{
              padding: '10px 14px',
              borderTop: '1px solid var(--border, #f1f5f9)',
              background: 'var(--surface, #ffffff)',
              textAlign: 'center',
            }}
          >
            <Link
              to="/notifications"
              onClick={onClose}
              style={{
                fontSize: '0.8rem',
                fontWeight: 600,
                color: 'var(--primary, #0284c7)',
                textDecoration: 'none',
              }}
            >
              View All Notifications →
            </Link>
          </div>
        </div>
      )}
    </div>
  )
}
