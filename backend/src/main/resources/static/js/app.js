// Engish frontend: plain HTML/CSS/JS single-page app with hash routing. No build step.
const appEl = document.getElementById('app');
let navId = 0;

const CATEGORY = { WORKPLACE: 'Workplace', CONFLICT: 'Conflict resolution', DIFFICULT: 'Difficult conversation', SOCIAL: 'Social', NEGOTIATION: 'Negotiation' };
const OUTCOME = { ACHIEVED: 'Objective achieved', PARTIAL: 'Partly achieved', NOT_ACHIEVED: 'Objective not achieved' };
const DIMS = [['clarity', 'Clarity'], ['tone', 'Tone'], ['empathy', 'Empathy'], ['assertiveness', 'Assertiveness'], ['strategy', 'Communication strategy'], ['effectiveness', 'Effectiveness']];
const TRAITS = ['warmth', 'confidence', 'directness', 'humor', 'empathy', 'formality'];
const MOODS = ['HOSTILE', 'DEFENSIVE', 'TENSE', 'NEUTRAL', 'RECEPTIVE', 'WARM'];
const DISCLAIMER = 'This is a suggestion inspired by a communication style, written by an AI. It is not what the real actor or character would actually say.';

// ---------------------------------------------------------------- helpers
function h(tag, props, ...kids) {
  const el = document.createElement(tag);
  for (const [k, v] of Object.entries(props || {})) {
    if (v == null || v === false) continue;
    if (k === 'class') el.className = v;
    else if (k === 'style') el.style.cssText = v;   // CSSOM is allowed by a strict CSP; setAttribute('style') is not
    else if (k.startsWith('on') && typeof v === 'function') el.addEventListener(k.slice(2).toLowerCase(), v);
    else el.setAttribute(k, v === true ? '' : v);
  }
  for (const kid of kids.flat(Infinity)) {
    if (kid == null || kid === false) continue;
    el.append(kid.nodeType ? kid : document.createTextNode(String(kid)));
  }
  return el;
}
function mount(node, token) {
  if (token !== undefined && token !== navId) return false;
  appEl.replaceChildren(node);
  return true;
}
const spinner = (label) => h('div', { class: 'loading', role: 'status' }, h('div', { class: 'spinner' }), h('span', null, label));
function errorBox(err, retry) {
  return h('div', { class: 'error', role: 'alert' }, h('strong', null, err.message || 'Something went wrong.'),
    retry ? h('div', null, h('button', { class: 'btn small', onclick: retry }, 'Try again')) : null);
}
const fmtDate = (d) => new Date(d).toLocaleString([], { dateStyle: 'medium', timeStyle: 'short' });
const moodClass = (m) => 'mood-' + String(m || '').toLowerCase();
const cap = (s) => (s || '').toLowerCase().replace(/_/g, ' ');

function moodRail(mood) {
  const idx = MOODS.indexOf(mood);
  return h('div', { class: 'rail', role: 'img', 'aria-label': 'Character mood: ' + cap(mood) },
    MOODS.map((m, i) => h('span', { class: `seg ${moodClass(m)}${i <= idx ? ' on' : ''}${i === idx ? ' cur' : ''}` })));
}
const moodLabel = (mood) => h('span', { class: `mood-label ${moodClass(mood)}` }, h('span', { class: 'dot' }), cap(mood));
function traitBars(traits) {
  return h('div', null, TRAITS.map((t) => h('div', { class: 'trait' }, h('span', null, t === 'humor' ? 'Humour' : t[0].toUpperCase() + t.slice(1)),
    h('div', { class: 'bar' }, h('span', { style: `width:${(traits[t] || 0) * 10}%` })))));
}
const pageError = (err, retry) => mount(h('div', null, h('a', { class: 'back', href: '#/' }, '‹ Back to scenarios'), errorBox(err, retry)));

