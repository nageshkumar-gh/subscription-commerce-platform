import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { App } from '../App'

beforeEach(() => {
  window.history.pushState({}, '', '/')
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
  localStorage.clear()
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
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ accessToken: 'test-token', tokenType: 'Bearer', expiresIn: 3600, customer }), { status: 201, headers: { 'Content-Type': 'application/json' } }))
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
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ accessToken: 'test-token', tokenType: 'Bearer', expiresIn: 3600, customer }), { status: 201, headers: { 'Content-Type': 'application/json' } }))
      .mockResolvedValueOnce(new Response(JSON.stringify(updated), { status: 200, headers: { 'Content-Type': 'application/json' } }))
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
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ accessToken: 'test-token', tokenType: 'Bearer', expiresIn: 3600, customer }), { status: 201, headers: { 'Content-Type': 'application/json' } }))
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
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
})
