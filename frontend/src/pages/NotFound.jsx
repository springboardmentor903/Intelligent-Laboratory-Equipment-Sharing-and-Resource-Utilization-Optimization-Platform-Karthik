import { Link } from 'react-router-dom'

export default function NotFound() {
  return (
    <section>
      <h1 className="text-3xl font-bold">404 - Page Not Found</h1>
      <p className="mt-3 text-gray-700">
        The page you requested does not exist.
      </p>
      <Link to="/" className="mt-4 inline-block text-blue-700 underline">
        Return Home
      </Link>
    </section>
  )
}