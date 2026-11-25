import { useEffect, useState } from 'react'
import { Link, NavLink, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { useTheme } from '../context/ThemeContext'
import { getNavigationItems } from '../access/roleAccess.js'
import {
  canViewCostOverview,
  canViewCharges,
  canViewBudgets,
  canManageInvoices,
} from '../access/billingWorkflow.js'

function SunIcon() {
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
      className="theme-icon-sun"
      aria-hidden="true"
    >
      <circle cx="12" cy="12" r="4" fill="currentColor" fillOpacity="0.2" />
      <path d="M12 2v2M12 20v2M4.93 4.93l1.41 1.41M17.66 17.66l1.41 1.41M2 12h2M20 12h2M6.34 17.66l-1.41 1.41M19.07 4.93l-1.41 1.41" />
    </svg>
  )
}

function MoonIcon() {
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
      className="theme-icon-moon"
      aria-hidden="true"
    >
      <path
        d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"
        fill="currentColor"
        fillOpacity="0.2"
      />
    </svg>
  )
}

const ROUTE_ICONS = {
  '/': '🏠',
  '/dashboard': '📊',
  '/sharing': '🤝',
  '/equipment': '🔬',
  '/bookings': '📅',
  '/booking-calendar': '📆',
  '/utilization': '📈',
  '/monitoring': '📡',
  '/maintenance': '🔧',
  '/profile': '👤',
  '/sharing-analytics': '📉',
  '/my-usage-cost': '💳',
  '/cost-overview': '📊',
  '/charges': '🧾',
  '/budgets': '💰',
  '/invoices': '📑',
  '/login': '🔑',
  '/register': '📝',
  '/register-institution': '🏛️',
}