// ---------------------------------------------------------------- 1. scenario library
async function renderLibrary() {
  const t = navId;
  mount(spinner('Loading scenarios…'), t);
  const scenarios = await Api.scenarios();
  let filter = 'ALL';
  const grid = h('div', { class: 'grid' });
  const chips = h('div', { class: 'chip-row' });
  const draw = () => {
    chips.replaceChildren(...['ALL', ...new Set(scenarios.map((s) => s.category))].map((c) =>
      h('button', { class: 'chip-btn', 'aria-pressed': String(filter === c), onclick: () => { filter = c; draw(); } }, c === 'ALL' ? 'All' : CATEGORY[c] || c)));
    grid.replaceChildren(...scenarios.filter((s) => filter === 'ALL' || s.category === filter).map((s) =>
      h('article', { class: `card scenario-card diff-${s.difficulty}` },
        h('div', { class: 'meta' }, h('span', { class: 'chip' }, CATEGORY[s.category] || s.category), h('span', { class: 'chip plain' }, cap(s.difficulty)), h('span', { class: 'small muted' }, `${s.maxTurns} turns`)),
        h('h3', null, s.title),
        h('p', null, s.description),
        h('p', { class: 'small muted' }, 'You talk to ', s.character ? s.character.name : 'an AI character', '.'),
        h('a', { class: 'btn primary', href: `#/setup/${s.id}` }, 'Set up this practice'))));
  };
  draw();
  mount(h('div', null, h('h1', null, 'Practice the conversation before you have it'),
    h('p', { class: 'lead' }, 'Pick a scenario, talk to an AI character who reacts to what you actually say, then get feedback and see how a different style might have handled your replies.'),
    chips, grid), t);
}

// ---------------------------------------------------------------- 2. scenario setup
async function renderSetup(id) {
  const t = navId;
  mount(spinner('Loading scenario…'), t);
  const [sc, styles] = await Promise.all([Api.scenario(id), Api.styles()]);
  let selected = styles[0].id;
  const errorSlot = h('div');
  const startBtn = h('button', { class: 'btn primary' }, 'Start conversation');
  const styleGrid = h('div', { class: 'style-grid' });
  const drawStyles = () => styleGrid.replaceChildren(...styles.map((st) =>
    h('button', { class: 'style-card', 'aria-pressed': String(st.id === selected), onclick: () => { selected = st.id; drawStyles(); } },
      h('h3', null, st.name), h('div', { class: 'by' }, 'Inspired by ', st.inspiredBy), h('p', { class: 'small' }, st.description), traitBars(st.traits))));
  drawStyles();

  async function start() {
    errorSlot.replaceChildren();
    startBtn.disabled = true;
    startBtn.textContent = 'Setting the scene…';
    const wait = h('div', { class: 'loading' }, h('div', { class: 'spinner' }), h('span', null, `${sc.character ? sc.character.name : 'The character'} is getting ready. A local model can take a little while.`));
    errorSlot.replaceChildren(wait);
    try {
      const session = await Api.createSession(sc.id, selected);
      location.hash = `#/chat/${session.id}`;
    } catch (e) {
      errorSlot.replaceChildren(errorBox(e));
      startBtn.disabled = false;
      startBtn.textContent = 'Start conversation';
    }
  }
  startBtn.addEventListener('click', start);

  mount(h('div', null, h('a', { class: 'back', href: '#/' }, '‹ All scenarios'),
    h('h1', null, sc.title), h('p', { class: 'lead' }, sc.description),
    h('div', { class: 'two-col' },
      h('section', { class: 'card' }, h('h2', null, 'The situation'),
        h('dl', { class: 'facts' }, h('dt', null, 'You'), h('dd', null, sc.userRole), h('dt', null, 'They'), h('dd', null, sc.aiRole),
          h('dt', null, 'Mood'), h('dd', null, sc.emotionalContext), h('dt', null, 'Length'), h('dd', null, `${sc.maxTurns} turns, ${cap(sc.difficulty)}`))),
      h('section', { class: 'card' }, h('h2', null, 'Your goal'), h('p', null, sc.objective), h('h3', null, 'You succeed when you…'),
        h('ul', { class: 'clean' }, sc.successCriteria.map((c) => h('li', null, c))))),
    h('h2', { style: 'margin-top:1.8rem' }, 'Choose a style for alternative replies'),
    h('p', { class: 'muted' }, 'After the conversation you can see how one of your replies might sound in this style. Your own replies are never rewritten during the chat.'),
    styleGrid, h('div', { class: 'notice', style: 'margin:1rem 0' }, DISCLAIMER),
    h('div', null, startBtn), errorSlot), t);
}

