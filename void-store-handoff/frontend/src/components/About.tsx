import { usePage } from '../api'
import { useI18n } from '../i18n'
import './About.css'

/** Brand story under the shop grid: the "about" page from Admin → Content (blank line = new paragraph). */
export function About() {
  const { l } = useI18n()
  const { data: page } = usePage('about')
  if (!page) return null
  const paras = l(page.body)
    .split(/\r?\n\s*\r?\n/)
    .map((p) => p.trim())
    .filter(Boolean)
  return (
    <section id="about" className="s-sec s-about" aria-labelledby="about-h">
      <h2 id="about-h" className="s-lab">
        {l(page.title)}
      </h2>
      {paras.map((p, i) => (
        <p key={i}>{p}</p>
      ))}
      <p className="s-about-sign" dir="ltr">
        VOID — Nothing is truly empty.
      </p>
    </section>
  )
}
