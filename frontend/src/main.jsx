import React, { useEffect, useState } from 'react';
import { createRoot } from 'react-dom/client';
import './styles.css';

const API = import.meta.env.VITE_API_URL || 'http://localhost:8080';
async function request(path, options) { const response = await fetch(`${API}${path}`, { headers: { 'Content-Type': 'application/json' }, ...options }); if (!response.ok) throw new Error(await response.text()); return response.status === 204 ? null : response.json(); }
function App() {
  const [appointments, setAppointments] = useState([]), [suggestions, setSuggestions] = useState([]), [error, setError] = useState('');
  const refresh = async () => { try { setError(''); const [a, s] = await Promise.all([request('/appointments'), request('/suggestions?status=PENDING')]); setAppointments(a); setSuggestions(s); } catch (e) { setError(e.message); } };
  useEffect(() => { refresh(); const timer = setInterval(refresh, 5000); return () => clearInterval(timer); }, []);
  const decide = async (id, status) => { try { await request(`/suggestions/${id}`, { method: 'PATCH', body: JSON.stringify({ status }) }); refresh(); } catch (e) { setError(e.message); } };
  return <main><header><div><span className="eyebrow">SLOTMIND</span><h1>Reschedule command center</h1><p>AI-assisted recovery for disrupted clinic appointments.</p></div><div className="pulse">● Live sync</div></header>
    {error && <div className="error">{error}</div>}<section className="stats"><article><b>{appointments.filter(a => a.status === 'RESCHEDULE_PENDING').length}</b><span>Needs review</span></article><article><b>{suggestions.length}</b><span>Pending suggestions</span></article><article><b>{appointments.length}</b><span>Total appointments</span></article></section>
    <section><div className="section-title"><h2>Approval queue</h2><span>Auto-refreshes every 5 seconds</span></div>{suggestions.length === 0 ? <div className="empty">No pending reschedule suggestions.</div> : suggestions.map(s => <article className="card" key={s.id}><div className="card-head"><div><span className="badge">AUTOMATIC · {s.triggerReason}</span><h3>Appointment {s.appointmentId}</h3></div><strong>{Math.round(s.confidence * 100)}% confidence</strong></div><p>{s.reasoning}</p><div className="recommendation"><div><small>Recommended slot</small><b>{s.recommendedSlotId}</b></div><div><small>Alternate provider</small><b>{s.alternateProviderId || 'Same provider'}</b></div></div><div className="actions"><button onClick={() => decide(s.id, 'REJECTED')} className="secondary">Reject</button><button onClick={() => decide(s.id, 'ACCEPTED')}>Accept recommendation</button></div></article>)}</section>
    <section><div className="section-title"><h2>Appointments</h2></div><div className="table">{appointments.map(a => <div className="row" key={a.id}><b>{a.patientName}</b><span>{a.appointmentType}</span><span>{a.providerId}</span><span>{a.slotId}</span><span className={`status ${a.status}`}>{a.status.replace('_',' ')}</span></div>)}</div></section></main>;
}
createRoot(document.getElementById('root')).render(<App />);
