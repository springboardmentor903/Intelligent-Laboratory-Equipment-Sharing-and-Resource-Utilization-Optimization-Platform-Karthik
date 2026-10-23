
import { Routes, Route } from 'react-router-dom'
import MainLayout from './layouts/MainLayout'
import ProtectedRoute from './components/ProtectedRoute'
import Home from './pages/Home'
import Login from './pages/Login'
import Register from './pages/Register'
import Dashboard from './pages/Dashboard'
import Equipment from './pages/Equipment'
import Bookings from './pages/Bookings'
import Utilization from './pages/Utilization'
import Maintenance from './pages/Maintenance'
import NotFound from './pages/NotFound'
import PasswordReset from './pages/PasswordReset'

export default function App() {
  return (
    <Routes>
      <Route element={<MainLayout />}>
        <Route path="/" element={<Home />} />
        <Route path="/login" element={<Login />} />
        <Route path="/register" element={<Register />} />

        <Route element={<ProtectedRoute />}>
          <Route path="/dashboard" element={<Dashboard />} />
          <Route path="/equipment" element={<Equipment />} />
          <Route path="/bookings" element={<Bookings />} />
          <Route path="/utilization" element={<Utilization />} />
          <Route path="/maintenance" element={<Maintenance />} />
        </Route>
        <Route path="/password-reset" element={<PasswordReset />} />
        <Route path="*" element={<NotFound />} />
      </Route>
    </Routes>
  )
}
