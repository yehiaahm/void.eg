import { useEffect, useRef, useState } from 'react'
import { ApiError, api, type ShippingZone } from '../api'
import { useI18n } from '../i18n'
import type { Strings } from '../i18n/strings'
import { LocateError, currentPosition } from '../lib/locate'
import { resolveZone } from '../lib/zones'
import './LocateButton.css'

export interface LocatedAddress {
  /** matched delivery zone ('' when the map's governorate is not one we deliver to) */
  code: string
  /** the map's governorate name to show when it could not be matched; null = show the zone's own name */
  text: string | null
  city: string
  street: string
  building: string
  /** the exact spot the device reported */
  lat: number
  lng: number
}

type Status = { kind: 'idle' } | { kind: 'busy' } | { kind: 'ok' | 'warn' | 'error'; message: string }

function failure(e: unknown, t: Strings): string {
  if (e instanceof LocateError) {
    return { unsupported: t.locateUnsupported, denied: t.locateDenied, unavailable: t.locateUnavailable, timeout: t.locateTimeout }[e.reason]
  }
  if (e instanceof ApiError) return e.code === 'rate_limited' ? t.tooMany : t.locateFailed
  if (e instanceof TypeError) return t.networkError
  return t.locateFailed
}

/** "Use my current location": asks the device for its position, looks the address up and hands it to the form. */
export function LocateButton({ zones, onFill, disabled }: { zones: ShippingZone[]; onFill: (a: LocatedAddress) => void; disabled?: boolean }) {
  const { lang, t } = useI18n()
  const [status, setStatus] = useState<Status>({ kind: 'idle' })
  const alive = useRef(true)
  const busy = useRef(false)
  useEffect(() => {
    alive.current = true
    return () => {
      alive.current = false
    }
  }, [])

  const run = async () => {
    if (busy.current) return
    busy.current = true
    setStatus({ kind: 'busy' })
    try {
      const pos = await currentPosition()
      const place = await api.reverseGeocode(pos.lat, pos.lng, lang)
      if (!alive.current) return
      if (!place.inEgypt) return setStatus({ kind: 'error', message: t.locateOutside })
      if (!place.governorate && !place.city && !place.street && !place.building) return setStatus({ kind: 'error', message: t.locateNothing })

      const zone = place.governorate ? resolveZone(zones, place.governorate) : null
      onFill({
        code: zone?.code ?? '',
        text: zone ? null : place.governorate,
        city: place.city,
        street: place.street,
        building: place.building,
        lat: pos.lat,
        lng: pos.lng,
      })
      const approx = pos.accuracy > 1000 ? ` ${t.locateApprox(Math.round(pos.accuracy / 100) * 100)}` : ''
      setStatus(zone ? { kind: approx ? 'warn' : 'ok', message: t.locateDone + approx } : { kind: 'warn', message: t.locateNoGov + approx })
    } catch (e) {
      if (alive.current) setStatus({ kind: 'error', message: failure(e, t) })
    } finally {
      busy.current = false
    }
  }

  const isBusy = status.kind === 'busy'
  return (
    <div className="s-locate-wrap">
      <button type="button" className="s-locate" onClick={run} disabled={disabled || isBusy} aria-busy={isBusy}>
        {isBusy ? (
          <span className="s-spinner" aria-hidden="true" />
        ) : (
          <svg
            width="18"
            height="18"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="1.5"
            strokeLinecap="round"
            aria-hidden="true"
          >
            <circle cx="12" cy="12" r="6.5" />
            <circle cx="12" cy="12" r="1.8" fill="currentColor" stroke="none" />
            <path d="M12 2.5v3M12 18.5v3M2.5 12h3M18.5 12h3" />
          </svg>
        )}
        {isBusy ? t.locating : t.useMyLocation}
      </button>
      {(status.kind === 'ok' || status.kind === 'warn') && (
        <p className={status.kind === 'ok' ? 's-note' : 's-note s-locate-warn'} role="status">
          {status.message}
        </p>
      )}
      {status.kind === 'error' && (
        <p className="s-alert" role="alert">
          {status.message}
        </p>
      )}
    </div>
  )
}
