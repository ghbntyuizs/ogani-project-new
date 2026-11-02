(function () {
  'use strict';
  const base = (window.OGANI_API_BASE || '').replace(/\/$/, '');
  const key = 'ogani.admin.session';
  const ownerKey = key + '.owner';
  let refreshing = null;
  let ownership = null, releaseOwnership = null;
  function session() {
    try { return JSON.parse(sessionStorage.getItem(key)) || null; }
    catch (_) { sessionStorage.removeItem(key); return null; }
  }
  function setSession(value) {
    if (value) {
      sessionStorage.setItem(key, JSON.stringify(value));
      if (!sessionStorage.getItem(ownerKey)) sessionStorage.setItem(ownerKey,
        window.crypto?.randomUUID?.() || (Date.now() + '-' + Math.random()));
    } else {
      sessionStorage.removeItem(key); sessionStorage.removeItem(ownerKey);
      releaseOwnership?.(); releaseOwnership = null; ownership = null;
    }
  }
  async function ensureOwnership() {
    // Duplicating a tab copies sessionStorage. Give each refresh token exactly one
    // live page owner; a copied tab signs in separately instead of racing rotation.
    if (!session() || !window.navigator?.locks) return;
    if (!ownership) {
      if (!sessionStorage.getItem(ownerKey)) setSession(session());
      ownership = new Promise((resolve, reject) => {
        window.navigator.locks.request('ogani-session-' + sessionStorage.getItem(ownerKey),
          { ifAvailable: true }, lock => {
            if (!lock) { resolve(false); return; }
            resolve(true);
            return new Promise(release => { releaseOwnership = release; });
          }).catch(reject);
      });
    }
    if (!await ownership) {
      setSession(null);
      throw new Error('This session is active in another tab. Please sign in separately.');
    }
  }
  async function send(path, options = {}, token) {
    const headers = new Headers(options.headers);
    if (token) headers.set('Authorization', 'Bearer ' + token);
    let body = options.body;
    if (body != null && !(body instanceof FormData)) {
      headers.set('Content-Type', 'application/json');
      body = JSON.stringify(body);
    }
    let response;
    try { response = await fetch(base + path, { ...options, body, headers, signal: options.signal || AbortSignal.timeout(15000) }); }
    catch (_) { throw new Error('Cannot reach the API. Check your connection and API configuration.'); }
    let payload;
    try { payload = await response.json(); }
    catch (_) { throw new Error('The API returned an invalid response.'); }
    if (!response.ok) {
      const details = Array.isArray(payload.details) ? payload.details.map(d => d.field + ': ' + d.message).join('; ') : '';
      const error = new Error([payload.message || ('Request failed (' + response.status + ')'), details].filter(Boolean).join(' — '));
      error.status = response.status;
      throw error;
    }
    return payload.data;
  }
  async function rotate() {
    if (!refreshing) {
      refreshing = (async () => {
        const current = session();
        if (!current?.refreshToken) throw new Error('Please sign in again.');
        try {
          const next = await send('/api/auth/refresh', { method: 'POST', body: { refreshToken: current.refreshToken } });
          // Logout in another pending operation must not resurrect the session.
          if (!session()) throw new Error('Please sign in again.');
          setSession(next); return next;
        } catch (error) { if (error.status === 401 || error.status === 403) setSession(null); throw error; }
      })().finally(() => { refreshing = null; });
    }
    return refreshing;
  }
  async function request(path, options = {}) {
    await ensureOwnership();
    const token = session()?.accessToken;
    try { return await send(path, options, token); }
    catch (error) {
      if (error.status !== 401 || !token) throw error;
      try {
        // Another request may have finished refreshing while this response was in flight.
        const current = session();
        const next = current?.accessToken !== token && current ? current : await rotate();
        return await send(path, options, next.accessToken);
      } catch (retryError) {
        if (retryError.status === 401) setSession(null);
        throw retryError;
      }
    }
  }
  function imageUrl(value, thumbnail = false) {
    if (!value) return '';
    try {
      // Legacy seed images were authored for the missing storefront.
      if (value.startsWith('img/')) return 'assets/images/ogani/' + value.slice(4).replace('product/details/', 'details/');
      const url = new URL(value.startsWith('/api/images/') ? base + value : value, location.href);
      if (thumbnail && url.pathname.startsWith('/api/images/') && url.origin === new URL(base || location.href, location.href).origin)
        url.searchParams.set('size', 'thumb');
      return ['http:', 'https:'].includes(url.protocol) ? url.href : '';
    } catch (_) { return ''; }
  }
  async function login(email, password) {
    const result = await send('/api/auth/login', { method: 'POST', body: { email, password } });
    if (!['ADMIN', 'STAFF'].includes(result.user.role)) {
      await send('/api/auth/logout', { method: 'POST', body: { refreshToken: result.refreshToken } });
      throw new Error('An admin or staff account is required.');
    }
    setSession(result); return result;
  }
  async function logout() {
    const current = session(); setSession(null);
    if (current?.refreshToken) await send('/api/auth/logout', { method: 'POST', body: { refreshToken: current.refreshToken } });
  }
  async function upload(file) {
    if (!file || file.size > 5 * 1024 * 1024 || !['image/png', 'image/jpeg'].includes(file.type))
      throw new Error('Choose a PNG or JPEG image up to 5 MB.');
    const body = new FormData(); body.append('file', file);
    return (await request('/api/admin/uploads', { method: 'POST', body })).imageUrl;
  }
  window.OganiAPI = { request, session, setSession, login, logout, upload, imageUrl };
})();
