import { useQuery } from '@tanstack/react-query'
import { http } from './http'
import type {
  Address,
  AddressInput,
  ContactInput,
  LineInput,
  Order,
  OrderSummary,
  PlaceOrderInput,
  PlaceOrderResult,
  Product,
  Quote,
  ReturnInput,
  ShippingZone,
  StorePage,
  StoreSettings,
  WaitlistInput,
} from './types'
import type { SessionUser } from './http'

export * from './types'
export { ApiError, assetUrl } from './http'

export const api = {
  settings: () => http.get<StoreSettings>('/settings'),
  products: () => http.get<Product[]>('/products'),
  product: (slug: string) => http.get<Product>(`/products/${encodeURIComponent(slug)}`),
  zones: () => http.get<ShippingZone[]>('/shipping-zones'),
  page: (slug: string) => http.get<StorePage>(`/pages/${encodeURIComponent(slug)}`),

  quote: (items: LineInput[], governorate?: string, couponCode?: string, email?: string) =>
    http.post<Quote>('/orders/quote', { items, governorate, couponCode, email }),
  placeOrder: (input: PlaceOrderInput) => http.post<PlaceOrderResult>('/orders', input as unknown as Record<string, unknown>),
  joinWaitlist: (input: WaitlistInput) => http.post<void>('/waitlist', input as unknown as Record<string, unknown>),
  sendContact: (input: ContactInput) => http.post<void>('/contact', input as unknown as Record<string, unknown>),
  track: (number: string, phone: string) => http.post<Order>('/orders/track', { number, phone }),
  guestCancel: (number: string, phone: string) => http.post<Order>('/orders/cancel', { number, phone }),
  guestReturn: (number: string, phone: string, r: ReturnInput) => http.post<Order>('/orders/returns', { number, phone, ...r }),
  myReturn: (number: string, r: ReturnInput) =>
    http.post<Order>(`/me/orders/${encodeURIComponent(number)}/returns`, r as unknown as Record<string, unknown>, true),

  wishlist: () => http.get<string[]>('/me/wishlist', true),
  wishAdd: (slug: string) => http.put<string[]>(`/me/wishlist/${encodeURIComponent(slug)}`),
  wishRemove: (slug: string) => http.del<string[]>(`/me/wishlist/${encodeURIComponent(slug)}`),
  wishMerge: (slugs: string[]) => http.post<string[]>('/me/wishlist/merge', { slugs }, true),

  me: () => http.get<SessionUser>('/me', true),
  updateMe: (name: string, phone: string) => http.patch<SessionUser>('/me', { name, phone }),
  changePassword: (current: string, next: string) => http.post<void>('/me/password', { current, next }, true),
  addresses: () => http.get<Address[]>('/me/addresses', true),
  addAddress: (a: AddressInput) => http.post<Address>('/me/addresses', a as unknown as Record<string, unknown>, true),
  updateAddress: (id: number, a: AddressInput) => http.put<Address>(`/me/addresses/${id}`, a as unknown as Record<string, unknown>),
  deleteAddress: (id: number) => http.del<void>(`/me/addresses/${id}`),
  myOrders: () => http.get<OrderSummary[]>('/me/orders', true),
  myOrder: (number: string) => http.get<Order>(`/me/orders/${encodeURIComponent(number)}`, true),
  cancelMyOrder: (number: string) => http.post<Order>(`/me/orders/${encodeURIComponent(number)}/cancel`, undefined, true),
}

export const useSettings = () => useQuery({ queryKey: ['settings'], queryFn: api.settings, staleTime: 60_000 })
export const useProducts = () => useQuery({ queryKey: ['products'], queryFn: api.products, staleTime: 30_000 })
export const useProduct = (slug: string) =>
  useQuery({
    queryKey: ['product', slug],
    queryFn: () => api.product(slug).catch((e) => (e?.status === 404 ? null : Promise.reject(e))),
    staleTime: 30_000,
  })
export const useZones = () => useQuery({ queryKey: ['zones'], queryFn: api.zones, staleTime: 5 * 60_000 })
export const usePage = (slug: string) =>
  useQuery({ queryKey: ['page', slug], queryFn: () => api.page(slug).catch((e) => (e?.status === 404 ? null : Promise.reject(e))) })
