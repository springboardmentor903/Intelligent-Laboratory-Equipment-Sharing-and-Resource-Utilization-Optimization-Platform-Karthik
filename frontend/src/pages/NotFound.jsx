
import { Link } from 'react-router-dom'

export default function NotFound() {
  return (
    <div className="not-found-page">
      <section className="not-found-card">
        <div className="not-found-illustration" aria-hidden="true">
          404
        </div>

        <span className="eyebrow">PAGE NOT FOUND</span>

        <h1>Looks like you're lost.</h1>

        <p>
          The page you're looking for doesn't exist, may have moved,
          or the address might be incorrect.
        </p>

        <div className="not-found-actions">
          <Link to="/" className="button button-primary">
            ← Return Home
          </Link>

          <Link to="/dashboard" className="button button-secondary">
            Go to Dashboard
          </Link>
        </div>
      </section>
    </div>
  )
}
