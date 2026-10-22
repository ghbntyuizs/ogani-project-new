/* Ogani Admin - tương tác tĩnh cho các trang quản trị (demo, chưa gọi API). */
document.addEventListener('DOMContentLoaded', function () {

  // 1) Tìm kiếm nhanh trong bảng: <input data-table-search> lọc các <tr> trong card gần nhất
  document.querySelectorAll('[data-table-search]').forEach(function (input) {
    input.addEventListener('input', function () {
      var card = input.closest('.table-card-custom');
      var q = input.value.trim().toLowerCase();
      card.querySelectorAll('tbody tr').forEach(function (tr) {
        tr.dataset.hiddenBySearch = tr.textContent.toLowerCase().includes(q) ? '' : '1';
        applyVisibility(tr);
      });
    });
  });

  // 2) Lọc theo trạng thái: nút .status-pill[data-filter] + hàng <tr data-status>
  document.querySelectorAll('.status-pill').forEach(function (pill) {
    pill.addEventListener('click', function () {
      var card = pill.closest('.table-card-custom');
      card.querySelectorAll('.status-pill').forEach(function (p) { p.classList.remove('active'); });
      pill.classList.add('active');
      var f = pill.dataset.filter;
      card.querySelectorAll('tbody tr').forEach(function (tr) {
        tr.dataset.hiddenByStatus = (f === 'ALL' || tr.dataset.status === f) ? '' : '1';
        applyVisibility(tr);
      });
    });
  });
  function applyVisibility(tr) {
    tr.style.display = (tr.dataset.hiddenBySearch || tr.dataset.hiddenByStatus) ? 'none' : '';
  }
  // Mở trang với ?status=PENDING thì tự bấm pill tương ứng
  var qs = new URLSearchParams(location.search).get('status');
  if (qs) { var p = document.querySelector('.status-pill[data-filter="' + qs + '"]'); if (p) p.click(); }

  // 3) Xác nhận xoá
  document.querySelectorAll('.table-btn-action.delete').forEach(function (btn) {
    btn.addEventListener('click', function (e) {
      e.preventDefault();
      if (confirm('Delete this record? (demo only - nothing is sent to the server)')) {
        var tr = btn.closest('tr'); if (tr) tr.remove();
      }
    });
  });

  // 4) Tự sinh slug từ tên: <input data-slug-source="#slug">
  document.querySelectorAll('[data-slug-source]').forEach(function (src) {
    var target = document.querySelector(src.dataset.slugSource);
    src.addEventListener('input', function () {
      if (!target || target.dataset.touched) return;
      target.value = src.value.toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g, '')
        .replace(/đ/g, 'd').replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '');
    });
    if (target) target.addEventListener('input', function () { target.dataset.touched = '1'; });
  });

  // 5) Xem trước ảnh upload: <input type=file data-preview="#imgId">
  document.querySelectorAll('input[type=file][data-preview]').forEach(function (input) {
    input.addEventListener('change', function () {
      var img = document.querySelector(input.dataset.preview);
      if (input.files[0] && img) { img.src = URL.createObjectURL(input.files[0]); img.classList.add('has-img'); }
    });
  });

  // 6) Tính % giảm giá trong form sản phẩm
  var price = document.getElementById('price'), sale = document.getElementById('salePrice'), pct = document.getElementById('salePct');
  function calcPct() {
    if (!price || !sale || !pct) return;
    var p = parseFloat(price.value), s = parseFloat(sale.value);
    pct.textContent = (p > 0 && s > 0 && s < p) ? '-' + Math.round((1 - s / p) * 100) + '%' : '—';
  }
  if (price && sale) { price.addEventListener('input', calcPct); sale.addEventListener('input', calcPct); calcPct(); }

  // 7) Cập nhật trạng thái đơn (demo): thêm dòng vào timeline
  var btn = document.getElementById('btnUpdateStatus');
  if (btn) btn.addEventListener('click', function () {
    var sel = document.getElementById('orderStatus'), note = document.getElementById('statusNote');
    var li = document.createElement('li');
    li.innerHTML = '<strong>' + sel.value + '</strong><small>' + new Date().toLocaleString() + ' · ' + (note.value || 'No note') + '</small>';
    document.getElementById('orderTimeline').prepend(li);
    note.value = '';
  });
});