// ---------------------------------------------------------------- 3. interactive conversation
async function renderChat(id) {
  const t = navId;
  mount(spinner('Loading conversation…'), t);
  let session = await Api.getSession(id);
  let sending = false, pendingText = null, error = null;

  const head = h('div'), side = h('aside', { class: 'side' }), msgs = h('div', { class: 'messages', 'aria-live': 'polite' }),
    footer = h('div'), input = h('textarea', { rows: '2', maxlength: '1000', placeholder: 'Type what you would say…', 'aria-label': 'Your message' }),
    sendBtn = h('button', { class: 'btn primary' }, 'Send'), counter = h('div', { class: 'counter' });

  function bubble(turn) {
    const isUser = turn.speaker === 'USER';
    return h('div', { class: `msg ${isUser ? 'user' : 'char ' + moodClass(turn.mood)}` },
      h('span', { class: 'who' }, isUser ? 'You' : session.character.name), h('div', { class: 'bubble' }, turn.message));
  }
  function drawAll() {
    const active = session.status === 'ACTIVE';
    head.replaceChildren(h('a', { class: 'back', href: '#/history' }, '‹ History'),
      h('div', { class: 'chat-head' },
        h('div', null, h('h1', null, session.character.name), h('div', { class: 'muted small' }, session.character.roleTitle)),
        h('div', null, moodLabel(session.mood), moodRail(session.mood))),
      h('div', { class: 'small muted' }, `Turn ${Math.min(session.userTurnCount + (active ? 1 : 0), session.maxTurns)} of ${session.maxTurns}`),
      h('div', { class: 'progress', role: 'progressbar', 'aria-valuemin': '0', 'aria-valuemax': String(session.maxTurns), 'aria-valuenow': String(session.userTurnCount) },
        h('span', { style: `width:${(session.userTurnCount / session.maxTurns) * 100}%` })));

    const endBtn = h('button', { class: 'btn small', disabled: sending || session.userTurnCount < 1, onclick: () => (location.hash = `#/results/${session.id}`) }, 'End now and get feedback');
    side.replaceChildren(
      h('section', { class: 'card' }, h('h3', null, 'Your goal'), h('p', { class: 'small' }, session.scenario.objective),
        h('ul', { class: 'clean small' }, session.scenario.successCriteria.map((c) => h('li', null, c)))),
      h('section', { class: 'card' }, h('h3', null, 'Scene'), h('p', { class: 'small scene-note' }, session.sceneState), active ? endBtn : null));

    msgs.replaceChildren(...session.turns.map(bubble));
    if (pendingText) msgs.append(h('div', { class: 'msg user pending' }, h('span', { class: 'who' }, 'You'), h('div', { class: 'bubble' }, pendingText)));
    if (sending) msgs.append(h('div', { class: 'msg char typing', role: 'status', 'aria-label': `${session.character.name} is typing` }, h('div', { class: 'bubble' }, h('i'), h('i'), h('i'))));
    msgs.scrollTop = msgs.scrollHeight;

    if (active) {
      input.disabled = sendBtn.disabled = sending;
      sendBtn.textContent = sending ? 'Waiting…' : 'Send';
      counter.textContent = `${input.value.length} / 1000`;
      footer.replaceChildren(error ? errorBox(error) : '', h('div', { class: 'composer' }, input, sendBtn), counter);
    } else {
      const done = session.status === 'COMPLETED';
      footer.replaceChildren(h('div', { class: 'banner' },
        h('div', null, h('strong', null, done ? 'This conversation is finished.' : 'The conversation has reached its end.'),
          h('div', { class: 'small muted' }, done ? 'Your feedback is saved.' : 'Get your evaluation and see alternative replies.')),
        h('a', { class: 'btn primary', href: `#/results/${session.id}` }, done ? 'View feedback' : 'Get my feedback')));
    }
  }

  async function send() {
    const text = input.value.trim();
    if (!text || sending) return;
    sending = true; error = null; pendingText = text; input.value = '';
    drawAll();
    try {
      const updated = await Api.sendTurn(session.id, text);
      if (t !== navId) return;
      session = updated; pendingText = null;
    } catch (e) {
      if (t !== navId) return;
      input.value = text; pendingText = null; error = e;   // keep the draft so nothing is lost
      if (e.code === 'SESSION_NOT_ACTIVE' || e.code === 'TURN_CONFLICT') { try { session = await Api.getSession(session.id); } catch (_) { /* keep old state */ } }
    }
    sending = false;
    drawAll();
    if (session.status === 'ACTIVE') input.focus();
  }
  sendBtn.addEventListener('click', send);
  input.addEventListener('input', () => { counter.textContent = `${input.value.length} / 1000`; });
  input.addEventListener('keydown', (e) => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); send(); } });

  drawAll();
  mount(h('div', null, head, h('div', { class: 'chat-layout' }, h('div', null, msgs, footer), side)), t);
  if (session.status === 'ACTIVE') input.focus();
}

