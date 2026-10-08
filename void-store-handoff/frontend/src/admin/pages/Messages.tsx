import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { useAuth } from '../../store/auth'
import { adminApi, fmtDate, type ContactMessage } from '../api'
import { useT } from '../i18n'
import { Badge, Icons, PageHead, Pager, errText } from '../ui'

const FILTERS = ['NEW', 'HANDLED', 'ALL'] as const
type Filter = (typeof FILTERS)[number]

/** Messages sent from the storefront's Contact page. */
export default function Messages() {
  const { t, lang } = useT()
  const qc = useQueryClient()
  const { user } = useAuth()
  const canDelete = user?.role === 'ADMIN' || user?.role === 'OWNER'
  const [status, setStatus] = useState<Filter>('NEW')
  const [q, setQ] = useState('')
  const [debounced, setDebounced] = useState('')
  const [page, setPage] = useState(0)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    const h = setTimeout(() => {
      setDebounced(q.trim())
      setPage(0)
    }, 300)
    return () => clearTimeout(h)
  }, [q])

  const filters = { status: status === 'ALL' ? undefined : status, q: debounced || undefined }
  const { data, isLoading } = useQuery({
    queryKey: ['admin', 'messages', filters, page],
    queryFn: () => adminApi.messages({ ...filters, page }),
    placeholderData: keepPreviousData,
  })

  const refresh = () => {
    qc.invalidateQueries({ queryKey: ['admin', 'messages'] })
    qc.invalidateQueries({ queryKey: ['admin', 'messages-unread'] })
  }
  const toggle = useMutation({
    mutationFn: (m: ContactMessage) => adminApi.setHandled(m.id, !m.handled),
    onSuccess: refresh,
    onError: (e) => setError(errText(e, t)),
  })
  const remove = useMutation({
    mutationFn: (id: number) => adminApi.deleteMessage(id),
    onSuccess: refresh,
    onError: (e) => setError(errText(e, t)),
  })

  const label: Record<Filter, string> = { NEW: t.msgNew, HANDLED: t.msgHandled, ALL: t.all }

  return (
    <>
      <PageHead title={t.messages} sub={t.messagesSub} />

      <div className="a-filters">
        <input className="a-input a-search" type="search" placeholder={t.searchMessages} value={q} onChange={(e) => setQ(e.target.value)} aria-label={t.search} />
      </div>
      <div className="a-chips" style={{ marginBottom: 16 }} role="group" aria-label={t.status}>
        {FILTERS.map((s) => (
          <button
            key={s}
            type="button"
            className="a-chip"
            aria-pressed={status === s}
            onClick={() => {
              setStatus(s)
              setPage(0)
            }}
          >
            {label[s]}
          </button>
        ))}
      </div>
      {error && (
        <p className="a-error" style={{ marginBottom: 12 }}>
          {error}
        </p>
      )}

      <div className="a-table-wrap">
        <table className="a-table a-wait">
          <thead>
            <tr>
              <th>{t.name}</th>
              <th className="a-hide-sm">{t.received}</th>
              <th>{t.status}</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {data?.items.map((m) => (
              <tr key={m.id}>
                <td>
                  {m.name}
                  <div className="a-faint" dir="ltr" style={{ textAlign: 'start' }}>
                    <a href={`mailto:${m.email}`}>{m.email}</a>
                    {m.phone && (
                      <>
                        {' · '}
                        <a href={`tel:${m.phone}`}>{m.phone}</a>
                      </>
                    )}
                  </div>
                  <p className="a-wait-notes">{m.message}</p>
                </td>
                <td className="a-muted a-hide-sm">{fmtDate(m.createdAt, lang, true)}</td>
                <td>
                  <Badge s={m.handled ? 'on' : 'NEW'}>{m.handled ? t.msgHandled : t.msgNew}</Badge>
                </td>
                <td className="a-num">
                  <div className="a-actions" style={{ justifyContent: 'flex-end', flexWrap: 'nowrap' }}>
                    <a className="a-btn a-btn-sm" href={`mailto:${m.email}?subject=${encodeURIComponent('VOID')}`}>
                      {t.reply}
                    </a>
                    <button type="button" className="a-btn a-btn-sm" disabled={toggle.isPending} onClick={() => toggle.mutate(m)}>
                      {m.handled ? t.markUnhandled : t.markHandled}
                    </button>
                    {canDelete && (
                      <button
                        type="button"
                        className="a-btn a-btn-sm a-btn-icon"
                        aria-label={t.delete}
                        title={t.delete}
                        disabled={remove.isPending}
                        onClick={() => window.confirm(t.confirmDelete) && remove.mutate(m.id)}
                      >
                        <Icons.trash />
                      </button>
                    )}
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {!isLoading && data?.items.length === 0 && <p className="a-empty">{t.empty}</p>}
        {isLoading && <p className="a-empty">{t.loading}</p>}
      </div>
      {data && <Pager page={data.page} total={data.total} size={data.size} onPage={setPage} />}
    </>
  )
}
