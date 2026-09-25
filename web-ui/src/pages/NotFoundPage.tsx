import { Link } from 'react-router-dom'
import { StateMessage } from '../components/StateMessage'

export function NotFoundPage() {
  return <StateMessage title="Page not found" tone="error"><p>The page you requested does not exist.</p><Link className="button" to="/">Return home</Link></StateMessage>
}
