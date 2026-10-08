import { useParams } from 'react-router-dom'
import { usePage } from '../api'
import { useDocumentTitle } from '../hooks/useDocumentTitle'
import { useI18n } from '../i18n'
import { NotFoundPage } from './NotFoundPage'
import '../styles/forms.css'

type Block = { kind: 'h'; text: string } | { kind: 'ul'; items: string[] } | { kind: 'p'; text: string }

/** Admin text → blocks: blank lines split paragraphs, "## " starts a heading, "- " a bullet point. */
function parse(body: string): Block[] {
  const blocks: Block[] = []
  let para: string[] = []
  const flush = () => {
    if (para.length) blocks.push({ kind: 'p', text: para.join('\n') })
    para = []
  }
  for (const raw of body.split(/\r?\n/)) {
    const line = raw.trim()
    if (!line) {
      flush()
      continue
    }
    if (line.startsWith('## ')) {
      flush()
      blocks.push({ kind: 'h', text: line.slice(3).trim() })
      continue
    }
    const bullet = /^[-•]\s+(.*)$/.exec(line)
    if (bullet) {
      flush()
      const last = blocks.at(-1)
      if (last?.kind === 'ul') last.items.push(bullet[1]!)
      else blocks.push({ kind: 'ul', items: [bullet[1]!] })
      continue
    }
    para.push(line)
  }
  flush()
  return blocks
}

/** Policy / info pages edited in the admin (plain text with light formatting, see `parse`). */
export function InfoPage() {
  const { slug = '' } = useParams()
  const { l } = useI18n()
  const { data: page, isLoading } = usePage(slug)
  useDocumentTitle(page ? `${l(page.title)} — VOID` : 'VOID')

  if (isLoading) return <main className="s-main s-page" aria-busy="true" />
  if (!page) return <NotFoundPage />
  return (
    <main className="s-main s-page">
      <article className="s-narrow s-prose">
        <h1 className="s-title">{l(page.title)}</h1>
        {parse(l(page.body)).map((b, i) =>
          b.kind === 'h' ? (
            <h2 key={i}>{b.text}</h2>
          ) : b.kind === 'ul' ? (
            <ul key={i}>
              {b.items.map((x, j) => (
                <li key={j}>{x}</li>
              ))}
            </ul>
          ) : (
            <p key={i}>{b.text}</p>
          ),
        )}
      </article>
    </main>
  )
}
