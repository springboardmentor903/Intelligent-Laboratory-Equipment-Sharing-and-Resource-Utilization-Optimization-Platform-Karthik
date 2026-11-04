import test from 'node:test'
import assert from 'node:assert/strict'
import { connectEquipmentSocket } from '../src/access/equipmentSocket.js'

class MockWebSocket {
  static instances = []

  constructor(url, protocols) {
    this.url = url
    this.protocols = protocols
    this.closed = false
    MockWebSocket.instances.push(this)
  }

  open() { this.onopen?.() }
  message(data) { this.onmessage?.({ data }) }
  fail() { this.closed = true; this.onerror?.(); this.onclose?.() }
  close() { this.closed = true }
}

function fakeTimers() {
  let nextId = 0
  const callbacks = new Map()
  const delays = new Map()
  return {
    callbacks,
    delays,
    setTimer(callback, delay) {
      const id = ++nextId
      callbacks.set(id, callback)
      delays.set(id, delay)
      return id
    },
    clearTimer(id) { callbacks.delete(id) },
    run(id) {
      const callback = callbacks.get(id)
      callbacks.delete(id)
      callback?.()
    },
  }
}

test('opens one authenticated socket using the backend subprotocol contract', () => {
  MockWebSocket.instances = []
  const states = []
  const updates = []
  const client = connectEquipmentSocket({
    url: 'ws://localhost:8080/ws/equipment', token: 'test-token',
    WebSocketImpl: MockWebSocket,
    onStateChange: (state) => states.push(state),
    onEquipmentUpdate: (message) => updates.push(message),
  })

  assert.equal(MockWebSocket.instances.length, 1)
  const socket = MockWebSocket.instances[0]
  assert.equal(socket.url, 'ws://localhost:8080/ws/equipment')
  assert.deepEqual(socket.protocols, ['lab-equipment-v1', 'bearer.test-token'])
  socket.open()
  assert.deepEqual(states, ['Connecting', 'Connected'])

  const message = { type: 'EQUIPMENT_STATUS_UPDATED', equipmentId: 4, equipment: { id: 4 } }
  socket.message(JSON.stringify(message))
  socket.message('{malformed')
  socket.message(JSON.stringify({ type: 'OTHER' }))
  assert.deepEqual(updates, [message])

  client.close()
  assert.equal(socket.closed, true)
  assert.equal(socket.onmessage, null)
})

test('connection loss reconnects once and refreshes the complete snapshot after reconnect', () => {
  MockWebSocket.instances = []
  const timers = fakeTimers()
  const states = []
  let refreshes = 0
  const client = connectEquipmentSocket({
    url: 'ws://localhost:8080/ws/equipment', token: 'test-token',
    WebSocketImpl: MockWebSocket,
    setTimer: timers.setTimer,
    clearTimer: timers.clearTimer,
    onStateChange: (state) => states.push(state),
    onReconnect: () => { refreshes += 1 },
  })

  const firstSocket = MockWebSocket.instances[0]
  firstSocket.fail()
  firstSocket.onclose?.()
  assert.equal(timers.callbacks.size, 1)
  assert.equal(timers.delays.get(1), 1000)

  timers.run(1)
  assert.equal(MockWebSocket.instances.length, 2)
  const secondSocket = MockWebSocket.instances[1]
  assert.equal(states.at(-1), 'Reconnecting')
  secondSocket.open()
  assert.equal(refreshes, 1)
  assert.equal(states.at(-1), 'Connected')

  client.close()
})

test('unmount cleanup cancels pending reconnect and removes event handlers', () => {
  MockWebSocket.instances = []
  const timers = fakeTimers()
  const client = connectEquipmentSocket({
    url: 'ws://localhost:8080/ws/equipment', token: 'test-token',
    WebSocketImpl: MockWebSocket,
    setTimer: timers.setTimer,
    clearTimer: timers.clearTimer,
  })
  const socket = MockWebSocket.instances[0]
  socket.fail()
  const retryId = [...timers.callbacks.keys()][0]

  client.close()
  timers.run(retryId)

  assert.equal(timers.callbacks.size, 0)
  assert.equal(MockWebSocket.instances.length, 1)
  assert.equal(socket.closed, true)
  assert.equal(socket.onopen, null)
  assert.equal(socket.onclose, null)
})

test('missing token does not open an unauthenticated socket', () => {
  MockWebSocket.instances = []
  const states = []
  const client = connectEquipmentSocket({
    url: 'ws://localhost:8080/ws/equipment', token: null,
    WebSocketImpl: MockWebSocket,
    onStateChange: (state) => states.push(state),
  })

  assert.equal(MockWebSocket.instances.length, 0)
  assert.deepEqual(states, ['Authentication required'])
  client.close()
})
