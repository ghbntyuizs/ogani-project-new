// /shop is served by Spring Boot. VS Code/Node local hosting uses the API on8080.
window.OGANI_STOREFRONT_CONFIG = {
  apiBase: location.pathname.startsWith('/shop/') ? '' : 'http://localhost:8080',
  currency: 'USD'
};
