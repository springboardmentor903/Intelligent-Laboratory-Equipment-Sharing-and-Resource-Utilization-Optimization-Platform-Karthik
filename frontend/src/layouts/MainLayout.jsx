
import { Outlet } from 'react-router-dom'
import Navbar from '../components/Navbar'

export default function MainLayout() {
  return (
    <div className="app-layout">
      <Navbar />

      <main className="app-main">
        <Outlet />
      </main>

      <footer className="app-footer">
        <p>
          © {new Date().getFullYear()} Lab Resource Platform.
          <span> Smarter laboratory resource management.</span>
        </p>
      </footer>
    </div>
  )
}
