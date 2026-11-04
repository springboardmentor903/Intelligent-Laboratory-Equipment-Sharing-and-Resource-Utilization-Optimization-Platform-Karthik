export function connectEquipmentSocket({
  url,
  token,
  WebSocketImpl = globalThis.WebSocket,
  setTimer = globalThis.setTimeout,
  clearTimer = globalThis.clearTimeout,
  onStateChange = () => {},
  onEquipmentUpdate = () => {},
  onReconnect = () => {},
}) {
  let socket = null
  let reconnectTimer = null
  let attempts = 0
  let disposed = false

  function scheduleReconnect() {
    if (disposed || reconnectTimer !== null) return
    attempts += 1
    const delay = Math.min(30000, 1000 * (2 ** Math.min(attempts - 1, 5)))
    reconnectTimer = setTimer(() => {
      reconnectTimer = null
      connect()
    }, delay)
  }

  function connect() {
    if (disposed || socket) return
    if (!token) {
      onStateChange('Authentication required')
      return
    }

    onStateChange(attempts ? 'Reconnecting' : 'Connecting')
    let nextSocket
    try {
      nextSocket = new WebSocketImpl(url, ['lab-equipment-v1', `bearer.${token}`])
    } catch {
      onStateChange('Disconnected')
      scheduleReconnect()
      return
    }

    socket = nextSocket
    nextSocket.onopen = () => {
      if (disposed) return
      const reconnected = attempts > 0
      attempts = 0
      onStateChange('Connected')
      if (reconnected) onReconnect()
    }
    nextSocket.onmessage = (event) => {
      try {
        const message = JSON.parse(event.data)
        if (message?.type === 'EQUIPMENT_STATUS_UPDATED'
            && message.equipmentId != null
            && message.equipment) {
          onEquipmentUpdate(message)
        }
      } catch {
        // Ignore malformed frames; the API snapshot remains authoritative.
      }
    }
    nextSocket.onerror = () => {
      if (!disposed) onStateChange('Connection error')
    }
    nextSocket.onclose = () => {
      if (socket === nextSocket) socket = null
      nextSocket.onopen = null
      nextSocket.onmessage = null
      nextSocket.onerror = null
      nextSocket.onclose = null
      if (!disposed) {
        onStateChange('Disconnected')
        scheduleReconnect()
      }
    }
  }

  connect()

  return {
    close() {
      if (disposed) return
      disposed = true
      if (reconnectTimer !== null) clearTimer(reconnectTimer)
      reconnectTimer = null
      if (socket) {
        const closingSocket = socket
        socket = null
        closingSocket.onopen = null
        closingSocket.onmessage = null
        closingSocket.onerror = null
        closingSocket.onclose = null
        closingSocket.close()
      }
    },
  }
}
