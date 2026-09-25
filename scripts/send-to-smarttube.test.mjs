import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import vm from 'node:vm';

const script = readFileSync(new URL('./send-to-smarttube.user.js', import.meta.url), 'utf8');

test('places the TV button after Share and reuses the saved pairing', async () => {
  const calls = [];
  const storage = new Map();
  const alerts = [];
  const events = new Map();
  let promptCount = 0;
  let click;
  let row;
  let button;
  let textRemoved = false;
  let iconClass = false;
  const svg = { innerHTML: '' };
  const shareButton = {
    cloneNode() {
      button = {
        style: {},
        classList: { replace(from, to) { iconClass = from.endsWith('IconLeading') && to.endsWith('IconButton'); } },
        querySelector(selector) {
          if (selector === 'svg') return svg;
          if (selector === '.ytSpecButtonShapeNextButtonTextContent') return { remove() { textRemoved = true; } };
          return null;
        },
        setAttribute(name, value) { this[name] = value; },
        addEventListener(type, handler) { if (type === 'click') click = handler; },
        remove() { if (this.parentElement) this.parentElement.children.pop(); this.parentElement = null; },
      };
      return button;
    },
  };
  const share = {
    tagName: 'YT-BUTTON-VIEW-MODEL',
    querySelector: () => shareButton,
    after(element) {
      row.children = [share, element];
      element.parentElement = row;
      element.previousElementSibling = share;
    },
  };
  const makeRow = () => ({ children: [share], getBoundingClientRect: () => ({ width: 250 }) });
  row = makeRow();
  let visibleRows = [row];
  const observer = { observe() {}, disconnect() {}, callback: null };
  const payload = JSON.stringify([[0, ['c', 'session']], [1, ['S', 'google-session']]]);
  const responses = {
    get_screen: JSON.stringify({ screen: { screenId: 'screen-1' } }),
    get_lounge_token_batch: JSON.stringify({ screens: [{ loungeToken: 'token-1' }] }),
    get_screen_availability: JSON.stringify({ screens: [{ status: 'online' }] }),
    bind: `${payload.length}\n${payload}`,
  };
  const context = {
    document: {
      documentElement: { lang: 'en' },
      head: { append() {} },
      body: {},
      getElementById(id) { return id === 'smarttube-sent-to-tv' && button?.parentElement ? button : null; },
      querySelectorAll() { return visibleRows; },
      createElement() { return { textContent: '' }; },
      addEventListener(name, handler) { events.set(name, handler); },
    },
    location: { hostname: 'www.youtube.com', pathname: '/watch', href: 'https://www.youtube.com/watch?v=eBn4-FPEOOo' },
    localStorage: {
      getItem: key => storage.get(key),
      setItem: (key, value) => storage.set(key, value),
      removeItem: key => storage.delete(key),
    },
    async fetch(input, options) {
      const url = new URL(input);
      const path = url.pathname.split('/').at(-1);
      calls.push({ path, body: new URLSearchParams(options.body) });
      return { ok: true, text: async () => responses[path] };
    },
    MutationObserver: class { constructor(callback) { observer.callback = callback; } observe(...args) { observer.observe(...args); } disconnect() {} },
    prompt() { promptCount++; return '123456789012'; },
    alert: message => alerts.push(message),
    setTimeout() {},
    clearTimeout() {},
    URL,
    URLSearchParams,
    AbortSignal,
  };

  vm.runInNewContext(script, context);
  assert.equal(button.parentElement, row);
  assert.equal(button.previousElementSibling, share);
  assert.equal(button.style.marginLeft, '8px');
  assert.equal(button['aria-label'], 'Send to SmartTube');
  assert.equal(textRemoved, true);
  assert.equal(iconClass, true);
  assert.match(svg.innerHTML, /<path/);

  await click({ shiftKey: false, stopPropagation() {} });
  assert.equal(promptCount, 1);
  assert.equal(storage.get('smarttube-screen-id'), 'screen-1');
  assert.deepEqual(calls.map(call => call.path), ['get_screen', 'get_lounge_token_batch', 'get_screen_availability', 'bind', 'bind']);
  assert.equal(calls.at(-1).body.get('req0_videoId'), 'eBn4-FPEOOo');
  assert.equal(button['aria-label'], 'Sent');

  calls.length = 0;
  await click({ shiftKey: false, stopPropagation() {} });
  assert.equal(promptCount, 1);
  assert.equal(calls[0].path, 'get_lounge_token_batch');
  assert.deepEqual(alerts, []);

  row.children = [share];
  button.parentElement = null;
  button.previousElementSibling = null;
  observer.callback();
  assert.equal(button.parentElement, row);

  visibleRows = [];
  context.location.pathname = '/';
  events.get('yt-navigate-finish')();
  assert.equal(button.parentElement, null);

  row = makeRow();
  visibleRows = [row];
  context.location.pathname = '/watch';
  events.get('yt-navigate-finish')();
  assert.equal(button.parentElement, row);
});
