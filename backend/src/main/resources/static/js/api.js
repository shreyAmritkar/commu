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
  const TOKEN_KEY = 'engish.token', USER_KEY = 'engish.user';

  const store = {
    get(k) { try { return localStorage.getItem(k); } catch (e) { return null; } },
    set(k, v) { try { localStorage.setItem(k, v); } catch (e) { /* ignore */ } },
    del(k) { try { localStorage.removeItem(k); } catch (e) { /* ignore */ } },
  };
  const isLoggedIn = () => !!store.get(TOKEN_KEY);
  const user = () => { try { return JSON.parse(store.get(USER_KEY)); } catch (e) { return null; } };
  function logout() { store.del(TOKEN_KEY); store.del(USER_KEY); }

  async function request(method, path, body, requiresAuth = true) {
    const headers = { 'Content-Type': 'application/json' };
    const token = store.get(TOKEN_KEY);
    if (token) headers.Authorization = `Bearer ${token}`;
    let res;
    try {
      res = await fetch(BASE + path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
    } catch (e) {
      throw new ApiError(0, 'NETWORK', 'Cannot reach the Engish backend. Is it running?');
    }
    const text = await res.text();
    let data = null;
    if (text) { try { data = JSON.parse(text); } catch (e) { /* non-JSON body */ } }
    if (res.status === 401 && requiresAuth) {
      logout();
      window.dispatchEvent(new Event('engish:logout'));
      throw new ApiError(401, 'UNAUTHORIZED', 'Your session expired. Please sign in again.');
    }
    if (!res.ok) {
      throw new ApiError(res.status, (data && data.code) || 'ERROR', (data && data.message) || `Request failed (${res.status}).`);
    }
    return data;
  }

  function saveLogin(auth) {
    store.set(TOKEN_KEY, auth.token);
    store.set(USER_KEY, JSON.stringify(auth.user));
    return auth.user;
  }

  return {
    isLoggedIn, user, logout,
    login: async (email, password) => saveLogin(await request('POST', '/api/auth/login', { email, password }, false)),
    register: async (displayName, email, password) => saveLogin(await request('POST', '/api/auth/register', { displayName, email, password }, false)),
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
    notifications: () => request('GET', '/api/notifications'),
    markNotificationRead: (id) => request('POST', `/api/notifications/${id}/read`),
    markAllNotificationsRead: () => request('POST', '/api/notifications/read-all'),
    // fetch (not EventSource) so the Authorization header can be sent and the token never appears in a URL
    openStream: (signal) => fetch(BASE + '/api/notifications/stream', {
      headers: { Authorization: `Bearer ${store.get(TOKEN_KEY)}`, Accept: 'text/event-stream' }, signal,
    }),
  };
})();