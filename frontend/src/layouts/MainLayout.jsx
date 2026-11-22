import { Outlet } from 'react-router-dom'
import Navbar from '../components/Navbar'

const currentYear = new Date().getFullYear()

export default function MainLayout() {
  return (
    <div className="app-layout">
      <Navbar />

      <div className="app-content-wrapper">
        <main className="app-main">
          <Outlet />
        </main>

        <footer className="app-footer">
          <p>
            © {currentYear} Lab Resource Platform.
            <span> Smarter laboratory resource management.</span>
          </p>
        </footer>
      </div>
    </div>
  )
}
