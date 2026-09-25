import { useState, type FormEvent } from 'react'
import { ApiError } from '../api/client'
import { StateMessage } from '../components/StateMessage'
import { useAuth } from '../contexts/AuthContext'

export function ProfilePage() {
  const { user, updateProfile, deleteProfile } = useAuth()
  const [name, setName] = useState(user?.name ?? '')
  const [email, setEmail] = useState(user?.email ?? '')
  const [phone, setPhone] = useState(user?.phone ?? '')
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [confirmingDelete, setConfirmingDelete] = useState(false)

  async function save(event: FormEvent) {
    event.preventDefault()
    setError('')
    setSuccess('')
    if (name.trim().length < 2) return setError('Enter your full name.')
    if (!email.includes('@')) return setError('Enter a valid email address.')
    if (!/^[0-9]{7,15}$/.test(phone)) return setError('Phone number must contain 7 to 15 digits.')
    setSubmitting(true)
    try {
      await updateProfile({ name: name.trim(), email: email.trim(), phone })
      setSuccess('Your profile has been updated.')
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : 'Something went wrong. Please try again.')
    } finally {
      setSubmitting(false)
    }
  }

  async function removeProfile() {
    setError('')
    setSubmitting(true)
    try {
      await deleteProfile()
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : 'Something went wrong. Please try again.')
      setSubmitting(false)
      setConfirmingDelete(false)
    }
  }

  return (
    <section className="profile-page narrow-page">
      <div className="page-heading">
        <p className="eyebrow">Your account</p>
        <h1>Profile</h1>
        <p>Keep your contact details up to date.</p>
      </div>

      {error && <StateMessage title="We could not save your changes" tone="error">{error}</StateMessage>}
      {success && <StateMessage title="Profile updated" tone="success">{success}</StateMessage>}

      <form className="summary-card" onSubmit={save} noValidate>
        <label>Full name<input value={name} onChange={(event) => setName(event.target.value)} autoComplete="name" required /></label>
        <label>Email address<input type="email" value={email} onChange={(event) => setEmail(event.target.value)} autoComplete="email" required /></label>
        <label>Phone number<input type="tel" inputMode="numeric" value={phone} onChange={(event) => setPhone(event.target.value.replace(/\D/g, ''))} autoComplete="tel" minLength={7} maxLength={15} required /></label>
        <button className="button" disabled={submitting}>{submitting ? 'Saving…' : 'Save changes'}</button>
      </form>

      <section className="danger-zone" aria-labelledby="delete-profile-heading">
        <h2 id="delete-profile-heading">Delete profile</h2>
        <p>This permanently removes your customer profile. This action cannot be undone.</p>
        {!confirmingDelete ? (
          <button className="button button--danger" type="button" onClick={() => setConfirmingDelete(true)}>Delete my profile</button>
        ) : (
          <div className="delete-confirmation" role="group" aria-label="Confirm profile deletion">
            <p><strong>Are you sure you want to permanently delete your profile?</strong></p>
            <div className="actions">
              <button className="button button--quiet" type="button" disabled={submitting} onClick={() => setConfirmingDelete(false)}>Cancel</button>
              <button className="button button--danger" type="button" disabled={submitting} onClick={removeProfile}>{submitting ? 'Deleting…' : 'Yes, delete profile'}</button>
            </div>
          </div>
        )}
      </section>
    </section>
  )
}
