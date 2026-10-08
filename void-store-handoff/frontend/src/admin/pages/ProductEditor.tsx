import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useRef, useState, type FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { assetUrl } from '../../api/http'
import type { Localized } from '../../api/types'
import { useAuth } from '../../store/auth'
import { useToast } from '../../store/toast'
import { adminApi, fromPiastres, toPiastres, type AdminProduct, type AdminVariant, type ProductInput } from '../api'
import { useT } from '../i18n'
import { Badge, Field, Icons, PageHead, Pair, Pick, errText } from '../ui'

const L = (en = '', ar = ''): Localized => ({ en, ar })
const KINDS = ['front', 'back', 'detail', 'body']

interface FormState extends Omit<ProductInput, 'price' | 'compareAtPrice'> {
  price: string
  compareAtPrice: string
}

const blank = (): FormState => ({
  slug: '',
  dropId: null,
  status: 'DRAFT',
  category: L(),
  name: L(),
  description: L(),
  price: '',
  compareAtPrice: '',
  fit: L(),
  fabric: L(),
  weight: L(),
  colour: L(),
  care: L(),
  sizeFit: L(),
  sortOrder: 0,
  variants: ['S', 'M', 'L', 'XL'].map((size) => ({ size, sku: '', stock: 0 })),
})

const fromProduct = (p: AdminProduct): FormState => ({
  slug: p.slug,
  dropId: p.dropId,
  status: p.status,
  category: p.category,
  name: p.name,
  description: p.description,
  price: fromPiastres(p.price),
  compareAtPrice: fromPiastres(p.compareAtPrice),
  fit: p.fit,
  fabric: p.fabric,
  weight: p.weight,
  colour: p.colour,
  care: p.care,
  sizeFit: p.sizeFit,
  sortOrder: p.sortOrder,
  variants: p.variants.map((v) => ({ size: v.size, sku: v.sku ?? '', stock: v.stock })),
})

const slugify = (s: string) =>
  s.toLowerCase().trim().replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '')

