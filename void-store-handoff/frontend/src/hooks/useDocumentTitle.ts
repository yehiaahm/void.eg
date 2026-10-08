import { useEffect } from 'react'

function setMeta(selector: string, attr: 'name' | 'property', key: string, content: string | undefined) {
  let el = document.head.querySelector<HTMLMetaElement>(selector)
  if (!content) {
    el?.remove()
    return
  }
  if (!el) {
    el = document.createElement('meta')
    el.setAttribute(attr, key)
    document.head.appendChild(el)
  }
  el.content = content
}

/** Title + description/OG for client-side navigation (the server injects the first page's tags). */
export function useDocumentTitle(title: string, description?: string, image?: string) {
  useEffect(() => {
    document.title = title
    setMeta('meta[property="og:title"]', 'property', 'og:title', title)
    if (description !== undefined) {
      setMeta('meta[name="description"]', 'name', 'description', description)
      setMeta('meta[property="og:description"]', 'property', 'og:description', description)
    }
    if (image) setMeta('meta[property="og:image"]', 'property', 'og:image', image)
  }, [title, description, image])
}

/** Inject a JSON-LD block for the lifetime of the component. */
export function useJsonLd(data: object | null) {
  const json = data ? JSON.stringify(data) : null
  useEffect(() => {
    if (!json) return
    const s = document.createElement('script')
    s.type = 'application/ld+json'
    s.textContent = json
    document.head.appendChild(s)
    return () => s.remove()
  }, [json])
}
