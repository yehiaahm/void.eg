export type LocateReason = 'unsupported' | 'denied' | 'unavailable' | 'timeout'

/** Why the device location could not be read (the checkout shows a matching message). */
export class LocateError extends Error {
  reason: LocateReason
  constructor(reason: LocateReason) {
    super(reason)
    this.reason = reason
  }
}

export interface Position {
  lat: number
  lng: number
  /** radius of uncertainty in metres */
  accuracy: number
}

/** The device's current position, asked once when the shopper taps the button (never in the background). */
export function currentPosition(): Promise<Position> {
  return new Promise((resolve, reject) => {
    // geolocation only exists on https (and localhost); some in-app browsers block it too
    if (typeof navigator === 'undefined' || !navigator.geolocation || !window.isSecureContext) {
      reject(new LocateError('unsupported'))
      return
    }
    navigator.geolocation.getCurrentPosition(
      (p) => {
        const { latitude: lat, longitude: lng, accuracy } = p.coords
        if (!Number.isFinite(lat) || !Number.isFinite(lng)) reject(new LocateError('unavailable'))
        else resolve({ lat, lng, accuracy: Number.isFinite(accuracy) ? accuracy : 0 })
      },
      (e) => reject(new LocateError(e.code === e.PERMISSION_DENIED ? 'denied' : e.code === e.TIMEOUT ? 'timeout' : 'unavailable')),
      { enableHighAccuracy: true, timeout: 15_000, maximumAge: 30_000 },
    )
  })
}
