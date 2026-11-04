import axios from 'axios'
import { handleApiError } from './responseError.js'

export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'
export const WEBSOCKET_URL = import.meta.env.VITE_WS_BASE_URL || `${API_BASE_URL.replace(/^http/, 'ws')}/ws/equipment`

const api = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
  timeout: 15000,
})

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')

  if (token) {
    if (config.headers?.set) {
      config.headers.set('Authorization', `Bearer ${token}`)
    } else {
      config.headers.Authorization = `Bearer ${token}`
    }
  }

  return config
})

api.interceptors.response.use(
  (response) => response,
  (error) => Promise.reject(handleApiError(error)),
)

export default api