// ---------------------------------------------------------------- 4. evaluation results
async function loadResults(id, onStatus) {
  try {
    return await Api.results(id);
  } catch (e) {
    if (e.code !== 'RESULTS_NOT_READY') throw e;
    onStatus && onStatus();
    return Api.complete(id);          // generates the evaluation once, then it is stored
  }
}

async function renderResults(id) {
  const t = navId;
  mount(spinner('Loading results…'), t);
  let data;
  try {
    data = await loadResults(id, () => mount(spinner('Analysing your conversation. This can take a minute with a local model…'), t));
  } catch (e) {
    if (e.code === 'NOT_ENOUGH_TURNS') { location.hash = `#/chat/${id}`; return; }
    return mount(h('div', null, h('a', { class: 'back', href: `#/chat/${id}` }, '‹ Back to the conversation'), errorBox(e, () => route())), t);
  }
  const styles = await Api.styles();
  const ev = data.evaluation, d = ev.details, sess = data.session;
  let styleId = sess.style.id;
  const rows = h('div'), errSlot = h('div');
  const select = h('select', { id: 'style-select', 'aria-label': 'Alternative reply style', onchange: (e) => { styleId = Number(e.target.value); drawRows(); } },
    styles.map((s) => h('option', { value: s.id, selected: s.id === styleId }, `${s.name} (${s.inspiredBy.replace(' (fictional character)', '')})`)));

  const userTurns = sess.turns.filter((x) => x.speaker === 'USER');
  function drawRows() {
    rows.replaceChildren(...userTurns.map((turn) => {
      const existing = data.alternativeReplies.find((a) => a.turnId === turn.id && a.styleId === styleId);
      const btn = h('button', { class: `btn small${existing ? '' : ' primary'}` }, existing ? 'View comparison' : 'Generate alternative');
      btn.addEventListener('click', async () => {
        if (existing) { location.hash = `#/compare/${sess.id}/${existing.id}`; return; }
        errSlot.replaceChildren();
        rows.querySelectorAll('button').forEach((b) => (b.disabled = true));
        select.disabled = true;
        btn.textContent = 'Writing… (can take a minute)';
        try {
          const alt = await Api.createAlternative(sess.id, turn.id, styleId);
          data.alternativeReplies.push(alt);
          location.hash = `#/compare/${sess.id}/${alt.id}`;
        } catch (e) {
          errSlot.replaceChildren(errorBox(e));
          select.disabled = false; drawRows();
        }
      });
      return h('div', { class: 'turn-row' }, h('blockquote', null, turn.message), h('div', { class: 'actions' }, btn));
    }));
  }
  drawRows();

  const list = (title, items) => h('section', { class: 'card' }, h('h3', null, title), h('ul', { class: 'clean' }, items.map((i) => h('li', null, i))));
  mount(h('div', null,
    h('a', { class: 'back', href: '#/history' }, '‹ History'),
    h('h1', null, sess.scenario.title),
    h('section', { class: 'card' }, h('div', { class: 'score-head' },
      h('div', { class: 'score' }, String(ev.overallScore), h('small', null, ' / 10')),
      h('div', null, h('span', { class: 'chip' }, OUTCOME[ev.outcome] || cap(ev.outcome)), h('p', { style: 'margin-top:.6rem' }, ev.summary)))),
    h('div', { class: 'two-col', style: 'margin-top:1rem' },
      h('section', { class: 'card' }, h('h2', null, 'How you did'),
        DIMS.map(([k, label]) => { const dim = d.dimensions[k]; return h('div', { class: 'dim' },
          h('div', { class: 'row' }, h('span', null, label), h('span', null, `${dim.score}/10`)),
          h('div', { class: 'bar' }, h('span', { style: `width:${dim.score * 10}%` })), h('p', null, dim.comment)); })),
      h('div', { class: 'stack' }, list('Strengths', d.strengths), list('Where to improve', d.improvements), list('Try next time', d.suggestions))),
    h('section', { class: 'card', style: 'margin-top:1rem' }, h('h2', null, 'Compare your replies with a different style'),
      h('p', { class: 'muted' }, 'Pick a style, then choose one of your messages to see an alternative version side by side.'),
      h('div', { class: 'toolbar' }, h('label', { for: 'style-select' }, 'Style:'), select), errSlot, rows,
      h('div', { class: 'notice', style: 'margin-top:1rem' }, DISCLAIMER)),
    h('section', { class: 'card', style: 'margin-top:1rem' }, h('h2', null, 'Full transcript'),
      h('div', { class: 'stack' }, sess.turns.map((x) => h('div', null, h('strong', null, x.speaker === 'USER' ? 'You' : sess.character.name), ': ', x.message)))),
    h('div', { style: 'margin-top:1.2rem' }, h('a', { class: 'btn primary', href: '#/' }, 'Practise another scenario'))), t);
}

