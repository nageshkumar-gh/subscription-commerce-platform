import{useState,type FormEvent}from'react';
import{signIn,type AgentSession}from'../auth';

/** Operations agents sign in with their platform account; only accounts configured as agents get in. */
export function SignIn({onSignedIn}:{onSignedIn:(session:AgentSession)=>void}){
  const[email,setEmail]=useState(''),[password,setPassword]=useState(''),[busy,setBusy]=useState(false),[error,setError]=useState('');
  const submit=async(event:FormEvent)=>{
    event.preventDefault();
    if(!email.trim()||!password){setError('Enter your email and password.');return}
    setBusy(true);setError('');
    try{onSignedIn(await signIn(email,password))}catch(e){setError(e instanceof Error?e.message:'Sign-in failed.');setBusy(false)}
  };
  return <>
    <header><div><p>OPERATIONS CONSOLE</p><h1>Sign in</h1></div></header>
    <main><form className="panel sign-in" onSubmit={submit}>
      <p className="muted">Use your agent account. Access is limited to accounts set up as operations agents.</p>
      <label>Email<input type="email" autoComplete="username" value={email} onChange={e=>setEmail(e.target.value)}/></label>
      <label>Password<input type="password" autoComplete="current-password" value={password} onChange={e=>setPassword(e.target.value)}/></label>
      {error&&<p className="error" role="alert">{error}</p>}
      <button disabled={busy}>{busy?'Signing in…':'Sign in'}</button>
    </form></main>
  </>
}
