// Both interfaces use the same client and session, including refresh ownership.
window.OganiReady = import('/assets/api.js').then(({createApi}) => {
  const base=(window.OGANI_API_BASE||'').replace(/\/$/,'');
  const common=createApi({base});
  function imageUrl(value, thumbnail = false) {
    if (!value) return '';
    try {
      // Legacy seed images were authored for the missing storefront.
      if (value.startsWith('img/')) return '/admin/assets/images/ogani/' + value.slice(4).replace('product/details/', 'details/');
      const url = new URL(value.startsWith('/api/images/') ? base + value : value, location.href);
      if (thumbnail && url.pathname.startsWith('/api/images/') && url.origin === new URL(base || location.href, location.href).origin)
        url.searchParams.set('size', 'thumb');
      return ['http:', 'https:'].includes(url.protocol) ? url.href : '';
    } catch (_) { return ''; }
  }
  async function upload(file) {
    if(!file||file.size>5*1024*1024||!['image/png','image/jpeg'].includes(file.type))throw new Error('Choose a PNG or JPEG image up to 5 MB.');
    const body=new FormData();body.append('file',file);
    return (await common.request('/api/admin/uploads',{method:'POST',body})).imageUrl;
  }
  window.OganiAPI={...common,imageUrl,upload};return window.OganiAPI;
});
