import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { api } from '../api/client'
import { StateMessage } from '../components/StateMessage'
import { useAuth } from '../contexts/AuthContext'
import { useCart } from '../contexts/CartContext'
import type { Order } from '../types'

export function PaymentPage() {
  const { product, plan, clearCart } = useCart()
  const { user } = useAuth()
  const navigate = useNavigate()
  const [cardName, setCardName] = useState('')
  const [cardNumber, setCardNumber] = useState('')
  const [error, setError] = useState('')
  const [paying, setPaying] = useState(false)
  if (!product || !plan || !user) return <StateMessage title="Payment unavailable" tone="error"><Link to="/products">Return to products</Link></StateMessage>

  async function pay(event: FormEvent) {
    event.preventDefault()
    setError('')
    if (cardName.trim().length < 2 || cardNumber.replace(/\s/g, '').length !== 16) return setError('Enter a cardholder name and a 16-digit test card number.')
    setPaying(true)
    try {
      const order: Order = await api.createOrder(product!, plan!, user!)
      await api.takePayment(order)
      clearCart()
      navigate('/order-confirmation', { replace: true, state: { order } })
    } catch {
      setError('Payment simulation failed. No charge was made. Please try again.')
      setPaying(false)
    }
  }

  return (
    <section className="auth-card">
      <p className="eyebrow">Demo checkout</p><h1>Simulated payment</h1>
      <p>No real card data is sent or stored. Use any 16 digits.</p>
      {error && <StateMessage title="Check your details" tone="error">{error}</StateMessage>}
      <form onSubmit={pay} noValidate>
        <label>Cardholder name<input value={cardName} onChange={(event) => setCardName(event.target.value)} autoComplete="cc-name" required /></label>
        <label>Test card number<input inputMode="numeric" value={cardNumber} onChange={(event) => setCardNumber(event.target.value.replace(/[^0-9 ]/g, ''))} autoComplete="cc-number" placeholder="4242 4242 4242 4242" required /></label>
        <button className="button" disabled={paying}>{paying ? 'Simulating payment…' : `Pay €${(product.price + plan.monthlyPrice).toFixed(2)}`}</button>
      </form>
    </section>
  )
}
