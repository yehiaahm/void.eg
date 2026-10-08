import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { useAuth } from '../../store/auth'
import { adminApi, fmtDate, type WaitlistEntry } from '../api'
import { useT } from '../i18n'
import { StoreSwitch } from '../StoreSwitch'
import { Badge, Icons, PageHead, Pager, errText } from '../ui'

const FILTERS = ['NEW', 'CONTACTED', 'ALL'] as const
type Filter = (typeof FILTERS)[number]

/** 01XXXXXXXXX → WhatsApp chat link (Egypt, +20). */
const whatsApp = (phone: string) => `https://wa.me/2${phone}`

/** People who left their details on the closed-store page. */
export default function Waitlist() {
  const { t, lang } = useT()
  const qc = useQueryClient()
  const { user } = useAuth()
  const canDelete = user?.role === 'ADMIN' || user?.role === 'OWNER'
  const [status, setStatus] = useState<Filter>('NEW')
  const [q, setQ] = useState('')
  const [debounced, setDebounced] = useState('')
  const [page, setPage] = useState(0)
  const [error, setError] = useState<string | null>(null)
  const [exporting, setExporting] = useState(false)

  useEffect(() => {
    const h = setTimeout(() => {
      setDebounced(q.trim())
      setPage(0)
    }, 300)
    return () => clearTimeout(h)
  }, [q])

  const filters = { status: status === 'ALL' ? undefined : status, q: debounced || undefined }
  const { data, isLoading } = useQuery({
    queryKey: ['admin', 'waitlist', filters, page],
    queryFn: () => adminApi.waitlist({ ...filters, page }),
    placeholderData: keepPreviousData,
  })

  const refresh = () => {
    qc.invalidateQueries({ queryKey: ['admin', 'waitlist'] })
    qc.invalidateQueries({ queryKey: ['admin', 'store-status'] })
  }
  const toggle = useMutation({
    mutationFn: (e: WaitlistEntry) => adminApi.setContacted(e.id, !e.contacted),
    onSuccess: refresh,
    onError: (e) => setError(errText(e, t)),
  })
  const remove = useMutation({
    mutationFn: (id: number) => adminApi.deleteWaitlist(id),
    onSuccess: refresh,
    onError: (e) => setError(errText(e, t)),
  })

  const exportCsv = async () => {
    setExporting(true)
    setError(null)
    try {
      const blob = await adminApi.exportWaitlist(filters)
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `void-waitlist-${new Date().toISOString().slice(0, 10)}.csv`
      a.click()
      URL.revokeObjectURL(url)
    } catch (e) {
      setError(errText(e, t))
    } finally {
      setExporting(false)
    }
  }

  const label: Record<Filter, string> = { NEW: t.waitNew, CONTACTED: t.waitContacted, ALL: t.all }

  return (
    <>
      <PageHead title={t.waitlist} sub={t.waitlistSub}>
        <button type="button" className="a-btn" onClick={exportCsv} disabled={exporting}>
          {t.exportCsv}
        </button>
      </PageHead>

      <div className="a-stack">
        <StoreSwitch showWaitlistLink={false} />

        <div>
          <div className="a-filters">
            <input className="a-input a-search" type="search" placeholder={t.searchWaitlist} value={q} onChange={(e) => setQ(e.target.value)} aria-label={t.search} />
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
                  <th>{t.phone}</th>
                  <th className="a-hide-sm">{t.signedUp}</th>
                  <th>{t.status}</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {data?.items.map((e) => (
                  <tr key={e.id}>
                    <td>
                      {e.name}
                      {e.email && (
                        <div className="a-faint" dir="ltr" style={{ textAlign: 'start' }}>
                          <a href={`mailto:${e.email}`}>{e.email}</a>
                        </div>
                      )}
                      {e.notes && <p className="a-wait-notes">{e.notes}</p>}
                    </td>
                    <td>
                      <a className="a-mono" dir="ltr" href={`tel:${e.phone}`}>
                        {e.phone}
                      </a>
                      <div>
                        <a className="a-faint" href={whatsApp(e.phone)} target="_blank" rel="noopener noreferrer">
                          {t.whatsapp} ↗
                        </a>
                      </div>
                    </td>
                    <td className="a-muted a-hide-sm">{fmtDate(e.createdAt, lang, true)}</td>
                    <td>
                      <Badge s={e.contacted ? 'on' : 'NEW'}>{e.contacted ? t.waitContacted : t.waitNew}</Badge>
                    </td>
                    <td className="a-num">
                      <div className="a-actions" style={{ justifyContent: 'flex-end', flexWrap: 'nowrap' }}>
                        <button type="button" className="a-btn a-btn-sm" disabled={toggle.isPending} onClick={() => toggle.mutate(e)}>
                          {e.contacted ? t.markNew : t.markContacted}
                        </button>
                        {canDelete && (
                          <button
                            type="button"
                            className="a-btn a-btn-sm a-btn-icon"
                            aria-label={t.delete}
                            title={t.delete}
                            disabled={remove.isPending}
                            onClick={() => window.confirm(t.confirmDelete) && remove.mutate(e.id)}
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
        </div>
      </div>
    </>
  )
}
