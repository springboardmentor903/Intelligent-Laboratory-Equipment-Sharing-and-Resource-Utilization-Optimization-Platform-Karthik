
import { Link } from 'react-router-dom'

const features = [
  {
    icon: '🔬',
    title: 'Equipment Management',
    description:
      'Organize laboratory equipment, track availability, and manage resources in one place.',
  },
  {
    icon: '📅',
    title: 'Smart Bookings',
    description:
      'Request equipment reservations and manage booking schedules with fewer conflicts.',
  },
  {
    icon: '📊',
    title: 'Utilization Analytics',
    description:
      'Understand equipment usage and identify opportunities to improve resource utilization.',
  },
  {
    icon: '🛠️',
    title: 'Maintenance Tracking',
    description:
      'Record equipment issues and follow maintenance activities to support laboratory operations.',
  },
]

export default function Home() {
  return (
    <div className="home-page">
      <section className="hero-section">
        <div className="hero-content">
          <span className="eyebrow">
            LABORATORY RESOURCE MANAGEMENT
          </span>

          <h1>
            Make every laboratory
            <span className="hero-highlight"> resource count.</span>
          </h1>

          <p className="hero-description">
            A centralized platform to manage laboratory equipment,
            coordinate bookings, monitor utilization, and organize
            maintenance activities.
          </p>

          <div className="hero-actions">
            <Link to="/register" className="button button-primary">
              Get started <span aria-hidden="true">→</span>
            </Link>

            <Link to="/login" className="button button-secondary">
              Sign in
            </Link>
          </div>

          <div className="hero-note">
            <span className="status-dot" />
            One platform for smarter laboratory operations
          </div>
        </div>

        <div className="hero-visual" aria-label="Laboratory resource overview">
          <div className="visual-orbit orbit-one" />
          <div className="visual-orbit orbit-two" />

          <div className="visual-center">
            <span aria-hidden="true">🔬</span>
            <strong>Lab Resources</strong>
            <small>Connected. Organized. Optimized.</small>
          </div>

          <div className="floating-card floating-card-top">
            <span className="floating-icon">📅</span>
            <div>
              <strong>Equipment booking</strong>
              <small>Scheduling made simple</small>
            </div>
          </div>

          <div className="floating-card floating-card-bottom">
            <span className="floating-icon">📈</span>
            <div>
              <strong>Resource utilization</strong>
              <small>Usage insights in one place</small>
            </div>
          </div>
        </div>
      </section>

      <section className="features-section">
        <div className="section-heading">
          <span className="eyebrow">PLATFORM CAPABILITIES</span>
          <h2>Everything your lab needs to stay organized</h2>
          <p>
            Essential tools to support equipment sharing and laboratory
            resource management.
          </p>
        </div>

        <div className="feature-grid">
          {features.map((feature) => (
            <article className="feature-card" key={feature.title}>
              <div className="feature-icon" aria-hidden="true">
                {feature.icon}
              </div>
              <h3>{feature.title}</h3>
              <p>{feature.description}</p>
            </article>
          ))}
        </div>
      </section>

      <section className="home-cta">
        <div>
          <h2>Ready to organize your laboratory resources?</h2>
          <p>
            Sign in to continue or create an account to get started.
          </p>
        </div>

        <Link to="/register" className="button button-light">
          Create an account <span aria-hidden="true">→</span>
        </Link>
      </section>
    </div>
  )
}