// ---------------------------------------------------------------- 5. original vs alternative
async function renderCompare(sessionId, altId) {
  const t = navId;
  mount(spinner('Loading comparison…'), t);
  const data = await Api.results(sessionId);
  const alt = data.alternativeReplies.find((a) => String(a.id) === String(altId));
  const back = h('a', { class: 'back', href: `#/results/${sessionId}` }, '‹ Back to results');
  if (!alt) return mount(h('div', null, back, errorBox({ message: 'That comparison could not be found.' })), t);

  const sess = data.session, turn = sess.turns.find((x) => x.id === alt.turnId);
  const before = turn ? sess.turns.filter((x) => x.seq < turn.seq && x.speaker === 'CHARACTER').pop() : null;
  const reaction = turn ? sess.turns.find((x) => x.seq === turn.seq + 1) : null;
  const style = sess.style.id === alt.styleId ? sess.style : null;
  const c = alt.comparison;

  mount(h('div', null, back,
    h('h1', null, 'Your reply vs. an alternative'),
    h('p', { class: 'lead' }, `${alt.styleName}, inspired by ${alt.inspiredBy.replace(' (fictional character)', '')}`),
    before ? h('div', { class: `context ${moodClass(before.mood)}` }, h('div', { class: 'small muted' }, `${sess.character.name} said:`), h('div', null, before.message)) : null,
    h('div', { class: 'compare' },
      h('section', { class: 'card orig' }, h('h3', null, 'Your reply'), h('blockquote', null, alt.originalMessage)),
      h('section', { class: 'card alt' }, h('h3', null, 'Alternative'), h('blockquote', null, alt.alternativeMessage))),
    reaction ? h('div', { class: `context ${moodClass(reaction.mood)}`, style: 'margin-top:1rem' }, h('div', { class: 'small muted' }, `How ${sess.character.name} actually reacted to your reply:`), h('div', null, reaction.message)) : null,
    h('div', { class: 'explain' },
      [['Wording', c.wording], ['Tone', c.tone], ['Strategy', c.strategy], ['Likely effect', c.likelyEffect]].map(([title, body]) =>
        h('section', { class: 'card' }, h('h3', null, title), h('p', null, body)))),
    h('div', { class: 'takeaway' }, c.keyTakeaway),
    style ? h('div', { class: 'card', style: 'margin-top:1rem' }, h('h3', null, 'Style traits'), traitBars(style.traits)) : null,
    h('div', { class: 'notice', style: 'margin-top:1rem' }, DISCLAIMER)), t);
}

// ---------------------------------------------------------------- 6. session history
async function renderHistory() {
  const t = navId;
  mount(spinner('Loading your sessions…'), t);
  const list = await Api.history();
  if (!list.length) return mount(h('div', { class: 'empty' }, h('h2', null, 'No practice sessions yet'), h('p', null, 'Your conversations will show up here.'), h('a', { class: 'btn primary', href: '#/' }, 'Choose a scenario')), t);
  const stateText = { ACTIVE: 'In progress', FINISHED: 'Awaiting feedback', COMPLETED: 'Completed' };
  mount(h('div', null, h('h1', null, 'Your sessions'), h('p', { class: 'muted' }, 'Saved for this browser. Guest sessions are tied to this device.'),
    list.map((s) => h('div', { class: 'card history-row' },
      h('div', null, h('h3', { style: 'margin:0' }, s.scenarioTitle),
        h('div', { class: 'small muted' }, `with ${s.characterName}, ${fmtDate(s.createdAt)}`),
        h('div', { class: 'chip-row', style: 'margin:.5rem 0 0' }, h('span', { class: 'chip' }, stateText[s.status] || s.status),
          h('span', { class: 'chip plain' }, `${s.userTurnCount}/${s.maxTurns} turns`),
          s.overallScore != null ? h('span', { class: 'chip plain' }, `Score ${s.overallScore}/10`) : null)),
      h('a', { class: 'btn', href: s.status === 'ACTIVE' ? `#/chat/${s.id}` : `#/results/${s.id}` }, s.status === 'ACTIVE' ? 'Continue' : s.status === 'FINISHED' ? 'Get feedback' : 'View results')))), t);
}

