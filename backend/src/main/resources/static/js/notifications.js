// In-app notifications: live over SSE, with polling as the fallback. The database is the source of truth,
// so a dropped stream or missed event only delays a notification, it never loses one.
const Notifications = (() => {
  const POLL_MS = 60000, WATCHDOG_MS = 15000, STALE_MS = 70000;   // heartbeat is every 25s, so 70s of silence means a dead connection
  let items = [], unread = 0, live = false, running = false, paused = false;
  let controller = null, reconnectTimer = null, retry = 0, lastActivity = 0, tick = 0;
  let timers = [], els = null, listenersAdded = false;

  function bellIcon() {
    const ns = 'http://www.w3.org/2000/svg';
    const svg = document.createElementNS(ns, 'svg');
    [['viewBox', '0 0 24 24'], ['width', '20'], ['height', '20'], ['fill', 'none'], ['stroke', 'currentColor'],
      ['stroke-width', '2'], ['stroke-linecap', 'round'], ['stroke-linejoin', 'round'], ['aria-hidden', 'true']]
      .forEach(([k, v]) => svg.setAttribute(k, v));
    ['M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9', 'M13.73 21a2 2 0 0 1-3.46 0'].forEach((d) => {
      const p = document.createElementNS(ns, 'path');
      p.setAttribute('d', d);
      svg.append(p);
    });
    return svg;
  }

  // ---- UI
  function ensureUi() {
    if (els) return;
    const slot = document.getElementById('bell-slot');
    if (!slot) return;
    const badge = h('span', { class: 'bell-badge', hidden: true });
    const btn = h('button', { class: 'bell-btn', type: 'button', 'aria-haspopup': 'true', 'aria-expanded': 'false', 'aria-label': 'Notifications', onclick: togglePanel }, bellIcon(), badge);
    const markAll = h('button', { class: 'linklike', type: 'button', onclick: markAllRead }, 'Mark all as read');
    const list = h('div', { class: 'notif-list' });
    const panel = h('div', { class: 'notif-panel', hidden: true, role: 'region', 'aria-label': 'Notifications' },
      h('div', { class: 'notif-head' }, h('strong', null, 'Notifications'), markAll), list);
    slot.replaceChildren(btn, panel);
    els = { slot, btn, badge, panel, list, markAll };
    document.addEventListener('click', (e) => { if (els && !els.slot.contains(e.target)) closePanel(); });
    document.addEventListener('keydown', (e) => { if (e.key === 'Escape') closePanel(); });
  }
  function togglePanel() {
    const open = els.panel.hidden;
    els.panel.hidden = !open;
    els.btn.setAttribute('aria-expanded', String(open));
  }
  function closePanel() {
    if (!els) return;
    els.panel.hidden = true;
    els.btn.setAttribute('aria-expanded', 'false');
  }
  function row(n) {
    return h('button', { class: 'notif-item' + (n.read ? '' : ' unread'), type: 'button', onclick: () => openNotification(n) },
      h('span', { class: 'notif-title' }, n.title),
      n.body ? h('span', { class: 'small muted' }, n.body) : null,
      h('span', { class: 'small muted' }, fmtDate(n.createdAt)));
  }
  function render() {
    if (!els) return;
    els.badge.hidden = unread === 0;
    els.badge.textContent = unread > 9 ? '9+' : String(unread);
    els.btn.setAttribute('aria-label', unread ? `Notifications, ${unread} unread` : 'Notifications');
    els.markAll.hidden = unread === 0;
    els.list.replaceChildren(...(items.length ? items.map(row) : [h('p', { class: 'muted small notif-empty' }, 'No notifications yet.')]));
  }
  function toast(n) {
    let box = document.getElementById('toasts');
    if (!box) { box = h('div', { id: 'toasts', 'aria-live': 'polite' }); document.body.append(box); }
    const t = h('div', { class: 'toast', role: 'status' },
      h('strong', null, n.title), n.body ? h('div', { class: 'small' }, n.body) : null,
      h('div', { class: 'toast-actions' },
        h('button', { class: 'btn small primary', type: 'button', onclick: () => { t.remove(); openNotification(n); } }, 'View'),
        h('button', { class: 'btn small', type: 'button', onclick: () => t.remove() }, 'Dismiss')));
    box.append(t);
    setTimeout(() => t.remove(), 10000);
  }

  // ---- state
  async function refresh() {
    try {
      const d = await Api.notifications();
      items = d.items; unread = d.unreadCount;
      render();
      markVisibleRead();
    } catch (e) { /* quiet: the next poll or reconnect retries */ }
  }
  function markReadLocal(n) {
    if (n.read) return;
    n.read = true;
    unread = Math.max(0, unread - 1);
    Api.markNotificationRead(n.id).catch(() => {});
    render();
  }
  function markAllRead() {
    items.forEach((n) => { n.read = true; });
    unread = 0;
    Api.markAllNotificationsRead().catch(() => {});
    render();
  }
  function openNotification(n) {
    closePanel();
    markReadLocal(n);
    if (n.sessionId) location.hash = `#/results/${n.sessionId}`;
  }
  // Someone looking at a results page does not need an unread badge for it.
  function markVisibleRead() {
    const m = location.hash.match(/^#\/results\/([\w-]+)/);
    if (m) items.filter((n) => n.sessionId === m[1] && !n.read).forEach(markReadLocal);
  }
  function onIncoming(n) {
    if (items.some((x) => x.id === n.id)) return;
    items = [n, ...items].slice(0, 30);
    unread += n.read ? 0 : 1;
    render();
    const viewing = n.sessionId && location.hash === `#/results/${n.sessionId}`;
    if (viewing) markReadLocal(n); else toast(n);
  }

  // ---- stream
  function handleFrame(frame) {
    let event = 'message';
    const data = [];
    for (const line of frame.split('\n')) {
      if (!line || line.startsWith(':')) continue;       // blank or heartbeat comment
      if (line.startsWith('event:')) event = line.slice(6).trim();
      else if (line.startsWith('data:')) data.push(line.slice(5).replace(/^ /, ''));
    }
    if (event === 'notification') { try { onIncoming(JSON.parse(data.join('\n'))); } catch (e) { /* ignore a bad frame */ } }
    else if (event === 'replaced') paused = true;        // another tab took this slot: do not fight for it
  }

  async function connect() {
    if (!running || controller) return;
    const mine = new AbortController();
    controller = mine;
    try {
      const res = await Api.openStream(mine.signal);
      if (res.status === 401) { Api.logout(); window.dispatchEvent(new Event('engish:logout')); return; }
      if (!res.ok || !res.body) throw new Error('stream unavailable');
      live = true; retry = 0; lastActivity = Date.now();
      await refresh();                                    // catch up on anything missed while disconnected
      const reader = res.body.getReader();
      const decoder = new TextDecoder();
      let buf = '';
      for (;;) {
        const { value, done } = await reader.read();
        if (done) break;
        lastActivity = Date.now();
        buf += decoder.decode(value, { stream: true }).replace(/\r\n/g, '\n');
        let i;
        while ((i = buf.indexOf('\n\n')) >= 0) { handleFrame(buf.slice(0, i)); buf = buf.slice(i + 2); }
      }
    } catch (e) { /* network drop or abort: reconnect below */ }
    finally { if (controller === mine) controller = null; live = false; }
    if (!running || paused) return;
    retry += 1;
    reconnectTimer = setTimeout(connect, Math.min(30000, 1000 * 2 ** Math.min(retry, 5)) + Math.random() * 1000);
  }

  function onVisible() {
    if (!running || document.visibilityState !== 'visible') return;
    paused = false;
    refresh();
    if (!controller) { clearTimeout(reconnectTimer); retry = 0; connect(); }
  }

  // ---- lifecycle
  function start() {
    ensureUi();
    if (running) return;
    running = true;
    if (!listenersAdded) {
      listenersAdded = true;
      document.addEventListener('visibilitychange', onVisible);
      window.addEventListener('hashchange', () => { if (running) markVisibleRead(); });
    }
    refresh();
    connect();
    timers = [
      setInterval(() => { tick += 1; if (!live || tick % 5 === 0) refresh(); }, POLL_MS),                    // fallback poll
      setInterval(() => { if (live && controller && Date.now() - lastActivity > STALE_MS) controller.abort(); }, WATCHDOG_MS),
    ];
  }
  function stop() {
    if (!running && !els) return;
    running = false; live = false; paused = false; items = []; unread = 0; retry = 0;
    timers.forEach(clearInterval); timers = [];
    clearTimeout(reconnectTimer);
    if (controller) controller.abort();
    controller = null;
    if (els) els.slot.replaceChildren();
    els = null;
  }

  return { start, stop };
})();