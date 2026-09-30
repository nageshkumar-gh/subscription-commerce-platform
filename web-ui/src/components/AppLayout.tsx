import { NavLink, Outlet } from 'react-router-dom'
import { useAuth } from '../contexts/AuthContext'
import { useCart } from '../contexts/CartContext'

export function AppLayout() {
  const { user, logout } = useAuth()
  const { product } = useCart()

  return (
    <div className="app-shell">
      <header className="site-header">
        <NavLink className="brand" to="/">Subscribely</NavLink>
        <nav aria-label="Main navigation">
          <NavLink to="/products">Products</NavLink>
          <NavLink to="/cart">Cart{product ? ' (1)' : ''}</NavLink>
          {user && <NavLink to="/orders">My orders</NavLink>}
          {user && <NavLink to="/subscription">My subscription</NavLink>}
          {user && <NavLink to="/profile">Profile</NavLink>}
        </nav>
        <div className="account-actions">
          {user ? (
            <button className="button button--quiet" onClick={logout}>Log out</button>
          ) : (
            <><NavLink to="/login">Log in</NavLink><NavLink className="button" to="/register">Register</NavLink></>
          )}
        </div>
      </header>
      <main className="page"><Outlet /></main>
      <footer>Subscription Commerce Platform · Demo experience</footer>
    </div>
  )
}