// ---------------------------------------------------------------- login / register
function renderAuth(mode) {
  const t = navId;
  const isLogin = mode === 'login';
  const err = h('div');
  const btn = h('button', { class: 'btn primary', type: 'submit' }, isLogin ? 'Sign in' : 'Create account');
  const name = h('input', { id: 'name', type: 'text', required: true, maxlength: '80', autocomplete: 'name' });
  const email = h('input', { id: 'email', type: 'email', required: true, maxlength: '254', autocomplete: 'email' });
  const pw = h('input', { id: 'pw', type: 'password', required: true, minlength: isLogin ? null : '8', maxlength: '72', autocomplete: isLogin ? 'current-password' : 'new-password' });

  const form = h('form', { class: 'card auth-card', novalidate: false, onsubmit: async (e) => {
    e.preventDefault();
    err.replaceChildren();
    btn.disabled = true;
    try {
      if (isLogin) await Api.login(email.value, pw.value);
      else await Api.register(name.value, email.value, pw.value);
      let back = null;
      try { back = sessionStorage.getItem('engish.returnTo'); sessionStorage.removeItem('engish.returnTo'); } catch (x) { /* ignore */ }
      location.hash = back && !back.startsWith('#/login') && !back.startsWith('#/register') ? back : '#/';
    } catch (ex) {
      err.replaceChildren(errorBox(ex));
      btn.disabled = false;
    }
  } },
    h('h1', null, isLogin ? 'Welcome back' : 'Create your account'),
    h('p', { class: 'muted' }, isLogin ? 'Sign in to continue practising.' : 'Your sessions and feedback are saved to your account.'),
    isLogin ? null : [h('label', { for: 'name' }, 'Name'), name],
    h('label', { for: 'email' }, 'Email'), email,
    h('label', { for: 'pw' }, 'Password' + (isLogin ? '' : ' (at least 8 characters)')), pw,
    err, h('div', { style: 'margin-top:1rem' }, btn),
    h('p', { class: 'small muted', style: 'margin-top:1rem' }, isLogin ? 'New here? ' : 'Already have an account? ',
      h('a', { href: isLogin ? '#/register' : '#/login' }, isLogin ? 'Create an account' : 'Sign in')));
  mount(form, t);
}

function renderNav() {
  const u = Api.user();
  const nav = document.querySelector('nav');
  nav.hidden = !Api.isLoggedIn();
  document.getElementById('userbox').replaceChildren(...(u ? [
    h('span', { class: 'muted small' }, u.displayName),
    h('button', { class: 'linklike', onclick: () => { Api.logout(); location.hash = '#/login'; } }, 'Log out'),
  ] : []));
}

// ---------------------------------------------------------------- router
const routes = [
  [/^#\/?$/, renderLibrary], [/^#\/setup\/(\d+)$/, renderSetup], [/^#\/chat\/([\w-]+)$/, renderChat],
  [/^#\/results\/([\w-]+)$/, renderResults], [/^#\/compare\/([\w-]+)\/(\d+)$/, renderCompare], [/^#\/history$/, renderHistory],
  [/^#\/login$/, () => renderAuth('login')], [/^#\/register$/, () => renderAuth('register')],
];
const PUBLIC_ROUTES = ['#/login', '#/register'];

async function route() {
  const hash = location.hash || '#/';
  const isPublic = PUBLIC_ROUTES.includes(hash);
  if (!Api.isLoggedIn() && !isPublic) {            // route guard
    try { sessionStorage.setItem('engish.returnTo', hash); } catch (e) { /* ignore */ }
    location.hash = '#/login';
    return;
  }
  if (Api.isLoggedIn() && isPublic) { location.hash = '#/'; return; }

  navId += 1;
  const t = navId;
  renderNav();
  document.querySelectorAll('nav a').forEach((a) => a.classList.toggle('active',
    (a.dataset.nav === 'history' && hash.startsWith('#/history')) || (a.dataset.nav === 'library' && !hash.startsWith('#/history'))));
  window.scrollTo(0, 0);
  for (const [re, fn] of routes) {
    const m = hash.match(re);
    if (!m) continue;
    try { await fn(...m.slice(1)); } catch (e) { if (t === navId) pageError(e, route); }
    return;
  }
  mount(h('div', { class: 'empty' }, h('h2', null, 'Page not found'), h('a', { class: 'btn primary', href: '#/' }, 'Back to scenarios')));
}
window.addEventListener('hashchange', route);
window.addEventListener('engish:logout', () => { if (location.hash === '#/login') route(); else location.hash = '#/login'; });
route();
