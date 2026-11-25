import { useState, useEffect } from 'react'
import { Link } from 'react-router-dom'
import api from '../api/axiosInstance'

export default function NotificationPreferences() {
  const [preferences, setPreferences] = useState({
    emailEnabled: true,
    smsEnabled: false,
    pushEnabled: false,
    inAppEnabled: true,
    hasPhoneNumber: false,
  })
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [registeringDevice, setRegisteringDevice] = useState(false)
  const [successMessage, setSuccessMessage] = useState('')
  const [errorMessage, setErrorMessage] = useState('')
  const [pushStatus, setPushStatus] = useState('')

  useEffect(() => {
    async function loadPreferences() {
      setLoading(true)
      setErrorMessage('')
      try {
        const res = await api.get('/api/notifications/preferences')
        if (res.data) {
          setPreferences({
            emailEnabled: res.data.emailEnabled ?? true,
            smsEnabled: res.data.smsEnabled ?? false,
            pushEnabled: res.data.pushEnabled ?? false,
            inAppEnabled: true,
            hasPhoneNumber: Boolean(res.data.hasPhoneNumber),
          })
        }
      } catch (err) {
        console.error('Failed to load notification preferences', err)
        setErrorMessage('Unable to load preferences. Please try again.')
      } finally {
        setLoading(false)
      }
    }

    loadPreferences()

    if (typeof window !== 'undefined' && 'Notification' in window) {
      setPushStatus(Notification.permission)
    }
  }, [])

  const handleToggle = (key) => {
    if (key === 'smsEnabled' && !preferences.hasPhoneNumber) {
      return
    }
    setPreferences((prev) => ({
      ...prev,
      [key]: !prev[key],
    }))
    setSuccessMessage('')
  }

  const handleRegisterDevice = async () => {
    if (typeof window === 'undefined' || !('Notification' in window)) {
      setErrorMessage('Push notifications are not supported in this browser.')
      return
    }

    setRegisteringDevice(true)
    setErrorMessage('')
    setSuccessMessage('')

    try {
      const permission = await Notification.requestPermission()
      setPushStatus(permission)

      if (permission === 'granted') {
        let storedToken = localStorage.getItem('push_device_token')
        if (!storedToken) {
          storedToken = `web_${Date.now()}_${Math.random().toString(36).substring(2, 10)}`
          localStorage.setItem('push_device_token', storedToken)
        }

        await api.post('/api/notifications/devices', {
          token: storedToken,
          deviceType: 'WEB',
          userAgent: navigator.userAgent,
        })

        setPreferences((prev) => ({ ...prev, pushEnabled: true }))
        setSuccessMessage('This device was successfully registered for Web Push notifications.')
      } else if (permission === 'denied') {
        setErrorMessage('Push permission was denied in your browser settings. Please enable notifications for this site.')
      } else {
        setErrorMessage('Push notification permission was dismissed.')
      }
    } catch (err) {
      console.error('Failed to register device for push notifications', err)
      setErrorMessage('Failed to register device for push notifications.')
    } finally {
      setRegisteringDevice(false)
    }
  }

  const handleSave = async (e) => {
    e.preventDefault()
    setSaving(true)
    setSuccessMessage('')
    setErrorMessage('')

    try {
      const payload = {
        emailEnabled: preferences.emailEnabled,
        smsEnabled: preferences.hasPhoneNumber ? preferences.smsEnabled : false,
        pushEnabled: preferences.pushEnabled,
      }

      const res = await api.put('/api/notifications/preferences', payload)
      if (res.data) {
        setPreferences((prev) => ({
          ...prev,
          emailEnabled: res.data.emailEnabled,
          smsEnabled: res.data.smsEnabled,
          pushEnabled: res.data.pushEnabled,
          hasPhoneNumber: Boolean(res.data.hasPhoneNumber),
        }))
      }
      setSuccessMessage('Notification delivery preferences updated successfully.')
    } catch (err) {
      console.error('Failed to update preferences', err)
      const backendMsg = err.response?.data?.message || err.message
      setErrorMessage(backendMsg || 'Failed to update preferences.')
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="page-container" style={{ maxWidth: 800, margin: '0 auto', padding: '24px 16px' }}>
      <header className="page-header" style={{ marginBottom: 24 }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 12 }}>
          <div>
            <div style={{ marginBottom: 6 }}>
              <Link
                to="/notifications"
                style={{
                  color: 'var(--primary, #0284c7)',
                  textDecoration: 'none',
                  fontSize: '0.875rem',
                  fontWeight: 500,
                }}
              >
                ← Back to Notification Center
              </Link>
            </div>
            <h1 style={{ fontSize: '1.75rem', fontWeight: 700, margin: 0 }}>Notification Preferences</h1>
            <p className="muted-text" style={{ marginTop: 4, margin: 0, color: 'var(--text-muted, #64748b)' }}>
              Configure how and where you receive notifications across channels
            </p>
          </div>
        </div>
      </header>

      {successMessage && (
        <div className="alert alert-success" style={{ marginBottom: 20, padding: 12, borderRadius: 6, background: '#dcfce7', color: '#166534', border: '1px solid #86efac' }}>
          ✓ {successMessage}
        </div>
      )}

      {errorMessage && (
        <div className="alert alert-danger" style={{ marginBottom: 20, padding: 12, borderRadius: 6, background: '#fee2e2', color: '#991b1b', border: '1px solid #fca5a5' }}>
          {errorMessage}
        </div>
      )}

      {loading ? (
        <div style={{ textAlign: 'center', padding: '40px 0', color: 'var(--text-muted, #64748b)' }}>
          Loading your preferences…
        </div>
      ) : (
        <form onSubmit={handleSave}>
          <div
            className="preferences-card"
            style={{
              background: 'var(--surface, #ffffff)',
              border: '1px solid var(--border, #e2e8f0)',
              borderRadius: 8,
              padding: '24px',
              display: 'flex',
              flexDirection: 'column',
              gap: 20,
              boxShadow: '0 1px 3px rgba(0,0,0,0.04)',
            }}
          >
            {/* In-App Notifications (Mandatory) */}
            <div
              style={{
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
                paddingBottom: 16,
                borderBottom: '1px solid var(--border, #f1f5f9)',
              }}
            >
              <div>
                <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                  <span style={{ fontSize: '1.25rem' }}>🔔</span>
                  <span style={{ fontWeight: 600, fontSize: '1rem', color: 'var(--text, #1e293b)' }}>
                    In-App Notifications
                  </span>
                  <span
                    style={{
                      fontSize: '0.7rem',
                      fontWeight: 700,
                      background: '#e0f2fe',
                      color: '#0369a1',
                      padding: '2px 8px',
                      borderRadius: 12,
                      textTransform: 'uppercase',
                    }}
                  >
                    Always Active
                  </span>
                </div>
                <p style={{ margin: '4px 0 0 32px', fontSize: '0.85rem', color: 'var(--text-muted, #64748b)' }}>
                  Activity alerts, approvals, and reminders are always visible in your notification center.
                </p>
              </div>
              <input
                type="checkbox"
                checked={true}
                disabled={true}
                style={{ width: 18, height: 18, cursor: 'not-allowed' }}
              />
            </div>

            {/* Email Notifications */}
            <div
              style={{
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
                paddingBottom: 16,
                borderBottom: '1px solid var(--border, #f1f5f9)',
              }}
            >
              <div>
                <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                  <span style={{ fontSize: '1.25rem' }}>✉️</span>
                  <span style={{ fontWeight: 600, fontSize: '1rem', color: 'var(--text, #1e293b)' }}>
                    Email Notifications
                  </span>
                </div>
                <p style={{ margin: '4px 0 0 32px', fontSize: '0.85rem', color: 'var(--text-muted, #64748b)' }}>
                  Receive booking confirmations, maintenance alerts, and weekly reminders directly to your account email.
                </p>
              </div>
              <label style={{ cursor: 'pointer', display: 'flex', alignItems: 'center' }}>
                <input
                  type="checkbox"
                  checked={preferences.emailEnabled}
                  onChange={() => handleToggle('emailEnabled')}
                  style={{ width: 18, height: 18, cursor: 'pointer' }}
                />
              </label>
            </div>

            {/* SMS Notifications */}
            <div
              style={{
                display: 'flex',
                flexDirection: 'column',
                gap: 10,
                paddingBottom: 16,
                borderBottom: '1px solid var(--border, #f1f5f9)',
              }}
            >
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <div>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                    <span style={{ fontSize: '1.25rem' }}>📱</span>
                    <span style={{ fontWeight: 600, fontSize: '1rem', color: 'var(--text, #1e293b)' }}>
                      SMS Text Alerts
                    </span>
                  </div>
                  <p style={{ margin: '4px 0 0 32px', fontSize: '0.85rem', color: 'var(--text-muted, #64748b)' }}>
                    Receive critical safety alerts and urgent work order notifications via SMS text message.
                  </p>
                </div>
                <label
                  style={{
                    cursor: preferences.hasPhoneNumber ? 'pointer' : 'not-allowed',
                    display: 'flex',
                    alignItems: 'center',
                  }}
                >
                  <input
                    type="checkbox"
                    checked={preferences.smsEnabled && preferences.hasPhoneNumber}
                    disabled={!preferences.hasPhoneNumber}
                    onChange={() => handleToggle('smsEnabled')}
                    style={{
                      width: 18,
                      height: 18,
                      cursor: preferences.hasPhoneNumber ? 'pointer' : 'not-allowed',
                    }}
                  />
                </label>
              </div>

              {!preferences.hasPhoneNumber && (
                <div
                  style={{
                    marginLeft: 32,
                    padding: '10px 14px',
                    borderRadius: 6,
                    background: '#fef3c7',
                    border: '1px solid #fde68a',
                    color: '#92400e',
                    fontSize: '0.85rem',
                    display: 'flex',
                    alignItems: 'center',
                    gap: 8,
                  }}
                >
                  <span>⚠️</span>
                  <span>
                    A verified mobile phone number is required to enable SMS notifications.{' '}
                    <Link
                      to="/profile"
                      style={{
                        color: '#b45309',
                        fontWeight: 600,
                        textDecoration: 'underline',
                      }}
                    >
                      Add a phone number in your Profile →
                    </Link>
                  </span>
                </div>
              )}
            </div>

            {/* Web Push Notifications */}
            <div
              style={{
                display: 'flex',
                flexDirection: 'column',
                gap: 12,
              }}
            >
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <div>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                    <span style={{ fontSize: '1.25rem' }}>🌐</span>
                    <span style={{ fontWeight: 600, fontSize: '1rem', color: 'var(--text, #1e293b)' }}>
                      Web Push Notifications
                    </span>
                  </div>
                  <p style={{ margin: '4px 0 0 32px', fontSize: '0.85rem', color: 'var(--text-muted, #64748b)' }}>
                    Instant desktop and mobile browser notifications even when the tab is running in the background.
                  </p>
                </div>
                <label style={{ cursor: 'pointer', display: 'flex', alignItems: 'center' }}>
                  <input
                    type="checkbox"
                    checked={preferences.pushEnabled}
                    onChange={() => handleToggle('pushEnabled')}
                    style={{ width: 18, height: 18, cursor: 'pointer' }}
                  />
                </label>
              </div>

              <div style={{ marginLeft: 32, display: 'flex', alignItems: 'center', gap: 12, flexWrap: 'wrap' }}>
                <button
                  type="button"
                  className="btn btn-secondary"
                  onClick={handleRegisterDevice}
                  disabled={registeringDevice}
                  style={{
                    fontSize: '0.8rem',
                    padding: '6px 12px',
                    borderRadius: 6,
                    border: '1px solid var(--border, #cbd5e1)',
                    cursor: registeringDevice ? 'wait' : 'pointer',
                  }}
                >
                  {registeringDevice ? 'Registering…' : '🔔 Register This Browser / Device'}
                </button>
                {pushStatus && (
                  <span style={{ fontSize: '0.8rem', color: 'var(--text-muted, #64748b)' }}>
                    Browser permission: <strong>{pushStatus}</strong>
                  </span>
                )}
              </div>
            </div>

            {/* Action Bar */}
            <div style={{ marginTop: 12, paddingTop: 16, borderTop: '1px solid var(--border, #f1f5f9)', display: 'flex', justifyContent: 'flex-end', gap: 12 }}>
              <Link
                to="/notifications"
                className="btn btn-secondary"
                style={{
                  padding: '8px 16px',
                  borderRadius: 6,
                  border: '1px solid var(--border, #cbd5e1)',
                  textDecoration: 'none',
                  color: 'var(--text, #334155)',
                  fontSize: '0.875rem',
                }}
              >
                Cancel
              </Link>
              <button
                type="submit"
                className="btn btn-primary"
                disabled={saving}
                style={{
                  padding: '8px 20px',
                  borderRadius: 6,
                  fontWeight: 600,
                  fontSize: '0.875rem',
                  cursor: saving ? 'wait' : 'pointer',
                }}
              >
                {saving ? 'Saving…' : 'Save Preferences'}
              </button>
            </div>
          </div>
        </form>
      )}
    </div>
  )
}
