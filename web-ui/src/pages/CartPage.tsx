import { Link } from 'react-router-dom'
import { StateMessage } from '../components/StateMessage'
import { useCart } from '../contexts/CartContext'

export function CartPage() {
  const { product, plan, clearCart } = useCart()
  if (!product || !plan) return <StateMessage title="Your cart is empty"><p>Select one phone and eSIM plan to continue.</p><Link className="button" to="/products">Browse phones</Link></StateMessage>
  return (
    <section className="narrow-page">
      <div className="page-heading"><p className="eyebrow">Your selection</p><h1>Cart</h1></div>
      <article className="summary-card row-card">
        <div><p className="eyebrow">{product.storage} · {product.finish}</p><h2>{product.name}</h2><p>{plan.name} eSIM · {plan.description}</p></div>
        <div className="price-stack"><strong>€{product.price.toFixed(2)}</strong><span>+ €{plan.monthlyPrice.toFixed(2)}/month</span></div>
      </article>
      <div className="actions"><button className="button button--quiet" onClick={clearCart}>Remove</button><Link className="button" to="/checkout">Continue to checkout</Link></div>
    </section>
  )
}