export default function ProductEditor() {
  const { id } = useParams()
  const isNew = !id
  const pid = Number(id)
  const { t } = useT()
  const { user } = useAuth()
  const canEdit = user?.role === 'ADMIN' || user?.role === 'OWNER'
  const navigate = useNavigate()
  const qc = useQueryClient()
  const flash = useToast()

  const { data: product, isLoading } = useQuery({ queryKey: ['admin', 'product', pid], queryFn: () => adminApi.product(pid), enabled: !isNew })
  const { data: drops = [] } = useQuery({ queryKey: ['admin', 'drops'], queryFn: adminApi.drops })
  const [f, setF] = useState<FormState>(blank)
  const [slugTouched, setSlugTouched] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (product) {
      setF(fromProduct(product))
      setSlugTouched(true)
    }
  }, [product])

  const set = <K extends keyof FormState>(k: K, v: FormState[K]) => setF((x) => ({ ...x, [k]: v }))

  const toInput = (): ProductInput | string => {
    const price = toPiastres(f.price)
    const compare = toPiastres(f.compareAtPrice)
    if (Number.isNaN(price) || Number.isNaN(compare)) return t.price
    if (!f.name.en.trim()) return `${t.name}: ${t.english}`
    if (!/^[a-z0-9]+(-[a-z0-9]+)*$/.test(f.slug)) return `${t.slug}: ${t.slugHint}`
    if (!f.variants.length) return t.variants
    return {
      ...f,
      price,
      compareAtPrice: compare,
      variants: f.variants.map((v) => ({ size: v.size.trim().toUpperCase(), sku: v.sku?.trim() || null, stock: Math.max(0, Math.floor(Number(v.stock) || 0)) })),
    }
  }

  const save = useMutation({
    mutationFn: (input: ProductInput) => (isNew ? adminApi.createProduct(input) : adminApi.updateProduct(pid, input)),
    onSuccess: (p) => {
      qc.setQueryData(['admin', 'product', p.id], p)
      qc.invalidateQueries({ queryKey: ['admin', 'products'] })
      qc.invalidateQueries({ queryKey: ['products'] })
      flash(t.saved)
      if (isNew) navigate(`/admin/products/${p.id}`, { replace: true })
    },
    onError: (e) => setError(errText(e, t)),
  })

  const saveStock = useMutation({
    mutationFn: () => adminApi.updateStock(pid, f.variants.map((v) => ({ size: v.size, sku: v.sku || null, stock: Math.max(0, Number(v.stock) || 0) }))),
    onSuccess: (p) => {
      qc.setQueryData(['admin', 'product', p.id], p)
      qc.invalidateQueries({ queryKey: ['admin', 'products'] })
      flash(t.saved)
    },
    onError: (e) => setError(errText(e, t)),
  })

  const del = useMutation({
    mutationFn: () => adminApi.deleteProduct(pid),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin', 'products'] })
      navigate('/admin/products', { replace: true })
    },
    onError: (e) => setError(errText(e, t)),
  })

  const submit = (e: FormEvent) => {
    e.preventDefault()
    setError(null)
    if (!canEdit) return saveStock.mutate()
    const input = toInput()
    if (typeof input === 'string') return setError(input)
    save.mutate(input)
  }

  if (!isNew && isLoading) return <p className="a-muted">{t.loading}</p>

  const setVariant = (i: number, patch: Partial<AdminVariant>) =>
    set('variants', f.variants.map((v, j) => (j === i ? { ...v, ...patch } : v)))

  const ro = !canEdit
  return (
    <form onSubmit={submit}>
      <Link to="/admin/products" className="a-btn a-btn-sm" style={{ marginBottom: 14 }}>
        {t.products}
      </Link>
      <PageHead title={isNew ? t.newProduct : f.name.en || t.product} sub={!isNew && <Badge s={f.status}>{t.productStatus[f.status]}</Badge>}>
        {!isNew && f.status === 'ACTIVE' && (
          <a className="a-btn" href={`/en/product/${product?.slug}`} target="_blank" rel="noopener">
            {t.previewInStore} ↗
          </a>
        )}
        {!isNew && canEdit && (
          <button type="button" className="a-btn a-btn-danger" onClick={() => window.confirm(t.deleteProductConfirm) && del.mutate()}>
            {t.delete}
          </button>
        )}
        <button type="submit" className="a-btn a-btn-primary" disabled={save.isPending || saveStock.isPending}>
          {save.isPending || saveStock.isPending ? t.saving : canEdit ? t.save : t.quickStock}
        </button>
      </PageHead>
      {error && <p className="a-error" style={{ marginBottom: 16 }}>{error}</p>}

      <div className="a-split">
        <div className="a-stack">
          <fieldset className="a-card a-form" disabled={ro} style={{ margin: 0 }}>
            <Pair
              label={t.name}
              value={f.name}
              onChange={(v) => {
                setF((x) => ({ ...x, name: v, slug: slugTouched ? x.slug : slugify(v.en) }))
              }}
            />
            <Field
              label={t.slug}
              hint={t.slugHint}
              value={f.slug}
              dir="ltr"
              onChange={(e) => {
                setSlugTouched(true)
                set('slug', e.target.value.toLowerCase())
              }}
            />
            <Pair label={t.category} value={f.category} onChange={(v) => set('category', v)} />
            <Pair label={t.description} value={f.description} onChange={(v) => set('description', v)} multiline />
          </fieldset>

          <div className="a-card">
            <h2>{t.images}</h2>
            {isNew ? <p className="a-info">{t.saveFirst}</p> : <Images product={product!} readOnly={ro} />}
          </div>

          <fieldset className="a-card a-form" disabled={ro} style={{ margin: 0 }}>
            <h2 className="a-card-title">{t.details}</h2>
            <Pair label={t.fit} value={f.fit} onChange={(v) => set('fit', v)} />
            <Pair label={t.fabric} value={f.fabric} onChange={(v) => set('fabric', v)} />
            <Pair label={t.weight} value={f.weight} onChange={(v) => set('weight', v)} />
            <Pair label={t.colour} value={f.colour} onChange={(v) => set('colour', v)} />
            <Pair label={t.care} value={f.care} onChange={(v) => set('care', v)} />
            <Pair label={t.sizeFit} value={f.sizeFit} onChange={(v) => set('sizeFit', v)} multiline rows={3} />
          </fieldset>
        </div>

        <div className="a-stack">
          <fieldset className="a-card a-form" disabled={ro} style={{ margin: 0 }}>
            <Pick label={t.status} hint={t.statusHint} value={f.status} onChange={(e) => set('status', e.target.value)}>
              {['DRAFT', 'ACTIVE', 'ARCHIVED'].map((s) => (
                <option key={s} value={s}>
                  {t.productStatus[s]}
                </option>
              ))}
            </Pick>
            <Field label={t.price} value={f.price} onChange={(e) => set('price', e.target.value)} inputMode="decimal" dir="ltr" placeholder="0" />
            <Field label={t.compareAt} hint={t.compareAtHint} value={f.compareAtPrice} onChange={(e) => set('compareAtPrice', e.target.value)} inputMode="decimal" dir="ltr" />
            <Pick label={t.drop} value={f.dropId ?? ''} onChange={(e) => set('dropId', e.target.value ? Number(e.target.value) : null)}>
              <option value="">{t.noDrop}</option>
              {drops.map((d) => (
                <option key={d.id} value={d.id}>
                  {String(d.number).padStart(2, '0')} — {d.name.en}
                </option>
              ))}
            </Pick>
            <Field label={t.sortOrder} hint={t.sortHint} type="number" value={f.sortOrder} onChange={(e) => set('sortOrder', Number(e.target.value) || 0)} />
          </fieldset>

          <div className="a-card">
            <h2>{t.variants}</h2>
            <table className="a-table">
              <thead>
                <tr>
                  <th>{t.size}</th>
                  <th>{t.sku}</th>
                  <th className="a-num">{t.stock}</th>
                  {canEdit && <th />}
                </tr>
              </thead>
              <tbody>
                {f.variants.map((v, i) => (
                  <tr key={i}>
                    <td style={{ width: 70 }}>
                      <input className="a-input a-mono" value={v.size} disabled={ro || (!isNew && !!product?.variants.find((x) => x.size === v.size))} onChange={(e) => setVariant(i, { size: e.target.value.toUpperCase() })} aria-label={t.size} dir="ltr" />
                    </td>
                    <td>
                      <input className="a-input a-mono" value={v.sku ?? ''} disabled={ro} onChange={(e) => setVariant(i, { sku: e.target.value })} aria-label={t.sku} dir="ltr" />
                    </td>
                    <td style={{ width: 90 }}>
                      <input className="a-input a-num" type="number" min={0} value={v.stock} onChange={(e) => setVariant(i, { stock: Number(e.target.value) })} aria-label={`${t.stock} ${v.size}`} />
                    </td>
                    {canEdit && (
                      <td style={{ width: 40 }}>
                        <button type="button" className="a-btn a-btn-icon a-btn-sm" aria-label={t.delete} onClick={() => set('variants', f.variants.filter((_, j) => j !== i))}>
                          <Icons.trash />
                        </button>
                      </td>
                    )}
                  </tr>
                ))}
              </tbody>
            </table>
            {canEdit && (
              <button type="button" className="a-btn a-btn-sm" style={{ marginTop: 12 }} onClick={() => set('variants', [...f.variants, { size: '', sku: '', stock: 0 }])}>
                {t.addSize}
              </button>
            )}
          </div>
        </div>
      </div>
    </form>
  )
}

