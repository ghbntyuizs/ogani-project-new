(function () {
  'use strict';
  const api = new Proxy({}, {get:(_target,name)=>window.OganiAPI[name]});
  const $ = (selector, root = document) => root.querySelector(selector);
  const ui = () => window.OganiUI;
  const node = (...args) => ui().node(...args);
  const money = value => new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' }).format(value);
  function button(text, action, style = 'btn-custom btn-custom-outline-secondary') {
    const result = node('button', text, style); result.type = 'button';
    result.addEventListener('click', async () => {
      result.disabled = true;
      try { await action(); } catch (error) { ui().message(error.message); }
      finally { result.disabled = false; }
    });
    return result;
  }
  function cell(row, value) { const td = node('td', value ?? '—'); row.append(td); return td; }
  function tableState(body, text, columns) {
    const row = node('tr'), td = node('td', text); td.colSpan = columns; row.append(td); body.replaceChildren(row);
  }
  function image(value, className) {
    const img = node('img', null, className); img.alt = '';
    const url = api.imageUrl(value, true);
    img.loading = 'lazy';
    if (url) img.src = url;
    img.addEventListener('error', () => { img.hidden = true; }, { once: true });
    return img;
  }
  async function products() {
    const body = $('tbody'); tableState(body, 'Loading products…', 8);
    const search = $('[data-table-search]');
    const footer = $('.table-pagination-info'); const pagination = $('.pagination');
    let page = 0, status = 'ALL', sequence = 0;
    const controls = $('.table-filter-group');
    const categories = node('select', null, 'form-select'); categories.setAttribute('aria-label', 'Category filter');
    const all = node('option', 'All categories'); all.value = ''; categories.append(all); controls?.append(categories);
    const sort = node('select', null, 'form-select'); sort.setAttribute('aria-label', 'Sort products');
    for (const [value, title] of [['latest','Latest'],['price_asc','Price: low to high'],['price_desc','Price: high to low'],['popular','Popular']]) {
      const option = node('option', title); option.value = value; sort.append(option);
    }
    controls?.append(sort);
    async function load() {
      const current = ++sequence; tableState(body, 'Loading products…', 8); pagination.replaceChildren();
      const query = new URLSearchParams({ page, size: 12, sort: sort.value });
      if (search.value.trim()) query.set('keyword', search.value.trim());
      if (status !== 'ALL') query.set('status', status);
      if (categories.value) query.set('categoryId', categories.value);
      try {
        const result = await api.request('/api/admin/products?' + query);
        if (current !== sequence) return;
        if (page > 0 && result.content.length === 0) { page--; return load(); }
        body.replaceChildren();
        for (const product of result.content) {
          const row = node('tr');
          const first = cell(row, ''); first.append(image(product.thumbnailUrl, 'prod-thumb'), node('span', product.name));
          cell(row, product.categoryName); cell(row, money(product.salePrice ?? product.price));
          cell(row, product.stockQuantity); cell(row, product.avgRating + ' (' + product.reviewCount + ')');
          cell(row, product.soldCount); cell(row, product.status);
          const actions = cell(row, ''); const edit = node('a', 'Edit', 'btn-custom btn-custom-outline-secondary');
          edit.href = 'product-form.html?id=' + product.id; actions.append(edit);
          if (ui().role === 'ADMIN' && product.status !== 'HIDDEN') actions.append(button('Hide', async () => {
            if (!confirm('Hide ' + product.name + '?')) return;
            await api.request('/api/admin/products/' + product.id, { method: 'DELETE' }); await load();
          }));
          body.append(row);
        }
        if (!result.content.length) tableState(body, 'No products found.', 8);
        footer.textContent = result.totalElements + ' products · Page ' + (result.totalPages ? page + 1 : 0) + ' of ' + result.totalPages;
        const previous = button('Previous', async () => { page--; await load(); }); previous.disabled = page === 0;
        const next = button('Next', async () => { page++; await load(); }); next.disabled = page + 1 >= result.totalPages;
        pagination.append(previous, next); ui().message('');
      } catch (error) {
        if (current !== sequence) return;
        tableState(body, 'Products could not be loaded.', 8); footer.textContent = '';
        pagination.append(button('Retry', load)); ui().message(error.message);
      }
    }
    let debounce;
    search.addEventListener('input', () => { clearTimeout(debounce); debounce = setTimeout(() => { page = 0; load(); }, 300); });
    for (const select of [sort, categories]) select.addEventListener('change', () => { page = 0; load(); });
    document.querySelectorAll('.status-pill').forEach(pill => pill.addEventListener('click', () => {
      status = pill.dataset.filter; page = 0;
      document.querySelectorAll('.status-pill').forEach(item => item.classList.toggle('active', item === pill)); load();
    }));
    const cats = await api.request('/api/admin/categories');
    cats.forEach(cat => { const option = node('option', cat.name); option.value = cat.id; categories.append(option); });
    await load();
  }

  async function categories() {
    const root = $('#tab-pc'), body = $('tbody', root), form = $('#modalCat form');
    const modalError = node('div', '', 'alert alert-danger'); modalError.hidden = true; modalError.setAttribute('role', 'alert');
    $('.modal-body', form).prepend(modalError);
    let records = [], editing = null, imageUrl = null, saving = false;
    $('#modalCat').addEventListener('hide.bs.modal', event => { if (saving) event.preventDefault(); });
    tableState(body, 'Loading categories…', 7);
    document.querySelectorAll('[data-bs-target="#tab-bc"], [data-bs-target="#tab-bt"]').forEach(tab => {
      tab.disabled = true; tab.title = 'Blog API is not implemented yet';
    });
    $('#c_active').closest('.col-12').textContent = 'Categories are created active. Use Delete to deactivate a category.';
    const name = $('#c_name'); name.required = true; name.maxLength = 100;
    $('#c_slug').maxLength = 120; $('#c_sort').min = 0; $('#c_sort').step = 1;
    form.removeAttribute('onsubmit');
    const save = $('[type=submit]', form); save.removeAttribute('data-bs-dismiss');
    const file = $('input[type=file]', form); file.accept = 'image/png,image/jpeg';
    if (ui().role !== 'ADMIN') { save.disabled = true; $('[data-bs-target="#modalCat"]').disabled = true; }
    function render() {
      body.replaceChildren();
      const query = $('[data-table-search]', root).value.trim().toLowerCase();
      records.filter(cat => (cat.name + ' ' + cat.slug).toLowerCase().includes(query)).forEach(cat => {
        const row = node('tr');
        [cat.id, cat.name, cat.slug, '—', cat.sortOrder, cat.active ? 'Active' : 'Inactive'].forEach(value => cell(row, value));
        const actions = cell(row, '');
        if (ui().role === 'ADMIN') {
          actions.append(button('Edit', () => open(cat)));
          if (cat.active) actions.append(button('Delete', async () => {
            if (!confirm('Deactivate ' + cat.name + '?')) return;
            await api.request('/api/admin/categories/' + cat.id, { method: 'DELETE' }); await load();
          }));
        }
        body.append(row);
      });
      if (!body.children.length) tableState(body, 'No categories found.', 7);
      $('.table-pagination-info', root).textContent = records.length + ' categories'; $('.pagination', root).replaceChildren();
    }
    async function load() { records = await api.request('/api/admin/categories'); render(); }
    function open(cat = null) {
      editing = cat?.id ?? null; imageUrl = cat?.imageUrl ?? null; form.reset(); modalError.hidden = true;
      name.value = cat?.name ?? ''; $('#c_slug').value = cat?.slug ?? ''; $('#c_sort').value = cat?.sortOrder ?? 0;
      const parent = $('#c_parent'); parent.replaceChildren();
      const none = node('option', 'None'); none.value = ''; parent.append(none);
      records.filter(item => item.active && item.id !== editing).forEach(item => {
        const option = node('option', item.name); option.value = item.id; parent.append(option);
      });
      parent.value = cat?.parentId ?? ''; bootstrap.Modal.getOrCreateInstance($('#modalCat')).show();
    }
    const add = $('[data-bs-target="#modalCat"]'); add.removeAttribute('data-bs-toggle'); add.addEventListener('click', () => open());
    $('[data-table-search]', root).addEventListener('input', render);
    form.addEventListener('submit', async event => {
      event.preventDefault(); if (saving || !form.reportValidity() || ui().role !== 'ADMIN') return;
      const targetId = editing;
      const payload = { name: name.value.trim(), slug: $('#c_slug').value.trim(), imageUrl,
        parentId: $('#c_parent').value ? Number($('#c_parent').value) : null, sortOrder: Number($('#c_sort').value) };
      const selectedFile = file.files[0];
      saving = true; save.disabled = true; modalError.hidden = true;
      const controls = Array.from(form.querySelectorAll('input, select, button'));
      controls.forEach(control => { control.disabled = true; });
      try {
        if (selectedFile) { imageUrl = await api.upload(selectedFile); payload.imageUrl = imageUrl; file.value = ''; }
        await api.request('/api/admin/categories' + (targetId ? '/' + targetId : ''), { method: targetId ? 'PUT' : 'POST', body: payload });
        saving = false;
        bootstrap.Modal.getOrCreateInstance($('#modalCat')).hide(); await load(); ui().message('Category saved.', 'success');
      } catch (error) { modalError.textContent = error.message; modalError.hidden = false; }
      finally { saving = false; controls.forEach(control => { control.disabled = false; }); }
    });
    await load();
  }

  async function productForm() {
    const form = $('form'), save = $('[type=submit]', form);
    form.addEventListener('submit', event => event.preventDefault());
    form.removeAttribute('onsubmit'); save.disabled = true;
    let id = new URLSearchParams(location.search).get('id');
    if (id && !/^\d+$/.test(id)) throw new Error('Invalid product ID.');
    let thumbnailUrl = null;
    const fields = { name: 'name', slug: 'slug', sku: 'sku', categoryId: 'category_id', status: 'status',
      shortDescription: 'short_description', description: 'description', information: 'information', price: 'price',
      salePrice: 'salePrice', weightValue: 'weight_value', weightUnit: 'weight_unit', stockQuantity: 'stock_quantity',
      shippingInfo: 'shipping_info', featured: 'is_featured' };
    const numeric = new Set(['categoryId', 'price', 'salePrice', 'weightValue', 'stockQuantity']);
    for (const field of ['name', 'sku', 'category_id', 'price', 'stock_quantity']) $('#' + field).required = true;
    $('#name').maxLength = 200; $('#sku').maxLength = 50; $('#slug').maxLength = 220;
    $('#short_description').maxLength = 500; $('#shipping_info').maxLength = 255;
    $('#stock_quantity').step = 1; $('#weight_value').min = 0;
    const cats = await api.request('/api/admin/categories');
    const category = $('#category_id'); category.replaceChildren();
    cats.filter(cat => cat.active).forEach(cat => { const option = node('option', cat.name); option.value = cat.id; category.append(option); });
    const product = id ? await api.request('/api/admin/products/' + id) : {
      name: '', slug: '', sku: '', categoryId: category.value, status: 'ACTIVE', price: '', stockQuantity: 0, weightUnit: 'KG', featured: false
    };
    if (id && !Array.from(category.options).some(option => Number(option.value) === product.categoryId)) {
      const option = node('option', product.categoryName + ' (inactive)'); option.value = product.categoryId; category.append(option);
    }
    for (const [key, field] of Object.entries(fields)) {
      const input = $('#' + field);
      if (input.type === 'checkbox') input.checked = !!product[key];
      else input.value = key === 'weightUnit' ? (product[key] || 'KG').toLowerCase() : product[key] ?? '';
    }
    document.querySelector('.page-title').textContent = id ? 'Edit product' : 'Add product';
    const editor = new window.OganiProductEditor(api, product);
    const files = Array.from(form.querySelectorAll('input[type=file]'));
    files.forEach(file => { file.accept = 'image/png,image/jpeg'; });
    const thumb = files.find(file => !file.multiple), galleryInput = files.find(file => file.multiple);
    const thumbHelp = node('small', '', 'text-muted'); thumb.insertAdjacentElement('afterend', thumbHelp);
    thumbnailUrl = product.thumbnailUrl ?? null;
    const preview = $('#thumbPreview'); preview.removeAttribute('src');
    if (thumbnailUrl) preview.src = api.imageUrl(thumbnailUrl, true);
    const gallery = $('.gallery-grid'); gallery.replaceChildren();
    let galleryBusy = false, currentGallery = { images: [], thumbnailUrl };
    function showPreview(url) {
      if (url) preview.src = api.imageUrl(url, true);
      else preview.removeAttribute('src');
    }
    async function withMedia(action) {
      if (galleryBusy || editor.stale) return;
      galleryBusy = true;
      const disabled = new Map(Array.from(form.querySelectorAll('input,select,textarea,button')).map(control => [control, control.disabled]));
      disabled.forEach((_wasDisabled, control) => { control.disabled = true; });
      try { await action(); }
      catch (error) { ui().message(error.message); }
      finally {
        galleryBusy = false;
        form.querySelectorAll('input,select,textarea,button').forEach(control => { control.disabled = disabled.get(control) ?? false; });
        save.disabled = editor.stale;
        thumb.disabled = editor.stale;
        galleryInput.disabled = !id || editor.stale;
        if (editor.stale) gallery.querySelectorAll('button').forEach(control => { control.disabled = true; });
      }
    }
    function renderGallery(state) {
      currentGallery = state; thumbnailUrl = state.thumbnailUrl;
      showPreview(thumbnailUrl); gallery.replaceChildren();
      thumbHelp.textContent = id
        ? 'Choose a new image to change the thumbnail immediately. Product text changes still need Save.'
        : 'The thumbnail is uploaded when you save the new product.';
      if (!id) { gallery.append(node('small', 'Save the product before adding gallery images.')); return; }
      state.images.forEach((item, index) => {
        const card = node('div'); card.append(image(item.imageUrl, 'prod-thumb'));
        if (index === 0) card.append(node('small', 'Thumbnail', 'badge bg-success'));
        else card.append(button('Set as thumbnail', () => withMedia(async () => {
          renderGallery(await editor.selectThumbnail(item.id)); ui().message('Thumbnail saved.', 'success');
        })));
        card.append(button('Remove', () => withMedia(async () => {
          if (!confirm('Remove this gallery image?')) return;
          renderGallery(await editor.removeImage(item.id)); ui().message('Gallery saved.', 'success');
        })));
        if (index > 0) card.append(button('Move earlier', () => withMedia(async () => {
          const order = state.images.map(image => image.id);
          [order[index - 1], order[index]] = [order[index], order[index - 1]];
          renderGallery(await editor.reorder(order)); ui().message('Gallery saved.', 'success');
        })));
        gallery.append(card);
      });
    }
    galleryInput.disabled = !id;
    galleryInput.addEventListener('change', async () => {
      const selected = Array.from(galleryInput.files);
      await withMedia(async () => {
        try {
          for (const file of selected) {
            const imageUrl = await api.upload(file);
            renderGallery(await editor.addImage(imageUrl));
          }
          ui().message('Gallery saved.', 'success');
        } finally { galleryInput.value = ''; }
      });
    });
    let previewObjectUrl;
    thumb.addEventListener('change', async () => {
      const selected = thumb.files[0]; if (!selected) return;
      if (previewObjectUrl) URL.revokeObjectURL(previewObjectUrl);
      if (!id) { previewObjectUrl = URL.createObjectURL(selected); preview.src = previewObjectUrl; return; }
      await withMedia(async () => {
        try {
          const imageUrl = await api.upload(selected);
          renderGallery(await editor.addImage(imageUrl, true));
          ui().message('Thumbnail saved.', 'success');
        } finally { thumb.value = ''; }
      });
    });
    window.addEventListener('pagehide', () => { if (previewObjectUrl) URL.revokeObjectURL(previewObjectUrl); });
    let slugTouched = !!id;
    $('#slug').addEventListener('input', () => { slugTouched = true; });
    $('#name').addEventListener('input', () => {
      if (!slugTouched) $('#slug').value = $('#name').value.toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g, '')
        .replace(/đ/g, 'd').replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '');
    });
    function discount() {
      const price = Number($('#price').value), sale = Number($('#salePrice').value);
      $('#salePct').textContent = $('#salePrice').value !== '' && price > 0 ? Math.round((1 - sale / price) * 100) + '%' : '—';
    }
    $('#price').addEventListener('input', discount); $('#salePrice').addEventListener('input', discount); discount();
    form.addEventListener('submit', async event => {
      event.preventDefault(); if (!form.reportValidity() || galleryBusy || editor.stale) return;
      const payload = {};
      for (const [key, field] of Object.entries(fields)) {
        const input = $('#' + field), value = input.value.trim();
        payload[key] = input.type === 'checkbox' ? input.checked : numeric.has(key) ? (value === '' ? null : Number(value)) : value;
      }
      payload.weightUnit = payload.weightUnit.toUpperCase();
      if (payload.salePrice != null && payload.salePrice > payload.price) { ui().message('Sale price must not exceed price.'); return; }
      await withMedia(async () => {
        if (!id && thumb.files[0]) { thumbnailUrl = await api.upload(thumb.files[0]); thumb.value = ''; }
        payload.thumbnailUrl = thumbnailUrl;
        const wasNew = !id;
        const saved = await editor.save(payload);
        if (wasNew) { id = String(saved.id); location.replace('product-form.html?id=' + id); return; }
        renderGallery({ ...currentGallery, version: saved.version, thumbnailUrl: saved.thumbnailUrl });
        ui().message('Product saved.', 'success');
      });
    });
    renderGallery(await editor.loadGallery()); save.disabled = false;

  }
  window.OganiCatalog = { start: page => ({ 'products.html': products, 'categories.html': categories, 'product-form.html': productForm })[page]() };
})();
