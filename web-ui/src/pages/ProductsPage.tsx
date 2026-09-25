import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { api } from '../api/client'
import iphone512 from '../assets/iphone-18-pro-512.jpg'
import iphone1tb from '../assets/iphone-18-pro-1tb.jpg'
import { StateMessage } from '../components/StateMessage'
import { useCart } from '../contexts/CartContext'
import { esimPlans } from '../data/mockData'
import type { Product } from '../types'

export function ProductsPage() {
  const [items, setItems] = useState<Product[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [selectedPlans, setSelectedPlans] = useState<Record<string, string>>({})
  const { selectProduct } = useCart()
  const navigate = useNavigate()

  useEffect(() => {
    api.getProducts().then(setItems).catch(() => setError('Products could not be loaded. Please try again.')).finally(() => setLoading(false))
  }, [])

  if (loading) return <StateMessage title="Loading products…">Finding the best plans for you.</StateMessage>
  if (error) return <StateMessage title="Server error" tone="error">{error}</StateMessage>
  if (!items.length) return <StateMessage title="No products yet">Please check back soon.</StateMessage>

  return (
    <section>
      <div className="page-heading"><p className="eyebrow">Phones + eSIM</p><h1>Choose your new phone</h1><p>Pick a phone, then pair it with the data plan that suits you.</p></div>
      <div className="product-grid">
        {items.map((product, index) => (
          <article className="product-card phone-card" key={product.id}>
            <div className="phone-image-wrap">
              <img className="phone-image" src={index === 0 ? iphone512 : iphone1tb} alt={`${product.name} in ${product.finish}`} />
            </div>
            <p className="storage-badge">{product.storage}</p>
            <h2>{product.name}</h2>
            <p>{product.description}</p>
            <p className="price"><strong>€{product.price.toFixed(2)}</strong> one-time</p>
            <ul>{product.features.map((feature) => <li key={feature}>{feature}</li>)}</ul>
            <fieldset className="plan-picker">
              <legend>Choose an eSIM plan</legend>
              {esimPlans.map((plan) => (
                <label className="plan-option" key={plan.id}>
                  <input type="radio" name={`plan-${product.id}`} value={plan.id} checked={selectedPlans[product.id] === plan.id} onChange={() => setSelectedPlans((current) => ({ ...current, [product.id]: plan.id }))} />
                  <span><strong>{plan.name}</strong><small>{plan.description}</small></span>
                  <strong>€{plan.monthlyPrice.toFixed(2)}/mo</strong>
                </label>
              ))}
            </fieldset>
            <button className="button" disabled={!selectedPlans[product.id]} onClick={() => {
              const plan = esimPlans.find((item) => item.id === selectedPlans[product.id])
              if (plan) { selectProduct(product, plan); navigate('/cart') }
            }}>Add {product.storage} phone</button>
          </article>
        ))}
      </div>
    </section>
  )
}
