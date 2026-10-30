function isValidLocalDateTime(value) {
  const match = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::(\d{2}))?$/.exec(String(value ?? ''))
  if (!match) return false
  const [, yearText, monthText, dayText, hourText, minuteText, secondText = '00'] = match
  const year = Number(yearText)
  const month = Number(monthText)
  const day = Number(dayText)
  const hour = Number(hourText)
  const minute = Number(minuteText)
  const second = Number(secondText)
  const leapYear = year % 4 === 0 && (year % 100 !== 0 || year % 400 === 0)
  const daysByMonth = [31, leapYear ? 29 : 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31]

  return month >= 1 && month <= 12
    && day >= 1 && day <= daysByMonth[month - 1]
    && hour <= 23 && minute <= 59 && second <= 59
}

/** Serialize a datetime-local value as a timezone-free ISO LocalDateTime. */
export function serializeLocalDateTime(value) {
  if (!isValidLocalDateTime(value)) {
    throw new TypeError('Booking date and time must be a valid local date-time.')
  }
  return value.length === 16 ? `${value}:00` : value
}

export function buildBookingPayload({ equipmentId, startTime, endTime, purpose }) {
  return {
    equipmentId: Number(equipmentId),
    startTime: serializeLocalDateTime(startTime),
    endTime: serializeLocalDateTime(endTime),
    purpose: String(purpose ?? '').trim(),
  }
}
