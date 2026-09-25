import { Link } from 'react-router-dom'

export function HomePage() {
  return (
    <section className="hero">
      <p className="eyebrow">Your phone. Your data.</p>
      <h1>A new phone and eSIM, made simple.</h1>
      <p>Choose 512 GB or 1 TB, add the data plan that fits your life, and track everything from activation to delivery.</p>
      <Link className="button" to="/products">Shop phones</Link>
    </section>
  )
}
