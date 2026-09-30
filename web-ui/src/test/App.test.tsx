import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { App } from '../App'

const products = [
  { id: 'phone-1', sku: 'IPHONE-18-PRO-512', name: 'iPhone 18 Pro', description: 'Pro phone', storage: '512 GB', price: 899, finish: 'Burgundy', features: ['OLED display'], active: true },
  { id: 'phone-2', sku: 'IPHONE-18-PRO-MAX-1TB', name: 'iPhone 18 Pro Max', description: 'Largest Pro phone', storage: '1 TB', price: 1299, finish: 'Burgundy', features: ['Long battery'], active: true },
]
const plans = [
  { id: 'plan-1', code: 'LIMITED-2GB-DAY', name: 'Everyday 2 GB', description: '2 GB daily', monthlyPrice: 14.99, active: true },
  { id: 'plan-2', code: 'UNLIMITED', name: 'Unlimited', description: 'Unlimited data', monthlyPrice: 29.99, active: true },
]
const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
const catalogueFetch = vi.fn((input: RequestInfo | URL) => Promise.resolve(json(String(input).includes('esim-plans') ? plans : products)))

beforeEach(() => {
  window.history.pushState({}, '', '/')
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
  localStorage.clear()
  vi.stubGlobal('fetch', catalogueFetch)
  catalogueFetch.mockClear()
})

