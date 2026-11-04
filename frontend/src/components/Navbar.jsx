import { Link, NavLink, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { useTheme } from '../context/ThemeContext'
import { getNavigationItems } from '../access/roleAccess.js'

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

export default function Navbar() {
  const { theme, toggleTheme } = useTheme()
  const { logout, isAuthenticated, user } = useAuth()
  const navigate = useNavigate()

  const isDark = theme === 'dark'

  const navLinkClass = ({ isActive }) =>
    `navbar-link${isActive ? ' navbar-link-active' : ''}`
  const roleNavigation = getNavigationItems(user?.roles)

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
              <NavLink to="/register-institution" className={navLinkClass}>
                Register Institution
              </NavLink>
            </>
          ) : (
            <>
              {roleNavigation.map((item) => (
                <NavLink
                  to={item.to}
                  className={navLinkClass}
                  key={item.to}
                >
                  {item.label}
                </NavLink>
              ))}
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
      </nav>
    </header>
  )
}
