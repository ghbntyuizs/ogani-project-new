document.addEventListener('DOMContentLoaded', function () {
  const form = document.getElementById('loginForm');
  const password = document.getElementById('password');
  document.getElementById('toggle-password')?.addEventListener('click', function () {
    password.type = password.type === 'password' ? 'text' : 'password';
    this.setAttribute('aria-label', password.type === 'password' ? 'Show password' : 'Hide password');
  });
  document.getElementById('rememberMe')?.closest('label')?.remove();
  document.querySelector('.forgot-password-link')?.remove();
  const error = document.createElement('div');
  error.className = 'alert alert-danger'; error.hidden = true; error.setAttribute('role', 'alert');
  form.prepend(error);
  form.removeAttribute('action'); form.removeAttribute('method');
  form.addEventListener('submit', async event => {
    event.preventDefault(); error.hidden = true; form.classList.add('was-validated');
    if (!form.checkValidity()) return;
    const button = form.querySelector('[type=submit]'); button.disabled = true;
    try {
      await OganiAPI.login(document.getElementById('email').value.trim(), password.value);
      location.replace('products.html');
    } catch (failure) { error.textContent = failure.message; error.hidden = false; }
    finally { button.disabled = false; }
  });
});
