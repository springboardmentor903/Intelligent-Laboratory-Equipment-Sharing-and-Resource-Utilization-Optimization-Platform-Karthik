import { Link, NavLink, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { useTheme } from '../context/ThemeContext'
import { getNavigationItems } from '../access/roleAccess.js'

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