function Images({ product, readOnly }: { product: AdminProduct; readOnly: boolean }) {
  const { t } = useT()
  const qc = useQueryClient()
  const input = useRef<HTMLInputElement>(null)
  const [over, setOver] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const images = product.images

  const update = (p: AdminProduct) => {
    qc.setQueryData(['admin', 'product', p.id], p)
    qc.invalidateQueries({ queryKey: ['admin', 'products'] })
    qc.invalidateQueries({ queryKey: ['products'] })
  }

  const upload = async (files: FileList | File[]) => {
    setError(null)
    setBusy(true)
    try {
      let current = product
      for (const file of Array.from(files)) {
        // first free slot in gallery order, then extras as "detail"
        const used = new Set(current.images.map((i) => i.kind))
        const kind = KINDS.find((k) => !used.has(k)) ?? 'detail'
        current = await adminApi.uploadImage(product.id, file, kind)
        update(current)
      }
    } catch (e) {
      setError(errText(e, t))
    } finally {
      setBusy(false)
    }
  }

  const run = async (fn: () => Promise<AdminProduct>) => {
    setError(null)
    try {
      update(await fn())
    } catch (e) {
      setError(errText(e, t))
    }
  }

  const move = (i: number, d: -1 | 1) => {
    const ids = images.map((x) => x.id)
    const j = i + d
    if (j < 0 || j >= ids.length) return
    ;[ids[i], ids[j]] = [ids[j], ids[i]]
    run(() => adminApi.reorderImages(product.id, ids))
  }

  return (
    <div className="a-form">
      <p className="a-faint" style={{ margin: 0, fontSize: 12 }}>
        {t.imagesHint}
      </p>
      {images.length > 0 && (
        <div className="a-images">
          {images.map((img, i) => (
            <div className="a-img" key={img.id}>
              <figure>
                <img src={assetUrl(img.url)} alt={img.alt.en} />
              </figure>
              <select className="a-input" style={{ height: 32, fontSize: 12 }} value={img.kind} disabled={readOnly} onChange={(e) => run(() => adminApi.updateImage(product.id, img.id, e.target.value, img.alt))} aria-label={t.slot[img.kind]}>
                {KINDS.map((k) => (
                  <option key={k} value={k}>
                    {t.slot[k]}
                  </option>
                ))}
              </select>
              {!readOnly && (
                <div className="a-img-tools">
                  <button type="button" className="a-btn a-btn-icon a-btn-sm" aria-label={t.moveUp} disabled={i === 0} onClick={() => move(i, -1)}>
                    <span style={{ display: 'inline-block', transform: 'rotate(-90deg)' }}><Icons.up /></span>
                  </button>
                  <button type="button" className="a-btn a-btn-icon a-btn-sm" aria-label={t.moveDown} disabled={i === images.length - 1} onClick={() => move(i, 1)}>
                    <span style={{ display: 'inline-block', transform: 'rotate(-90deg)' }}><Icons.down /></span>
                  </button>
                  <button type="button" className="a-btn a-btn-icon a-btn-sm a-btn-danger" style={{ marginInlineStart: 'auto' }} aria-label={t.delete} onClick={() => window.confirm(t.confirmDelete) && run(() => adminApi.deleteImage(product.id, img.id))}>
                    <Icons.trash />
                  </button>
                </div>
              )}
            </div>
          ))}
        </div>
      )}
      {!readOnly && (
        <div
          className="a-drop"
          data-over={over}
          role="button"
          tabIndex={0}
          onClick={() => input.current?.click()}
          onKeyDown={(e) => (e.key === 'Enter' || e.key === ' ') && input.current?.click()}
          onDragOver={(e) => {
            e.preventDefault()
            setOver(true)
          }}
          onDragLeave={() => setOver(false)}
          onDrop={(e) => {
            e.preventDefault()
            setOver(false)
            if (e.dataTransfer.files.length) upload(e.dataTransfer.files)
          }}
        >
          <Icons.upload />
          {busy ? t.saving : t.dropHere}
          <input ref={input} type="file" accept="image/jpeg,image/png,image/webp,image/avif" multiple hidden onChange={(e) => e.target.files && upload(e.target.files)} />
        </div>
      )}
      {error && <p className="a-error">{error}</p>}
    </div>
  )
}
