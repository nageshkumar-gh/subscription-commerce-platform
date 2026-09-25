import { createContext, useContext, useMemo, useState, type ReactNode } from 'react'
import type { EsimPlan, Product } from '../types'

type CartContextValue = {
  product: Product | null
  plan: EsimPlan | null
  selectProduct: (product: Product, plan: EsimPlan) => void
  clearCart: () => void
}

const CartContext = createContext<CartContextValue | undefined>(undefined)

export function CartProvider({ children }: { children: ReactNode }) {
  const [product, setProduct] = useState<Product | null>(null)
  const [plan, setPlan] = useState<EsimPlan | null>(null)
  const value = useMemo(() => ({
    product,
    plan,
    selectProduct: (nextProduct: Product, nextPlan: EsimPlan) => { setProduct(nextProduct); setPlan(nextPlan) },
    clearCart: () => { setProduct(null); setPlan(null) },
  }), [product, plan])
  return <CartContext.Provider value={value}>{children}</CartContext.Provider>
}

// eslint-disable-next-line react-refresh/only-export-components
export function useCart() {
  const context = useContext(CartContext)
  if (!context) throw new Error('useCart must be used inside CartProvider')
  return context
}
