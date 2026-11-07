// A cached document must not reveal the previous account's DOM after a role switch.
window.addEventListener('pagehide', () => { document.documentElement.hidden = true; });
window.addEventListener('pageshow', event => {
  if (event.persisted) {
    document.documentElement.hidden = true;
    location.reload();
  }
});
document.addEventListener('DOMContentLoaded', async function () {
  await window.OganiReady;
  const api = window.OganiAPI;
  api.subscribe(reason=>{if(reason==='logout'||reason==='expired'){document.documentElement.hidden=true;location.replace('/login.html');}});
  const page = location.pathname.split('/').pop() || 'index.html';
  const node = (tag, text, className) => {
    const element = document.createElement(tag);
    if (text != null) element.textContent = text;
    if (className) element.className = className;
    return element;
  };
  const host = document.querySelector('.page-title')?.parentElement?.parentElement;
  function message(text, kind = 'danger') {
    let box = document.getElementById('api-message');
    if (!box) {
      box = node('div'); box.id = 'api-message'; box.setAttribute('role', 'status');
      (host || document.body).insertAdjacentElement(host ? 'afterend' : 'afterbegin', box);
    }
    box.className = 'alert alert-' + kind; box.textContent = text; box.hidden = !text;
    if (text) box.scrollIntoView({ block: 'nearest' });
  }
  window.OganiUI = { node, message };
  const globalSearch = document.getElementById('main-search');
  if (globalSearch) {
    globalSearch.disabled = true;
    globalSearch.placeholder = 'Use the filters on this page';
    globalSearch.closest('.navbar-search-wrapper')?.querySelector('button')?.setAttribute('disabled', '');
  }
  document.querySelectorAll('.sidebar-brand').forEach(el=>{el.href='/index.html';el.title='Ogani Store';const text=el.querySelector('span');if(text)text.textContent='Ogani Store';});
  document.querySelectorAll('a[href="index.html"]').forEach(el=>{el.href='/';});
  if (!api.session()) { location.replace('/login.html'); return; }
  document.querySelectorAll('a[data-action="logout"]').forEach(link => {
    link.addEventListener('click', async event => {
      event.preventDefault();
      try { await api.logout(); }
      catch (error) { console.warn('Logout revocation failed:', error.message); }
      location.replace('/login.html');
    });
  });
  try {
    const user = await api.verifySession();
    if (!user) { location.replace('/login.html'); return; }
    if (!['ADMIN', 'STAFF'].includes(user.role)) {
      location.replace('/index.html'); return;
    }
    document.querySelectorAll('.sidebar-profile-name, .navbar-profile-name').forEach(el => {
      el.textContent = api.session()?.user?.fullName || user.email;
    });
    document.querySelectorAll('.sidebar-profile-email').forEach(el => { el.textContent = user.email; });
    window.OganiUI.role = user.role;
    if (['products.html', 'product-form.html', 'categories.html'].includes(page)) {
      await window.OganiCatalog.start(page);
    } else if(['orders.html','order-detail.html'].includes(page)) {
      const orders=await import('./orders.js');await orders.start(page,api,window.OganiUI);
    } else {
      message('Preview only — this page has no connected API yet. Sample data is not live.', 'warning');
      document.querySelectorAll('form, .table-card-custom, .page-actions').forEach(section => {
        section.querySelectorAll('button, input, select, textarea').forEach(control => { control.disabled = true; });
        section.querySelectorAll('a.table-btn-action').forEach(link => {
          link.setAttribute('aria-disabled', 'true'); link.addEventListener('click', e => e.preventDefault());
        });
      });
      document.getElementById('btnUpdateStatus')?.setAttribute('disabled', '');
      document.querySelectorAll('form').forEach(form => form.addEventListener('submit', e => e.preventDefault()));
    }
  } catch (error) {
    if (!api.session()) { location.replace('/login.html'); return; }
    message(error.message);
  }
});
