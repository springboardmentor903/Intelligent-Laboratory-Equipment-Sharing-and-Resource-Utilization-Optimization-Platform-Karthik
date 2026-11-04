import assert from 'node:assert/strict'
import test from 'node:test'

test('heatmap intensity level maps scores correctly', () => {
  function getIntensityLevel(score) {
    if (score > 75) return 4
    if (score > 50) return 3
    if (score > 25) return 2
    if (score > 0) return 1
    return 0
  }

  assert.equal(getIntensityLevel(0), 0)
  assert.equal(getIntensityLevel(10), 1)
  assert.equal(getIntensityLevel(25), 1)
  assert.equal(getIntensityLevel(25.1), 2)
  assert.equal(getIntensityLevel(50), 2)
  assert.equal(getIntensityLevel(74.9), 3)
  assert.equal(getIntensityLevel(75), 3)
  assert.equal(getIntensityLevel(75.1), 4)
  assert.equal(getIntensityLevel(100), 4)
})

test('idle time alert levels classify properly based on thresholds', () => {
  function classifyIdleAlert(idlePct, threshold = 70.0) {
    if (idlePct >= 85.0) return { alert: true, level: 'CRITICAL' }
    if (idlePct >= threshold) return { alert: true, level: 'WARNING' }
    return { alert: false, level: 'NORMAL' }
  }

  assert.deepEqual(classifyIdleAlert(90.0), { alert: true, level: 'CRITICAL' })
  assert.deepEqual(classifyIdleAlert(85.0), { alert: true, level: 'CRITICAL' })
  assert.deepEqual(classifyIdleAlert(75.0), { alert: true, level: 'WARNING' })
  assert.deepEqual(classifyIdleAlert(70.0), { alert: true, level: 'WARNING' })
  assert.deepEqual(classifyIdleAlert(69.9), { alert: false, level: 'NORMAL' })
  assert.deepEqual(classifyIdleAlert(20.0), { alert: false, level: 'NORMAL' })
})

test('demand analysis calculates rates and unmet demand correctly', () => {
  const sample = {
    totalRequests: 20,
    fulfilled: 14,
    rejected: 4,
    noShow: 2,
    rejectedHours: 12.0,
    cancelledHours: 4.5,
  }

  const fulfillmentRate = (sample.fulfilled / sample.totalRequests) * 100
  const rejectionRate = (sample.rejected / sample.totalRequests) * 100
  const noShowRate = (sample.noShow / sample.totalRequests) * 100
  const unmetDemandHours = sample.rejectedHours + sample.cancelledHours

  assert.equal(fulfillmentRate, 70.0)
  assert.equal(rejectionRate, 20.0)
  assert.equal(noShowRate, 10.0)
  assert.equal(unmetDemandHours, 16.5)
})

test('capacity calculation respects units and operating hours', () => {
  function calculateCapacity(quantity, days, operatingHoursPerDay) {
    return Math.round(quantity * days * operatingHoursPerDay * 10) / 10
  }

  // 1 unit, 7 days, 24h
  assert.equal(calculateCapacity(1, 7, 24), 168.0)
  // 3 units, 30 days, 10h standard lab day
  assert.equal(calculateCapacity(3, 30, 10), 900.0)
  // 2 units, 1 day, 8h business hours
  assert.equal(calculateCapacity(2, 1, 8), 16.0)
})

test('peak slot tie breaker follows utilization score, then usage, then earlier hour', () => {
  const slots = [
    { hour: 14, utilizationScore: 75.0, totalUsageHours: 6.0 },
    { hour: 10, utilizationScore: 80.0, totalUsageHours: 6.0 },
    { hour: 11, utilizationScore: 80.0, totalUsageHours: 6.5 },
    { hour: 9,  utilizationScore: 80.0, totalUsageHours: 6.5 },
  ]

  const sorted = slots.slice().sort((a, b) => {
    if (b.utilizationScore !== a.utilizationScore) return b.utilizationScore - a.utilizationScore
    if (b.totalUsageHours !== a.totalUsageHours) return b.totalUsageHours - a.totalUsageHours
    return a.hour - b.hour
  })

  // 9 and 11 tie on score (80) and usage (6.5), so earlier hour 9 wins
  assert.equal(sorted[0].hour, 9)
  assert.equal(sorted[1].hour, 11)
  assert.equal(sorted[2].hour, 10)
  assert.equal(sorted[3].hour, 14)
})

test('quiet slots are ranked by lowest historical heatmap score first', () => {
  const candidateQuietSlots = [
    { hour: 10, avgScore: 35.0 },
    { hour: 14, avgScore: 12.0 },
    { hour: 16, avgScore: 8.5 },
    { hour: 11, avgScore: 45.0 },
  ]

  const ranked = candidateQuietSlots.slice().sort((a, b) => a.avgScore - b.avgScore)
  assert.equal(ranked[0].hour, 16)
  assert.equal(ranked[0].avgScore, 8.5)
  assert.equal(ranked[1].hour, 14)
  assert.equal(ranked[1].avgScore, 12.0)
})
