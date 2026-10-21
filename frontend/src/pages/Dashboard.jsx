
import { useEffect, useState } from 'react'
import api from '../api/axiosInstance'

export default function Dashboard() {
  const [status, setStatus] = useState('Checking backend...')

  useEffect(() => {
    api.get('/health')
      .then((response) => {
        setStatus(response.data.status === 'UP' ? 'UP' : 'Unexpected response')
      })
      .catch(() => {
        setStatus('Backend not reachable')
      })
  }, [])

  return (
    <section>
      <h1 className="text-3xl font-bold text-blue-700">Dashboard</h1>
      <p className="mt-4 text-gray-700">
        Backend status: <strong>{status}</strong>
      </p>
    </section>
  )
}
