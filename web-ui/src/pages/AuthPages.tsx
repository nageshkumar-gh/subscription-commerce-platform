import { useState, type FormEvent } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { ApiError } from '../api/client'
import { StateMessage } from '../components/StateMessage'
import { useAuth } from '../contexts/AuthContext'

type AuthMode = 'login' | 'register'

export function AuthPage({ mode }: { mode: AuthMode }) {
  const { login, register } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [phone, setPhone] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const isRegister = mode === 'register'

  async function submit(event: FormEvent) {
    event.preventDefault()
    setError('')
    if (isRegister && name.trim().length < 2) return setError('Enter your full name.')
    if (!email.includes('@')) return setError('Enter a valid email address.')
    if (isRegister && !/^[0-9]{7,15}$/.test(phone)) return setError('Phone number must contain 7 to 15 digits.')
    if (password.length < 8) return setError('Password must be at least 8 characters.')
    setSubmitting(true)
    try {
      if (isRegister) await register({ name: name.trim(), email, phone, password })
      else await login({ email, password })
      const destination = (location.state as { from?: string } | null)?.from ?? '/products'
      navigate(destination, { replace: true })
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : 'Something went wrong. Please try again.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <section className="auth-card">
      <p className="eyebrow">{isRegister ? 'Create an account' : 'Welcome back'}</p>
      <h1>{isRegister ? 'Register' : 'Log in'}</h1>
      <p>{isRegister ? 'Register to manage your subscription.' : 'Log in to continue to your account.'}</p>
      {error && <StateMessage title="We could not continue" tone="error">{error}</StateMessage>}
      <form onSubmit={submit} noValidate>
        {isRegister && <label>Full name<input value={name} onChange={(event) => setName(event.target.value)} autoComplete="name" required /></label>}
        <label>Email address<input type="email" value={email} onChange={(event) => setEmail(event.target.value)} autoComplete="email" required /></label>
        {isRegister && <label>Phone number<input type="tel" inputMode="numeric" value={phone} onChange={(event) => setPhone(event.target.value.replace(/\D/g, ''))} autoComplete="tel" minLength={7} maxLength={15} required /></label>}
        <label>Password<input type="password" value={password} onChange={(event) => setPassword(event.target.value)} autoComplete={isRegister ? 'new-password' : 'current-password'} minLength={8} required /></label>
        <button className="button" disabled={submitting}>{submitting ? 'Please wait…' : isRegister ? 'Create account' : 'Log in'}</button>
      </form>
      <p>{isRegister ? 'Already registered?' : 'New here?'} <Link to={isRegister ? '/login' : '/register'}>{isRegister ? 'Log in' : 'Create an account'}</Link></p>
    </section>
  )
}
