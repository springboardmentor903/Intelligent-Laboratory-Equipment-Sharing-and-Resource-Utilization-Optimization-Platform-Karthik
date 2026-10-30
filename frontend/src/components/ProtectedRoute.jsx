
import { useEffect } from 'react'
import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { hasAnyRole, hasValidRoles, SUPPORTED_ROLES } from '../access/roleAccess.js'

export default function ProtectedRoute({ allowedRoles = SUPPORTED_ROLES }) {
  const { isAuthenticated, loading, user, logout } = useAuth()
  const location = useLocation()
  const roleValid = hasValidRoles(user?.roles)

  useEffect(() => {
    if (user && !roleValid) logout()
  }, [user, roleValid, logout])

  if (loading) {
    return (
      <div className="route-loading" role="status">
        Checking your session…
      </div>
    )
  }

  if (!isAuthenticated || !roleValid) {
    return (
      <Navigate
        to="/login"
        replace
        state={{ from: location }}
      />
    )
  }

  if (!hasAnyRole(user.roles, allowedRoles)) {
    return (
      <div className="auth-alert" role="alert">
        Access denied. Your role is not authorized to view this page.
      </div>
    )
  }

  return <Outlet />
}
