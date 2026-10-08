import { useEffect, useRef, type MutableRefObject, type ReactNode, type RefObject } from 'react'
import { BlackHole } from './hero/blackHole'
import './VoidStage.css'

/**
 * A small black hole that opens like the hero's, with particles spiralling in. Children render inside
 * the event horizon (e.g. a check mark). Used by the order confirmation and the closed-store page.
 */
export function VoidStage({ children, className, engine, coreRef }: {
  children?: ReactNode
  className?: string
  /** Receives the particle engine, e.g. to call pulse(). */
  engine?: MutableRefObject<BlackHole | null>
  /** The event-horizon element, e.g. to animate something into it. */
  coreRef?: RefObject<HTMLSpanElement>
}) {
  const stage = useRef<HTMLElement>(null)
  const canvas = useRef<HTMLCanvasElement>(null)
  const ownCore = useRef<HTMLSpanElement>(null)
  const core = coreRef ?? ownCore

  useEffect(() => {
    if (!stage.current || !canvas.current || !core.current) return
    const e = new BlackHole(stage.current, canvas.current, core.current)
    if (engine) engine.current = e
    return () => {
      e.destroy()
      if (engine) engine.current = null
    }
    // the refs are stable for the component's lifetime
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  return (
    <section className={className ? `s-done-stage ${className}` : 's-done-stage'} ref={stage} aria-hidden="true">
      <canvas className="s-done-cv" ref={canvas} />
      <div className="s-done-hole">
        <span className="s-done-breathe">
          <img className="s-done-glow s-done-glow-a" src="/assets/void-glow-a.webp" alt="" />
          <img className="s-done-glow s-done-glow-b" src="/assets/void-glow-b.webp" alt="" />
          <img className="s-done-img" src="/assets/void-hole-760.webp" srcSet="/assets/void-hole-760.webp 760w, /assets/void-hole-1100.webp 1100w" sizes="min(110vw, 680px)" alt="" />
        </span>
        <span className="s-done-core" ref={core}>
          {children}
        </span>
      </div>
    </section>
  )
}

/** Ring + tick drawn inside the event horizon (timing via --check-delay). */
export function VoidCheck() {
  return (
    <svg viewBox="0 0 48 48" className="s-done-check">
      <circle cx="24" cy="24" r="22" pathLength="1" className="s-done-ring" />
      <path d="M14 24.5l7 7 13-14" pathLength="1" className="s-done-tick" />
    </svg>
  )
}
