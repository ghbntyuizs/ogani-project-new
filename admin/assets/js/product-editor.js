/* Version-aware operations, kept independent of the DOM for regression testing. */
(function () {
  'use strict';
  class ProductEditor {
    constructor(api, product) {
      this.api = api; this.id = product.id || null; this.version = product.version;
      this.thumbnailUrl = product.thumbnailUrl || null; this.stale = false; this.busy = false;
    }
    conflict() {
      this.stale = true;
      const error = new Error('This product changed in another session. Reload before saving; your edits have not been overwritten.');
      error.status = 409; return error;
    }
    async perform(action) {
      if (this.stale) throw this.conflict();
      if (this.busy) throw new Error('Please wait for the current operation to finish.');
      this.busy = true;
      try { return await action(); }
      catch (error) { if (error.status === 409) this.stale = true; throw error; }
      finally { this.busy = false; }
    }
    accept(state) {
      this.version = state.version; this.thumbnailUrl = state.thumbnailUrl || null; return state;
    }
    loadGallery() {
      return this.perform(async () => {
        if (!this.id) return { images: [], thumbnailUrl: this.thumbnailUrl, version: this.version };
        const state = await this.api.request('/api/admin/products/' + this.id + '/images/state');
        if (state.version !== this.version) throw this.conflict();
        return state;
      });
    }
    gallery(method, suffix, body) {
      return this.perform(async () => this.accept(await this.api.request(
        '/api/admin/products/' + this.id + '/images/state' + suffix,
        { method, headers: { 'If-Match': String(this.version) }, ...(body ? { body } : {}) })));
    }
    addImage(imageUrl, thumbnail = false) { return this.gallery('POST', thumbnail ? '?thumbnail=true' : '', { imageUrl }); }
    removeImage(id) { return this.gallery('DELETE', '/' + id); }
    selectThumbnail(id) { return this.gallery('PUT', '/' + id + '/thumbnail'); }
    reorder(imageIds) { return this.gallery('PUT', '/order', { imageIds }); }
    save(payload) {
      return this.perform(async () => {
        const body = { ...payload, ...(this.id ? { version: this.version } : {}) };
        if (!this.id) delete body.version;
        const saved = await this.api.request('/api/admin/products' + (this.id ? '/' + this.id : ''),
          { method: this.id ? 'PUT' : 'POST', body });
        this.id = saved.id; return this.accept(saved);
      });
    }
  }
  window.OganiProductEditor = ProductEditor;
})();
