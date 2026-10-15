// Service layer: every backend call goes through here.
class ApiError extends Error {
  constructor(status, code, message) {
    super(message);
    this.status = status;
    this.code = code;
  }
}

const Api = (() => {
  const BASE = (window.ENGISH_API_BASE ?? '').replace(/\/$/, '');

  function guestId() {
    let id = null;
    try { id = localStorage.getItem('engish.guestId'); } catch (e) { /* storage blocked */ }
    if (!id) {
      id = (window.crypto && crypto.randomUUID)
        ? crypto.randomUUID()
        : 'g-' + Date.now().toString(36) + Math.random().toString(36).slice(2, 12);
      try { localStorage.setItem('engish.guestId', id); } catch (e) { /* ignore */ }
    }
    return id;
  }

  async function request(method, path, body) {
    let res;
    try {
      res = await fetch(BASE + path, {
        method,
        headers: { 'Content-Type': 'application/json', 'X-Guest-Id': guestId() },
        body: body === undefined ? undefined : JSON.stringify(body),
      });
    } catch (e) {
      throw new ApiError(0, 'NETWORK', `Cannot reach the Engish backend. Is it running?`);
    }
    const text = await res.text();
    let data = null;
    if (text) { try { data = JSON.parse(text); } catch (e) { /* non-JSON body */ } }
    if (!res.ok) {
      throw new ApiError(res.status, (data && data.code) || 'ERROR', (data && data.message) || `Request failed (${res.status}).`);
    }
    return data;
  }

  return {
    scenarios: () => request('GET', '/api/scenarios'),
    scenario: (id) => request('GET', `/api/scenarios/${id}`),
    styles: () => request('GET', '/api/styles'),
    createSession: (scenarioId, styleId) => request('POST', '/api/sessions', { scenarioId, styleId }),
    getSession: (id) => request('GET', `/api/sessions/${id}`),
    sendTurn: (id, message) => request('POST', `/api/sessions/${id}/turns`, { message }),
    complete: (id) => request('POST', `/api/sessions/${id}/complete`),
    results: (id) => request('GET', `/api/sessions/${id}/results`),
    createAlternative: (id, turnId, styleId) => request('POST', `/api/sessions/${id}/alternative-replies`, { turnId, styleId }),
    history: () => request('GET', '/api/sessions/history'),
  };
})();
