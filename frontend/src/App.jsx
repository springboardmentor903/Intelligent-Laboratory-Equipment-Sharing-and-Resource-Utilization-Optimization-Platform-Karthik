
import { lazy, Suspense } from 'react'
import { Routes, Route } from 'react-router-dom'
import MainLayout from './layouts/MainLayout'
import ProtectedRoute from './components/ProtectedRoute'
import Home from './pages/Home'
import Login from './pages/Login'
import Register from './pages/Register'
import RegisterInstitution from './pages/RegisterInstitution'
import ApplySystemAdmin from './pages/ApplySystemAdmin'
import Dashboard from './pages/Dashboard'
import Equipment from './pages/Equipment'
import Bookings from './pages/Bookings'
import Utilization from './pages/Utilization'
import Maintenance from './pages/Maintenance'
import NotFound from './pages/NotFound'
import PasswordReset from './pages/PasswordReset'
import Profile from './pages/Profile'
import OAuthCallback from './pages/OAuthCallback'
const BookingCalendar = lazy(() => import('./pages/BookingCalendar'))
import { SUPPORTED_ROLES } from './access/roleAccess.js'

export default function App() {
  return (
    <Routes>
      <Route element={<MainLayout />}>
        <Route path="/" element={<Home />} />
        <Route path="/login" element={<Login />} />
        <Route path="/register" element={<Register />} />
        <Route path="/register-institution" element={<RegisterInstitution />} />
        <Route path="/apply-system-admin" element={<ApplySystemAdmin />} />

        <Route element={<ProtectedRoute allowedRoles={SUPPORTED_ROLES} />}>
          <Route path="/dashboard" element={<Dashboard />} />
          <Route path="/equipment" element={<Equipment />} />
          <Route path="/bookings" element={<Bookings />} />
          <Route path="/booking-calendar" element={
            <Suspense fallback={<div className="module-state" role="status">Loading calendar…</div>}>
              <BookingCalendar />
            </Suspense>
          } />
          <Route path="/utilization" element={<Utilization />} />
          <Route path="/maintenance" element={<Maintenance />} />
          <Route path="/profile" element={<Profile />} />
        </Route>
        <Route path="/password-reset" element={<PasswordReset />} />
        <Route path="/oauth/callback" element={<OAuthCallback />} />
        <Route path="*" element={<NotFound />} />
      </Route>
    </Routes>
  )
}
