import { Link } from 'react-router-dom'
import { StateMessage } from '../components/StateMessage'
import { useAuth } from '../contexts/AuthContext'
import { useCart } from '../contexts/CartContext'

export function CheckoutPage() {
  const { product, plan } = useCart()
  const { user } = useAuth()
  if (!product || !plan) return <StateMessage title="Nothing to check out"><Link className="button" to="/products">Choose a phone</Link></StateMessage>
  return (
    <section className="narrow-page">
      <div className="page-heading"><p className="eyebrow">Review order</p><h1>Checkout summary</h1></div>
      <dl className="summary-card details-list">
        <div><dt>Customer</dt><dd>{user?.name}</dd></div>
        <div><dt>Email</dt><dd>{user?.email}</dd></div>
        <div><dt>Phone</dt><dd>{product.name}, {product.storage}</dd></div>
        <div><dt>Phone price</dt><dd>€{product.price.toFixed(2)}</dd></div>
        <div><dt>eSIM plan</dt><dd>{plan.name} · €{plan.monthlyPrice.toFixed(2)}/month</dd></div>
        <div><dt>Plan billing</dt><dd>Monthly, cancel anytime</dd></div>
        <div className="total"><dt>Total due today</dt><dd>€{(product.price + plan.monthlyPrice).toFixed(2)}</dd></div>
      </dl>
      <p className="muted">This demo does not collect or process real payment details.</p>
      <Link className="button" to="/payment">Continue to simulated payment</Link>
    </section>
  )
}