export default function Navbar() {
  const { theme, toggleTheme } = useTheme()
  const { logout, isAuthenticated, user } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [mobileOpen, setMobileOpen] = useState(false)

  const isDark = theme === 'dark'

  const navLinkClass = ({ isActive }) =>
    `navbar-link${isActive ? ' navbar-link-active' : ''}`
  const roleNavigation = getNavigationItems(user?.roles)
    .filter((item) => !(Array.isArray(user?.roles) && user.roles.includes('SYSTEM_ADMIN') && item.to === '/maintenance'))

  // Automatically close mobile sidebar on location/route change
  useEffect(() => {
    setMobileOpen(false)
  }, [location.pathname])

  function handleLogout() {
    logout()
    navigate('/', { replace: true })
  }

  const closeDrawer = () => setMobileOpen(false)

  return (
    <>
      {/* Mobile top bar (visible on mobile viewports) */}
      <header className="mobile-topbar" aria-label="Mobile top bar">
        <button
          type="button"
          className="mobile-menu-btn"
          onClick={() => setMobileOpen((prev) => !prev)}
          aria-label="Toggle navigation menu"
          aria-expanded={mobileOpen}
        >
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
            <line x1="3" y1="12" x2="21" y2="12" />
            <line x1="3" y1="6" x2="21" y2="6" />
            <line x1="3" y1="18" x2="21" y2="18" />
          </svg>
        </button>

        <Link to="/" className="brand" onClick={closeDrawer}>
          <span className="brand-icon" aria-hidden="true">L</span>
          <span className="brand-name">
            Lab<span>Resource</span>
          </span>
        </Link>

        <button
          type="button"
          className="theme-toggle mobile-quick-toggle"
          onClick={toggleTheme}
          aria-label={`Switch to ${isDark ? 'light' : 'dark'} mode`}
          title={`Switch to ${isDark ? 'light' : 'dark'} mode`}
        >
          <span className="theme-toggle-icon" aria-hidden="true">
            {isDark ? <SunIcon /> : <MoonIcon />}
          </span>
        </button>
      </header>

      {/* Backdrop overlay for mobile drawer */}
      {mobileOpen && (
        <div
          className="sidebar-backdrop"
          onClick={closeDrawer}
          aria-hidden="true"
        />
      )}

      {/* Primary Vertical Left Sidebar */}
      <aside
        className={`app-sidebar ${mobileOpen ? 'sidebar-open' : ''}`}
        aria-label="Primary navigation sidebar"
      >
        <div className="sidebar-header">
          <Link to="/" className="brand" onClick={closeDrawer}>
            <span className="brand-icon" aria-hidden="true">L</span>
            <span className="brand-name">
              Lab<span>Resource</span>
            </span>
          </Link>

          <button
            type="button"
            className="sidebar-close-btn"
            onClick={closeDrawer}
            aria-label="Close navigation sidebar"
          >
            ✕
          </button>
        </div>

        <nav className="sidebar-nav" aria-label="Main navigation">
          <div className="sidebar-nav-section-title">Navigation</div>
          <div className="navbar-links">
            <NavLink to="/" end className={navLinkClass} onClick={closeDrawer}>
              <span className="sidebar-link-icon" aria-hidden="true">
                {ROUTE_ICONS['/']}
              </span>
              <span className="sidebar-link-text">Home</span>
            </NavLink>

            {!isAuthenticated ? (
              <>
                <NavLink to="/login" className={navLinkClass} onClick={closeDrawer}>
                  <span className="sidebar-link-icon" aria-hidden="true">
                    {ROUTE_ICONS['/login']}
                  </span>
                  <span className="sidebar-link-text">Login</span>
                </NavLink>
                <NavLink to="/register" className={navLinkClass} onClick={closeDrawer}>
                  <span className="sidebar-link-icon" aria-hidden="true">
                    {ROUTE_ICONS['/register']}
                  </span>
                  <span className="sidebar-link-text">Register</span>
                </NavLink>
                <NavLink to="/register-institution" className={navLinkClass} onClick={closeDrawer}>
                  <span className="sidebar-link-icon" aria-hidden="true">
                    {ROUTE_ICONS['/register-institution']}
                  </span>
                  <span className="sidebar-link-text">Register Institution</span>
                </NavLink>
              </>
            ) : (
              <>
                {roleNavigation.map((item) => (
                  <NavLink
                    to={item.to}
                    className={navLinkClass}
                    key={item.to}
                    onClick={closeDrawer}
                  >
                    <span className="sidebar-link-icon" aria-hidden="true">
                      {ROUTE_ICONS[item.to] || '📌'}
                    </span>
                    <span className="sidebar-link-text">{item.label}</span>
                  </NavLink>
                ))}

                <div className="sidebar-nav-section-title" style={{ marginTop: 14 }}>
                  Billing & Costs
                </div>
                <NavLink to="/my-usage-cost" className={navLinkClass} onClick={closeDrawer}>
                  <span className="sidebar-link-icon" aria-hidden="true">
                    {ROUTE_ICONS['/my-usage-cost']}
                  </span>
                  <span className="sidebar-link-text">My Usage Costs</span>
                </NavLink>

                {canViewCostOverview(user?.roles) && (
                  <NavLink to="/cost-overview" className={navLinkClass} onClick={closeDrawer}>
                    <span className="sidebar-link-icon" aria-hidden="true">
                      {ROUTE_ICONS['/cost-overview']}
                    </span>
                    <span className="sidebar-link-text">Cost Overview</span>
                  </NavLink>
                )}

                {canViewCharges(user?.roles) && (
                  <NavLink to="/charges" className={navLinkClass} onClick={closeDrawer}>
                    <span className="sidebar-link-icon" aria-hidden="true">
                      {ROUTE_ICONS['/charges']}
                    </span>
                    <span className="sidebar-link-text">Charges</span>
                  </NavLink>
                )}

                {canViewBudgets(user?.roles) && (
                  <NavLink to="/budgets" className={navLinkClass} onClick={closeDrawer}>
                    <span className="sidebar-link-icon" aria-hidden="true">
                      {ROUTE_ICONS['/budgets']}
                    </span>
                    <span className="sidebar-link-text">Budgets</span>
                  </NavLink>
                )}

                {canManageInvoices(user?.roles) && (
                  <NavLink to="/invoices" className={navLinkClass} onClick={closeDrawer}>
                    <span className="sidebar-link-icon" aria-hidden="true">
                      {ROUTE_ICONS['/invoices']}
                    </span>
                    <span className="sidebar-link-text">Invoices</span>
                  </NavLink>
                )}
              </>
            )}
          </div>
        </nav>

        <div className="sidebar-footer">
          {isAuthenticated && user && (
            <div className="sidebar-user-card">
              <span className="sidebar-user-avatar" aria-hidden="true">
                {(user.email?.[0] || 'U').toUpperCase()}
              </span>
              <div className="sidebar-user-info">
                <span className="sidebar-user-email" title={user.email}>{user.email}</span>
                <span className="sidebar-user-role">
                  {user.roles?.[0]?.replaceAll('_', ' ') || 'User'}
                </span>
              </div>
            </div>
          )}

          <div className="navbar-actions">
            <button
              type="button"
              className="theme-toggle"
              onClick={toggleTheme}
              aria-label={`Switch to ${isDark ? 'light' : 'dark'} mode`}
              title={`Switch to ${isDark ? 'light' : 'dark'} mode`}
            >
              <span className="theme-toggle-icon" aria-hidden="true">
                {isDark ? <SunIcon /> : <MoonIcon />}
              </span>
              <span className="theme-toggle-label">
                {isDark ? 'Light mode' : 'Dark mode'}
              </span>
            </button>

            {isAuthenticated && (
              <button
                type="button"
                className="navbar-logout"
                onClick={handleLogout}
              >
                Logout
              </button>
            )}
          </div>
        </div>
      </aside>
    </>
  )
}
