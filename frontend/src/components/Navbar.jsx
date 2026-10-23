
import { Link, NavLink, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { useTheme } from '../context/ThemeContext'

export default function Navbar() {
  const { theme, toggleTheme } = useTheme()
  const { logout, isAuthenticated } = useAuth()
  const navigate = useNavigate()

  const isDark = theme === 'dark'

  const navLinkClass = ({ isActive }) =>
    `navbar-link${isActive ? ' navbar-link-active' : ''}`

  function handleLogout() {
    logout()
    navigate('/', { replace: true })
  }

  return (
    <header className="app-header">
      <nav className="navbar-container" aria-label="Main navigation">
        <Link to="/" className="brand">
          <span className="brand-icon" aria-hidden="true">L</span>
          <span className="brand-name">
            Lab<span>Resource</span>
          </span>
        </Link>

        <div className="navbar-links">
          <NavLink to="/" end className={navLinkClass}>
            Home
          </NavLink>

          {!isAuthenticated ? (
            <>
              <NavLink to="/login" className={navLinkClass}>
                Login
              </NavLink>
              <NavLink to="/register" className={navLinkClass}>
                Register
              </NavLink>
            </>
          ) : (
            <>
              <NavLink to="/dashboard" className={navLinkClass}>
                Dashboard
              </NavLink>
              <NavLink to="/equipment" className={navLinkClass}>
                Equipment
              </NavLink>
              <NavLink to="/bookings" className={navLinkClass}>
                Bookings
              </NavLink>
              <NavLink to="/utilization" className={navLinkClass}>
                Utilization
              </NavLink>
              <NavLink to="/maintenance" className={navLinkClass}>
                Maintenance
              </NavLink>
            </>
          )}
        </div>

        <div className="navbar-actions">
          <button
            type="button"
            className="theme-toggle"
            onClick={toggleTheme}
            aria-label={`Switch to ${isDark ? 'light' : 'dark'} mode`}
            title={`Switch to ${isDark ? 'light' : 'dark'} mode`}
          >
            <span aria-hidden="true">
              {isDark ? '☀️' : '🌙'}
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
      </nav>
    </header>
  )
}
