
import { Link } from 'react-router-dom'

export default function Navbar() {
  return (
    <nav className="bg-blue-700 px-6 py-4 text-white">
      <div className="mx-auto flex max-w-6xl items-center justify-between">
        <Link to="/" className="text-xl font-bold">
          Lab Resource Platform
        </Link>

        <div className="flex gap-5">
          <Link to="/">Home</Link>
          <Link to="/login">Login</Link>
          <Link to="/register">Register</Link>
          <Link to="/dashboard">Dashboard</Link>
        </div>
      </div>
    </nav>
  )
}
