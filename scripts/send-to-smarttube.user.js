// ==UserScript==
// @name         SmartTube sent to TV
// @version      1.0.2
// @description  Send the current YouTube video to a paired SmartTube TV
// @match        https://www.youtube.com/*
// @grant        none
// @run-at       document-idle
// @noframes
// @license      MIT
// ==/UserScript==

(() => {
  'use strict';
  if (location.hostname !== 'www.youtube.com') return;

  const API = 'https://www.youtube.com/api/lounge/';
  const SCREEN_KEY = 'smarttube-screen-id';
  const BUTTON_ID = 'smarttube-sent-to-tv';
  if (document.getElementById(BUTTON_ID)) return;
  const style = document.createElement('style');
  style.textContent = '#send-to-smarttube{display:none!important}';
  document.head.append(style);
  const label = document.documentElement.lang.startsWith('ru')
    ? { send: 'Отправить на SmartTube', wait: 'Отправляю…', sent: 'Отправлено', paired: 'ТВ подключён', code: 'На ТВ: SmartTube → Настройки → Удалённое управление → Код ТВ. Введите код:', offline: 'Откройте SmartTube на телевизоре и попробуйте снова.' }
    : { send: 'Send to SmartTube', wait: 'Sending…', sent: 'Sent', paired: 'TV paired', code: 'On TV: SmartTube → Settings → Remote control → TV code. Enter the code:', offline: 'Open SmartTube on the TV and try again.' };
  let busy = false;

  async function post(path, data, params = {}) {
    const url = new URL(API + path);
    for (const [key, value] of Object.entries(params)) url.searchParams.set(key, value);
    const response = await fetch(url.href, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams(data).toString(),
      credentials: 'omit',
      signal: AbortSignal.timeout(12000),
    });
    if (!response.ok) throw new Error(path === 'pairing/get_screen' && response.status === 404
      ? 'TV code is invalid or expired. Get a new code in SmartTube.'
      : `${path}: HTTP ${response.status}`);
    return response.text();
  }

  function sessionFrom(response) {
    const events = [];
    let offset = 0;
    while (offset < response.length) {
      while (response[offset] === '\n' || response[offset] === '\r') offset++;
      if (offset === response.length) break;
      const end = response.indexOf('\n', offset);
      const length = Number(response.slice(offset, end));
      if (end < 0 || !Number.isInteger(length) || length < 0) break;
      events.push(...JSON.parse(response.slice(end + 1, end + 1 + length)));
      offset = end + 1 + length;
    }
    const sid = events.find(event => event[1][0] === 'c')?.[1][1];
    const gsessionid = events.find(event => event[1][0] === 'S')?.[1][1];
    if (!sid || !gsessionid) throw new Error('Lounge session was not created');
    return { sid, gsessionid, aid: events.at(-1)[0] };
  }

  async function pairedScreen() {
    let screenId = localStorage.getItem(SCREEN_KEY);
    if (!screenId) {
      const code = prompt(label.code)?.replace(/\s/g, '');
      if (!code) return null;
      if (!/^\d{12}$/.test(code)) throw new Error('TV code must contain 12 digits');
      const screen = JSON.parse(await post('pairing/get_screen', { pairing_code: code })).screen;
      if (!screen?.screenId) throw new Error('TV code was not accepted');
      screenId = screen.screenId;
      localStorage.setItem(SCREEN_KEY, screenId);
    }
    const screen = JSON.parse(await post('pairing/get_lounge_token_batch', { screen_ids: screenId })).screens?.[0];
    if (!screen?.loungeToken) {
      localStorage.removeItem(SCREEN_KEY);
      throw new Error('Pairing expired. Try again to enter a new TV code.');
    }
    return { screenId, token: screen.loungeToken };
  }

  async function sendVideo() {
    const screen = await pairedScreen();
    if (!screen) return false;
    const url = new URL(location.href);
    const videoId = url.searchParams.get('v') || url.pathname.match(/^\/(?:shorts|live)\/([-\w]{11})(?:\/|$)/)?.[1];
    if (!/^[-\w]{11}$/.test(videoId || '')) return 'paired';

    const availability = JSON.parse(await post('pairing/get_screen_availability', { lounge_token: screen.token }));
    if (availability.screens?.[0]?.status !== 'online') throw new Error(label.offline);

    const name = 'SmartTube browser';
    const connect = await post('bc/bind', {
      app: 'web', 'mdx-version': '3', name, id: screen.screenId,
      device: 'REMOTE_CONTROL', capabilities: 'que,dsdtr,atp,vsp',
      magnaKey: 'cloudPairedDevice', ui: 'false',
      deviceContext: 'user_agent=browser&window_width_points=&window_height_points=&os_name=browser&ms=',
      theme: 'cl', loungeIdToken: screen.token,
    }, { RID: '1', VER: '8', CVER: '1', auth_failure_option: 'send_error' });
    const session = sessionFrom(connect);
    await post('bc/bind', { count: '1', ofs: '1', req0__sc: 'setPlaylist', req0_videoId: videoId }, {
      name, loungeIdToken: screen.token, SID: session.sid, AID: String(session.aid),
      gsessionid: session.gsessionid, device: 'REMOTE_CONTROL', app: 'youtube-desktop',
      VER: '8', v: '2', RID: '2',
    });
    return 'sent';
  }

  let button;
  let resetTimer;
  function setLabel(value) {
    button.title = value;
    button.setAttribute('aria-label', value);
  }

  async function onSend() {
    if (busy) return;
    busy = true;
    clearTimeout(resetTimer);
    setLabel(label.wait);
    try {
      const result = await sendVideo();
      setLabel(result === 'sent' ? label.sent : result === 'paired' ? label.paired : label.send);
    } catch (error) {
      setLabel(label.send);
      alert(`SmartTube: ${error.message}`);
    } finally {
      busy = false;
      if (button.title !== label.send) resetTimer = setTimeout(() => setLabel(label.send), 2500);
    }
  }

  function createButton(share) {
    const copy = share.cloneNode(true);
    const svg = copy.querySelector('svg');
    if (!svg) return null;
    button = copy;
    button.id = BUTTON_ID;
    button.type = 'button';
    button.classList.replace('ytSpecButtonShapeNextIconLeading', 'ytSpecButtonShapeNextIconButton');
    button.querySelector('.ytSpecButtonShapeNextButtonTextContent')?.remove();
    svg.innerHTML = '<path d="M3 4h18a1 1 0 0 1 1 1v12a1 1 0 0 1-1 1h-7v2h3v2H7v-2h3v-2H3a1 1 0 0 1-1-1V5a1 1 0 0 1 1-1zm1 2v10h16V6H4zm6 2 5 3-5 3V8z"/>';
    button.style.marginLeft = '8px';
    setLabel(label.send);
    button.addEventListener('click', event => {
      event.stopPropagation();
      if (event.shiftKey) localStorage.removeItem(SCREEN_KEY);
      return onSend();
    });
    return button;
  }

  let row;
  let observedTarget;
  const observer = new MutationObserver(sync);
  function sync() {
    row = [...document.querySelectorAll('ytd-watch-metadata #top-level-buttons-computed')]
      .find(element => element.getBoundingClientRect().width > 0);
    const target = row || (location.pathname === '/watch' ? document.body : null);
    if (target !== observedTarget) {
      observer.disconnect();
      observedTarget = target;
      if (target) observer.observe(target, { childList: true, subtree: true });
    }
    const share = [...(row?.children || [])].find(element => element.tagName === 'YT-BUTTON-VIEW-MODEL');
    if (!share) return button?.remove();
    const shareButton = share.querySelector('button');
    if (!shareButton || (!button && !createButton(shareButton))) return;
    if (button.parentElement !== row || button.previousElementSibling !== share) share.after(button);
  }
  document.addEventListener('yt-navigate-finish', sync);
  document.addEventListener('yt-page-data-updated', sync);
  sync();
})();
