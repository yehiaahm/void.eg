import { useEffect, useRef } from 'react'
import { useSettings } from '../../api'
import { useGoShop } from '../../hooks/useGoShop'
import { useI18n } from '../../i18n'
import { dropNo } from '../../lib/format'
import { BlackHole } from './blackHole'
import './Hero.css'

export function Hero() {
  const { lang, t, l } = useI18n()
  const { data } = useSettings()
  const goShop = useGoShop()
  const hero = useRef<HTMLElement>(null)
  const canvas = useRef<HTMLCanvasElement>(null)
  const core = useRef<HTMLSpanElement>(null)

  useEffect(() => {
    let engine: BlackHole | null = null
    let idle = 0
    let cancelled = false
    // Defer the canvas work until after load so it never competes with the first paint.
    const start = () => {
      if (cancelled || !hero.current || !canvas.current || !core.current) return
      engine = new BlackHole(hero.current, canvas.current, core.current)
    }
    const schedule = () => {
      const ric = (window as Window & { requestIdleCallback?: (cb: () => void, o?: { timeout: number }) => number }).requestIdleCallback
      idle = ric ? ric(start, { timeout: 1200 }) : window.setTimeout(start, 200)
    }
    if (document.readyState === 'complete') schedule()
    else window.addEventListener('load', schedule, { once: true })
    return () => {
      cancelled = true
      window.removeEventListener('load', schedule)
      const cic = (window as Window & { cancelIdleCallback?: (id: number) => void }).cancelIdleCallback
      if (cic) cic(idle)
      else window.clearTimeout(idle)
      engine?.destroy()
    }
  }, [])

  const drop = data?.currentDrop

  return (
    <section className="s-hero" ref={hero}>
      <canvas className="s-cv" ref={canvas} aria-hidden="true" />
      <div className="s-holebox" aria-hidden="true">
        <span className="s-breathe">
          <img className="s-glow s-glow-a" src="/assets/void-glow-a.webp" alt="" decoding="async" />
          <img className="s-glow s-glow-b" src="/assets/void-glow-b.webp" alt="" decoding="async" />
          <img
            className="s-hole"
            src="/assets/void-hole.webp"
            srcSet="/assets/void-hole-760.webp 760w, /assets/void-hole-1100.webp 1100w, /assets/void-hole.webp 1480w"
            sizes="(max-width: 900px) 135vw, min(76vw, 920px)"
            alt=""
            width={1480}
            height={560}
            fetchPriority="high"
          />
        </span>
        <span className="s-core" ref={core} />
      </div>
      <p className="s-lab s-k">{drop ? t.dropLabel(dropNo(drop.number), l(drop.name)) : ' '}</p>
      <a className="s-cta" href={`/${lang}#shop`} onClick={goShop}>
        {t.shopTheDrop}
      </a>
    </section>
  )
}