describe('subscription storefront', () => {
  it('shows both products and supports a single selected product', async () => {
    const user = userEvent.setup()
    render(<App />)
    await user.click(screen.getByRole('link', { name: /shop phones/i }))
    expect(await screen.findByRole('heading', { name: 'iPhone 18 Pro' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'iPhone 18 Pro Max' })).toBeInTheDocument()
    expect(screen.getByRole('img', { name: 'iPhone 18 Pro in Burgundy' })).toBeInTheDocument()

    await user.click(screen.getAllByRole('radio', { name: /unlimited/i })[1])
    await user.click(screen.getByRole('button', { name: 'Add 1 TB phone' }))
    expect(screen.getByRole('heading', { name: 'Cart' })).toBeInTheDocument()
    expect(screen.getByText('€1299.00')).toBeInTheDocument()
    expect(screen.getByText('+ €29.99/month')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Cart (1)' })).toBeInTheDocument()
  })

  it('validates registration and then allows checkout', async () => {
    const user = userEvent.setup()
    const customer = { id: 'customer-42', name: 'Taylor Customer', email: 'taylor@example.com', phone: '0871234567', active: true }
    const fetchMock = vi.fn((input: RequestInfo | URL) => {
      const path = String(input)
      if (path.includes('esim-plans')) return Promise.resolve(json(plans))
      if (path.includes('/api/products')) return Promise.resolve(json(products))
      return Promise.resolve(json({ accessToken: 'test-token', tokenType: 'Bearer', expiresIn: 3600, customer }, 201))
    })
    vi.stubGlobal('fetch', fetchMock)
    window.history.pushState({}, '', '/register')
    render(<App />)

    await user.click(screen.getByRole('button', { name: 'Create account' }))
    expect(screen.getByRole('alert')).toHaveTextContent('Enter your full name')
    await user.type(screen.getByLabelText('Full name'), 'Taylor Customer')
    await user.type(screen.getByLabelText('Email address'), 'taylor@example.com')
    await user.type(screen.getByLabelText('Phone number'), '0871234567')
    await user.type(screen.getByLabelText('Password'), 'password123')
    await user.click(screen.getByRole('button', { name: 'Create account' }))
    expect(await screen.findByRole('heading', { name: /choose your new phone/i })).toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledWith('/api/auth/register', expect.objectContaining({ method: 'POST' }))
  })

  it('redirects an unauthorized checkout visit to login', () => {
    window.history.pushState({}, '', '/checkout')
    render(<App />)
    expect(screen.getByRole('heading', { name: 'Log in' })).toBeInTheDocument()
  })

  it('updates the registered customer profile', async () => {
    const user = userEvent.setup()
    const customer = { id: 'customer-42', name: 'Taylor Customer', email: 'taylor@example.com', phone: '0871234567', active: true }
    const updated = { ...customer, name: 'Taylor Updated', phone: '0877654321' }
    const fetchMock = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      const path = String(input)
      if (path.includes('esim-plans')) return Promise.resolve(json(plans))
      if (path.includes('/api/products')) return Promise.resolve(json(products))
      if (path.includes('/api/customers/me') && init?.method === 'PUT') return Promise.resolve(json(updated))
      return Promise.resolve(json({ accessToken: 'test-token', tokenType: 'Bearer', expiresIn: 3600, customer }, 201))
    })
    vi.stubGlobal('fetch', fetchMock)
    window.history.pushState({}, '', '/register')
    render(<App />)

    await user.type(screen.getByLabelText('Full name'), customer.name)
    await user.type(screen.getByLabelText('Email address'), customer.email)
    await user.type(screen.getByLabelText('Phone number'), customer.phone)
    await user.type(screen.getByLabelText('Password'), 'password123')
    await user.click(screen.getByRole('button', { name: 'Create account' }))
    await user.click(await screen.findByRole('link', { name: 'Profile' }))

    const name = screen.getByLabelText('Full name')
    const phone = screen.getByLabelText('Phone number')
    await user.clear(name)
    await user.type(name, updated.name)
    await user.clear(phone)
    await user.type(phone, updated.phone)
    await user.click(screen.getByRole('button', { name: 'Save changes' }))

    expect(await screen.findByRole('heading', { name: 'Profile updated' })).toBeInTheDocument()
    expect(fetchMock).toHaveBeenLastCalledWith('/api/customers/me', expect.objectContaining({ method: 'PUT', headers: expect.objectContaining({ Authorization: 'Bearer test-token' }) }))
  })

  it('requires confirmation and deletes the customer profile', async () => {
    const user = userEvent.setup()
    const customer = { id: 'customer-42', name: 'Taylor Customer', email: 'taylor@example.com', phone: '0871234567', active: true }
    const fetchMock = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      const path = String(input)
      if (path.includes('esim-plans')) return Promise.resolve(json(plans))
      if (path.includes('/api/products')) return Promise.resolve(json(products))
      if (path.includes('/api/customers/me') && init?.method === 'DELETE') return Promise.resolve(new Response(null, { status: 204 }))
      return Promise.resolve(json({ accessToken: 'test-token', tokenType: 'Bearer', expiresIn: 3600, customer }, 201))
    })
    vi.stubGlobal('fetch', fetchMock)
    window.history.pushState({}, '', '/register')
    render(<App />)

    await user.type(screen.getByLabelText('Full name'), customer.name)
    await user.type(screen.getByLabelText('Email address'), customer.email)
    await user.type(screen.getByLabelText('Phone number'), customer.phone)
    await user.type(screen.getByLabelText('Password'), 'password123')
    await user.click(screen.getByRole('button', { name: 'Create account' }))
    await user.click(await screen.findByRole('link', { name: 'Profile' }))
    await user.click(screen.getByRole('button', { name: 'Delete my profile' }))
    expect(screen.getByRole('button', { name: 'Yes, delete profile' })).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Yes, delete profile' }))

    expect(await screen.findByRole('heading', { name: 'Log in' })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Profile' })).not.toBeInTheDocument()
    expect(fetchMock).toHaveBeenLastCalledWith('/api/customers/me', { method: 'DELETE', headers: { Authorization: 'Bearer test-token' } })
  })

  it('renders the not found state', () => {
    window.history.pushState({}, '', '/does-not-exist')
    render(<App />)
    expect(screen.getByRole('heading', { name: 'Page not found' })).toBeInTheDocument()
  })

  it('shows a catalogue error when product service is unavailable', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('network error')))
    window.history.pushState({}, '', '/products')
    render(<App />)
    expect(await screen.findByRole('heading', { name: 'Server error' })).toBeInTheDocument()
    expect(screen.getByText('Products and plans could not be loaded. Please try again.')).toBeInTheDocument()
  })
})
