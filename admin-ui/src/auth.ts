/** The signed-in operations agent. Kept in sessionStorage so it ends with the browser tab. */
export type AgentSession={token:string;email:string};

const KEY='ops-agent-session';
export const SIGNED_OUT_EVENT='ops-signed-out';

export function readSession():AgentSession|null{try{const raw=sessionStorage.getItem(KEY);return raw?JSON.parse(raw) as AgentSession:null}catch{return null}}
export function saveSession(session:AgentSession|null){try{if(session)sessionStorage.setItem(KEY,JSON.stringify(session));else sessionStorage.removeItem(KEY)}catch{/* storage unavailable: the session lasts until reload */}}

/** fetch with the agent's token; a 401 (expired or revoked) signs the agent out. */
export async function apiFetch(input:string,init:RequestInit={}):Promise<Response>{
  const session=readSession();
  const headers=new Headers(init.headers);
  if(session)headers.set('Authorization',`Bearer ${session.token}`);
  const response=await fetch(input,{...init,headers});
  if(response.status===401&&session){saveSession(null);window.dispatchEvent(new Event(SIGNED_OUT_EVENT))}
  return response;
}

/** Signs in with a customer-service account and confirms it is an operations agent. */
export async function signIn(email:string,password:string):Promise<AgentSession>{
  const login=await fetch('/api/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({email:email.trim(),password})});
  const body=await login.json().catch(()=>null) as {accessToken?:string;message?:string}|null;
  if(!login.ok||!body?.accessToken)throw new Error(login.status===401?'Email or password is incorrect.':body?.message??'Sign-in failed. Please try again.');
  const agent=await fetch('/api/auth/agent',{headers:{Authorization:`Bearer ${body.accessToken}`}});
  if(agent.status===403)throw new Error('This account is not an operations agent.');
  if(!agent.ok)throw new Error('Sign-in could not be verified. Please try again.');
  const session={token:body.accessToken,email:email.trim()};
  saveSession(session);
  return session;
}
