import { useEffect, useRef, useState, type ReactNode } from 'react'
import { useI18n } from '../../i18n'
import type { CapColor } from './capModel'
import type { CapView } from './capStage'
import './CapViewer.css'

const SPIN_CARD = 0.55 // rad/s — a full turn in ~11 s
const SPIN_PAGE = 0.4

interface Props {
  color: CapColor
  /** Accessible name of the product. */
  name: string
  /** Product page: drag to spin, controls, keyboard. Without it the cap just turns on its own. */
  interactive?: boolean
  /** Shown until the 3D scene is ready, and instead of it when WebGL is unavailable. */
  poster?: ReactNode
  className?: string
  /** Gives the caller the live view (for angle presets). */
  onView?: (v: CapView | null) => void
}

const reducedMotion = () => typeof window !== 'undefined' && window.matchMedia('(prefers-reduced-motion: reduce)').matches

/**
 * A 3D cap that keeps turning (shop cards) or can be dragged through 360° (product page).
 * three.js is loaded on demand, only when a cap is actually on the page.
 */
export function CapViewer({ color, name, interactive = false, poster, className, onView }: Props) {
  const { t } = useI18n()
  const canvas = useRef<HTMLCanvasElement>(null)
  const view = useRef<CapView | null>(null)
  const [state, setState] = useState<'loading' | 'ready' | 'failed'>('loading')
  const [spinning, setSpinning] = useState(true)
  const reduced = useRef(reducedMotion())

  useEffect(() => {
    const el = canvas.current
    if (!el) return
    let cancelled = false
    let created: CapView | null = null
    import('./capStage')
      .then((m) => {
        if (cancelled) return
        const base = interactive ? SPIN_PAGE : SPIN_CARD
        created = m.createView({ canvas: el, color, interactive, spin: reduced.current ? 0 : base, still: reduced.current && !interactive })
        view.current = created
        onView?.(created)
        setState('ready')
      })
      .catch(() => {
        if (!cancelled) setState('failed')
      })
    return () => {
      cancelled = true
      created?.destroy()
      view.current = null
      onView?.(null)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [color, interactive])

  const toggleSpin = () => {
    const next = !spinning
    setSpinning(next)
    view.current?.setSpin(next ? SPIN_PAGE : 0)
  }

  return (
    <div className={`s-cap3d${interactive ? ' s-cap3d-live' : ''}${className ? ` ${className}` : ''}`} data-state={state}>
      {state !== 'ready' && poster}
      {state !== 'failed' && (
        <canvas
          ref={canvas}
          className="s-cap3d-canvas"
          role="img"
          aria-label={interactive ? t.cap3d.label(name) : name}
          tabIndex={interactive ? 0 : -1}
        />
      )}
      {interactive && state === 'ready' && (
        <div className="s-cap3d-ui">
          <span className="s-cap3d-hint">{t.cap3d.hint}</span>
          <div className="s-cap3d-btns">
            <button type="button" className="s-cap3d-btn" onClick={toggleSpin} aria-pressed={spinning} aria-label={spinning ? t.cap3d.pause : t.cap3d.play} title={spinning ? t.cap3d.pause : t.cap3d.play}>
              {spinning ? (
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                  <path d="M4 12a8 8 0 0 1 13.7-5.6L20 8.5" />
                  <path d="M20 4v4.5h-4.5" />
                  <path d="M20 12a8 8 0 0 1-13.7 5.6L4 15.5" />
                  <path d="M4 20v-4.5h4.5" />
                </svg>
              ) : (
                <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
                  <path d="M8 5.5v13l11-6.5z" />
                </svg>
              )}
            </button>
            <button type="button" className="s-cap3d-btn" onClick={() => view.current?.tilt()} aria-label={t.cap3d.tilt} title={t.cap3d.tilt}>
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                <ellipse cx="12" cy="12" rx="9" ry="3.6" />
                <path d="M12 3v18" strokeDasharray="1.5 3" />
                <path d="M9 6l3-3 3 3" />
                <path d="M9 18l3 3 3-3" />
              </svg>
            </button>
          </div>
        </div>
      )}
    </div>
  )
}
