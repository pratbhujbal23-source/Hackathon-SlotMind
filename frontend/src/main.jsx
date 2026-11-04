import React, { useEffect, useState } from 'react';
import { createRoot } from 'react-dom/client';
import './styles.css';

const API = import.meta.env.VITE_API_URL || 'http://localhost:8080';

async function request(path, options = {}) {
  const response = await fetch(`${API}${path}`, {
    headers: { 'Content-Type': 'application/json' },
    ...options
  });
  if (!response.ok) throw new Error(await response.text());
  return response.status === 204 ? null : response.json();
}

function App() {
  const [appointments, setAppointments] = useState([]);
  const [suggestions, setSuggestions] = useState([]);
  const [providers, setProviders] = useState([]);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  const [working, setWorking] = useState('');

  const refresh = async () => {
    try {
      setError('');
      const [a, s, p] = await Promise.all([
        request('/appointments'),
        request('/suggestions?status=PENDING'),
        request('/providers')
      ]);
      setAppointments(a);
      setSuggestions(s);
      setProviders(p);
    } catch (e) {
      setError(e.message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    refresh();
    const timer = setInterval(refresh, 5000);
    return () => clearInterval(timer);
  }, []);

  const decide = async (id, status) => {
    try {
      setWorking(id);
      await request(`/suggestions/${id}`, {
        method: 'PATCH',
        body: JSON.stringify({ status })
      });
      await refresh();
    } catch (e) {
      setError(e.message);
    } finally {
      setWorking('');
    }
  };

  const changeProviderStatus = async (provider) => {
    try {
      setWorking(provider.id);
      await request(`/providers/${provider.id}/status`, {
        method: 'PATCH',
        body: JSON.stringify({ status: provider.status === 'OFFLINE' ? 'AVAILABLE' : 'OFFLINE' })
      });
      await refresh();
    } catch (e) {
      setError(e.message);
    } finally {
      setWorking('');
    }
  };

  return <main>
    <header>
      <div>
        <span className="eyebrow">SLOTMIND</span>
        <h1>Reschedule command center</h1>
        <p>AI-assisted recovery for disrupted clinic appointments.</p>
      </div>
      <div className="pulse">● Live sync</div>
    </header>
    {error && <div className="error">{error}</div>}
    <section className="stats">
      <article><b>{appointments.filter(a => a.status === 'RESCHEDULE_PENDING').length}</b><span>Needs review</span></article>
      <article><b>{suggestions.length}</b><span>Pending suggestions</span></article>
      <article><b>{appointments.length}</b><span>Total appointments</span></article>
    </section>
    <section>
      <div className="section-title"><h2>Providers</h2><button className="secondary" onClick={refresh}>Refresh</button></div>
      <div className="provider-list">
        {providers.map(provider => <div className="provider" key={provider.id}>
          <div><b>{provider.name}</b><small>{provider.id} · {provider.specialty}</small></div>
          <button className={provider.status === 'OFFLINE' ? 'secondary' : ''} disabled={working === provider.id} onClick={() => changeProviderStatus(provider)}>
            {working === provider.id ? 'Updating...' : provider.status === 'OFFLINE' ? 'Set available' : 'Set offline'}
          </button>
          <span className={`status ${provider.status}`}>{provider.status}</span>
        </div>)}
      </div>
    </section>
    <section>
      <div className="section-title"><h2>Approval queue</h2><span>{loading ? 'Loading...' : 'Auto-refreshes every 5 seconds'}</span></div>
      {loading ? <div className="empty">Loading suggestions...</div> : suggestions.length === 0 ? <div className="empty">No pending reschedule suggestions.</div> : suggestions.map(s =>
        <article className="card" key={s.id}>
          <div className="card-head"><div><span className="badge">{s.source || 'RULE'} · {s.triggerReason}</span><h3>Appointment {s.appointmentId}</h3></div><strong>{Math.round(s.confidence * 100)}% confidence</strong></div>
          <p>{s.reasoning}</p>
          <div className="recommendation"><div><small>Recommended slot</small><b>{s.recommendedSlotId}</b></div><div><small>Alternate provider</small><b>{s.alternateProviderId || 'Same provider'}</b></div></div>
          <div className="actions"><button disabled={working === s.id} onClick={() => decide(s.id, 'REJECTED')} className="secondary">Reject</button><button disabled={working === s.id} onClick={() => decide(s.id, 'ACCEPTED')}>{working === s.id ? 'Saving...' : 'Accept recommendation'}</button></div>
        </article>
      )}
    </section>
    <section><div className="section-title"><h2>Appointments</h2></div><div className="table">
      {appointments.map(a => <div className="row" key={a.id}><b>{a.patientName}</b><span>{a.appointmentType}</span><span>{a.providerId}</span><span>{a.slotId}</span><span className={`status ${a.status}`}>{a.status.replace('_', ' ')}</span></div>)}
    </div></section>
  </main>;
}

createRoot(document.getElementById('root')).render(<App />);
