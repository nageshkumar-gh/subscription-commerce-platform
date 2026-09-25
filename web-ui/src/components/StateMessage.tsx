import type { ReactNode } from 'react'

type Props = {
  title: string
  children?: ReactNode
  tone?: 'neutral' | 'success' | 'error'
}

export function StateMessage({ title, children, tone = 'neutral' }: Props) {
  return (
    <section className={`state-message state-message--${tone}`} role={tone === 'error' ? 'alert' : 'status'}>
      <h2>{title}</h2>
      {children && <div>{children}</div>}
    </section>
  )
}
