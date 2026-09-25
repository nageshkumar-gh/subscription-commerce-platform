import { BrowserRouter, Route, Routes } from 'react-router-dom'
import { AppLayout } from './components/AppLayout'
import { ProtectedRoute } from './components/ProtectedRoute'
import { AuthProvider } from './contexts/AuthContext'
import { CartProvider } from './contexts/CartContext'
import { AuthPage } from './pages/AuthPages'
import { CartPage } from './pages/CartPage'
import { CheckoutPage } from './pages/CheckoutPage'
import { HomePage } from './pages/HomePage'
import { NotFoundPage } from './pages/NotFoundPage'
import { OrderConfirmationPage } from './pages/OrderConfirmationPage'
import { PaymentPage } from './pages/PaymentPage'
import { ProductsPage } from './pages/ProductsPage'
import { ProfilePage } from './pages/ProfilePage'
import { SubscriptionPage } from './pages/SubscriptionPage'

export function App() {
  return (
    <BrowserRouter>
      <AuthProvider><CartProvider>
        <Routes>
          <Route element={<AppLayout />}>
            <Route index element={<HomePage />} />
            <Route path="products" element={<ProductsPage />} />
            <Route path="cart" element={<CartPage />} />
            <Route path="login" element={<AuthPage mode="login" />} />
            <Route path="register" element={<AuthPage mode="register" />} />
            <Route element={<ProtectedRoute />}>
              <Route path="checkout" element={<CheckoutPage />} />
              <Route path="payment" element={<PaymentPage />} />
              <Route path="order-confirmation" element={<OrderConfirmationPage />} />
              <Route path="subscription" element={<SubscriptionPage />} />
              <Route path="profile" element={<ProfilePage />} />
            </Route>
            <Route path="*" element={<NotFoundPage />} />
          </Route>
        </Routes>
      </CartProvider></AuthProvider>
    </BrowserRouter>
  )
}
