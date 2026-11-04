(function () {
  'use strict';
  let token = new URLSearchParams(location.hash.slice(1)).get('token');
  // Fragments do not reach the HTTP server; remove it from the visible URL immediately.
  history.replaceState(null, '', location.pathname + location.search);
  const form = document.getElementById('reset-form');
  const message = document.getElementById('message');
  const button = form.querySelector('button');
  const password = document.getElementById('new-password');
  const confirmation = document.getElementById('confirm-password');
  let busy = false;
  function show(text) { message.textContent = text; message.hidden = false; }
  if (!token || !/^[A-Za-z0-9_-]{43}$/.test(token)) {
    token = null; button.disabled = true;
    show('Liên kết không hợp lệ. Vui lòng yêu cầu gửi lại email đặt mật khẩu.');
  }
  form.addEventListener('submit', async function (event) {
    event.preventDefault();
    if (busy || !token || !form.reportValidity()) return;
    if (password.value !== confirmation.value) { show('Hai mật khẩu chưa khớp.'); return; }
    if (new TextEncoder().encode(password.value).length > 72) { show('Mật khẩu quá dài. Vui lòng chọn mật khẩu ngắn hơn.'); return; }
    busy = true; button.disabled = true;
    try {
      const response = await fetch('/api/auth/reset-password', {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ token, newPassword: password.value, confirmPassword: confirmation.value }),
        signal: AbortSignal.timeout(15000)
      });
      if (!response.ok) {
        show(response.status === 429 ? 'Bạn đã thử quá nhiều lần. Vui lòng thử lại sau.'
          : 'Không thể đặt lại mật khẩu. Kiểm tra mật khẩu mới hoặc yêu cầu một liên kết mới.');
        return;
      }
      token = null; password.value = ''; confirmation.value = ''; form.hidden = true;
      show('Đã đặt lại mật khẩu. Vui lòng quay lại ứng dụng và đăng nhập bằng mật khẩu mới.');
    } catch (_) {
      show('Chưa xác nhận được kết quả. Hãy thử đăng nhập bằng mật khẩu mới hoặc yêu cầu liên kết mới.');
    } finally { busy = false; button.disabled = !token; }
  });
})();
