import { useSettings } from '../api'
import { useNow } from '../hooks/useNow'
import { useI18n } from '../i18n'
import { dropNo, pad2 } from '../lib/format'

function countdown(target: number, now: number) {
  const s = Math.floor(Math.max(0, target - now) / 1000)
  return `${pad2(Math.floor(s / 86400))}:${pad2(Math.floor((s % 86400) / 3600))}:${pad2(Math.floor((s % 3600) / 60))}:${pad2(s % 60)}`
}

export function AnnouncementBar() {
  const { t, l } = useI18n()
  const { data } = useSettings()
  const now = useNow()
  const next = data?.nextDrop
  const target = next?.startsAt ? Date.parse(next.startsAt) : NaN
  if (!next || !Number.isFinite(target)) return null

  return (
    <div className="s-ann">
      <span className="s-lab">{t.announce(`${dropNo(next.number)} — ${l(next.name)}`)}</span>
      <span className="s-mono" dir="ltr" role="timer" style={{ fontSize: 12, fontWeight: 500 }}>
        {countdown(target, now)}
      </span>
    </div>
  )
}
