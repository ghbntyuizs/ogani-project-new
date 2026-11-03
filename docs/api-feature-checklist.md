# Checklist API theo feature

Cập nhật: 2026-11-03. Endpoint chưa hoàn thành là đề xuất, có thể điều chỉnh khi chốt nghiệp vụ. Chỉ đánh dấu hoàn thành khi có triển khai và kiểm thử.

## 1. Đơn hàng — Customer (ưu tiên cao)

- [x] **GET `/api/orders`** — Danh sách đơn của người đăng nhập.
  - [x] Phân trang page/size; mặc định page0, size20; giới hạn100.
  - [x] Lọc PENDING/CONFIRMED/SHIPPING/COMPLETED/CANCELLED.
  - [x] Sắp xếp createdAt tăng/giảm, thêm id để ổn định khi ngày trùng nhau.
  - [x] Truy vấn và tổng số đơn đều giới hạn theo người sở hữu.
  - [x] Không có đơn, không khớp filter hoặc vượt trang cuối trả200 với content rỗng.
  - [x] Tham số sai/blank/overflow hoặc sort không hỗ trợ trả400.
  - [x] Anonymous/token sai/tài khoản khóa trả401.
  - [x] Customer/Admin/Staff chỉ thấy đơn của chính mình; không chọn userId tùy ý.
  - [x] Payload tóm tắt không chứa checkout key/hash, địa chỉ hoặc thông tin item.
  - [x] Kiểm thử33 trường hợp. Chi tiết: [Order list](customer-order-list-verification.md).
- [ ] **POST `/api/orders/{id}/cancel`** — Hủy đơn của mình trong trạng thái cho phép.
  - [ ] Chốt trạng thái được hủy và quyền của customer.
  - [ ] Hoàn kho đúng một lần, xử lý trạng thái OUT_OF_STOCK khi hoàn.
  - [ ] Chống hủy đồng thời, ghi lịch sử và giữ snapshot đơn.

Đã có: GET `/api/orders/{id}` và checkout COD. API danh sách customer không thay thế API danh sách toàn bộ đơn cho admin.

## 2. Đơn hàng — Admin/Staff (ưu tiên cao)

- [x] **GET `/api/admin/orders`** — Tìm kiếm, lọc trạng thái, phân trang tất cả đơn theo quyền.
  - [x] Admin/Staff được xem tất cả đơn; Customer403, Anonymous401.
  - [x] Tìm mã đơn, tên/email snapshot; escape wildcard và giới hạn keyword.
  - [x] Lọc trạng thái đơn/thanh toán và khoảng ngày, kết hợp AND.
  - [x] Phân trang, sort ngày tạo tăng/giảm và id tie-breaker.
  - [x] Summary projection, kiểm tra SQL không N+1 và index V10.
  - [x] Empty/boundary/validation, tài khoản bị khóa/hạ quyền.
  - [x] [Tài liệu và kiểm thử](admin-order-list-verification.md).
- [x] **GET `/api/admin/orders/{id}`** — Chi tiết đơn và lịch sử trạng thái.
  - [x] Customer/address/product snapshots và payment/coupon/amounts đã lưu.
  - [x] Lịch sử theo changedAt/id, vẫn giữ sự kiện khi actor bị xóa.
  - [x] Nullable customer/product/coupon references và dữ liệu cũ rỗng.
  - [x] Admin/Staff; chặn Customer, Anonymous, tài khoản khóa/hạ quyền.
  - [x] Snapshot giao dịch nhất quán khi trạng thái đang được cập nhật.
  - [x] Projection3 truy vấn, không N+1 hoặc lộ key/hash; index V11.
  - [x] [Tài liệu và kiểm thử](admin-order-detail-verification.md).
- [ ] **PATCH `/api/admin/orders/{id}/status`** — Chuyển trạng thái theo state machine.
- [ ] **PATCH `/api/admin/orders/{id}/payment-status`** — Xác nhận thu tiền COD theo quyền.
- [ ] Chặn chuyển trạng thái sai, chống thao tác lặp/đồng thời.
- [ ] Hủy đơn hoàn kho đúng một lần; hoàn tất tăng sold_count đúng một lần.
- [ ] Liên kết quy tắc COMPLETED/PAID và lưu người thực hiện, thời gian, lý do.

API trạng thái và thanh toán có thể gộp lại khi chốt nghiệp vụ; không cập nhật tùy ý các enum.

## 3. Địa chỉ giao hàng

- [ ] **POST `/api/addresses`** — Lưu địa chỉ mới.
- [ ] **PUT `/api/addresses/{id}`** — Sửa địa chỉ của mình.
- [ ] **DELETE `/api/addresses/{id}`** — Xóa địa chỉ của mình.
- [ ] **PATCH `/api/addresses/{id}/default`** — Chọn địa chỉ mặc định.
- [ ] Chốt quy tắc địa chỉ mặc định khi tạo/xóa; chống nhiều địa chỉ mặc định do request đồng thời.
- [ ] Không ảnh hưởng địa chỉ snapshot của đơn cũ.

Đã có GET `/api/checkout/addresses` để lấy danh sách địa chỉ đã lưu và chọn khi checkout.

## 4. Tài khoản

- [ ] **PATCH `/api/users/me`** — Cập nhật thông tin cá nhân.
- [ ] **PUT `/api/users/me/password`** — Đổi mật khẩu.
- [ ] **POST `/api/auth/forgot-password`** — Yêu cầu khôi phục mật khẩu.
- [ ] **POST `/api/auth/reset-password`** — Đặt lại mật khẩu bằng token.
- [ ] Token reset có hạn, dùng một lần; chốt thu hồi session sau đổi/reset mật khẩu.

## 5. Coupon — Customer

- [ ] **POST `/api/cart/coupon`** — Áp dụng mã giảm giá.
- [ ] **DELETE `/api/cart/coupon`** — Bỏ mã giảm giá.
- [ ] Đồng bộ cart/preview/checkout, kiểm tra lại điều kiện khi đặt hàng.
- [ ] Kiểm tra hạn dùng, hạn mức, min order, phần trăm/trần giảm và tranh lượt sử dụng đồng thời.
- [ ] Chốt chính sách hoàn lượt sử dụng khi hủy đơn.

## 6. Coupon — Admin

- [ ] **GET `/api/admin/coupons`** — Danh sách/phân trang.
- [ ] **GET `/api/admin/coupons/{id}`** — Chi tiết.
- [ ] **POST `/api/admin/coupons`** — Tạo coupon.
- [ ] **PUT `/api/admin/coupons/{id}`** — Cập nhật coupon.
- [ ] **DELETE `/api/admin/coupons/{id}`** — Vô hiệu hóa, không phá snapshot đơn cũ.

## 7. Wishlist

- [ ] **GET `/api/wishlist`** — Danh sách yêu thích của mình.
- [ ] **POST `/api/wishlist/items`** — Thêm sản phẩm.
- [ ] **DELETE `/api/wishlist/items/{productId}`** — Bỏ sản phẩm.
- [ ] Chống trùng item, chốt hiển thị sản phẩm đã ẩn/xóa.

## 8. Đánh giá sản phẩm

- [ ] **GET `/api/products/{slug}/reviews`** — Danh sách đánh giá/phân trang.
- [ ] **POST `/api/products/{slug}/reviews`** — Đánh giá sản phẩm đã mua.
- [ ] **PUT `/api/reviews/{id}`** — Sửa đánh giá của mình.
- [ ] **DELETE `/api/reviews/{id}`** — Xóa đánh giá của mình.
- [ ] Chốt điều kiện đã mua/hoàn tất và số đánh giá được phép; cập nhật avg_rating/review_count đúng.

## 9. Quản lý người dùng

- [ ] **GET `/api/admin/users`** — Danh sách/tìm kiếm/phân trang.
- [ ] **GET `/api/admin/users/{id}`** — Chi tiết theo quyền, không lộ hash/token.
- [ ] **PATCH `/api/admin/users/{id}/status`** — Khóa/mở tài khoản.
- [ ] **PATCH `/api/admin/users/{id}/role`** — Phân quyền, chỉ ADMIN.
- [ ] Chốt bảo vệ tài khoản admin cuối cùng/tự khóa/tự hạ quyền và thu hồi session.

## 10. Cấu hình cửa hàng

- [ ] **GET `/api/settings/public`** — Chỉ cấu hình công khai.
- [ ] **GET `/api/admin/settings`** — Xem cấu hình theo quyền.
- [ ] **PUT `/api/admin/settings`** — Sửa phí ship, ngưỡng miễn phí và cấu hình hợp lệ.
- [ ] Phân biệt cấu hình công khai/bí mật; đồng bộ với preview checkout và không sửa snapshot đơn cũ.

## 11. Dashboard

- [ ] **GET `/api/admin/dashboard/summary`** — Tổng quan đơn/doanh thu.
- [ ] **GET `/api/admin/dashboard/revenue`** — Doanh thu theo thời gian.
- [ ] **GET `/api/admin/dashboard/top-products`** — Sản phẩm bán chạy.
- [ ] Chốt định nghĩa doanh thu theo trạng thái/thanh toán, múi giờ và đơn hủy/hoàn tiền.

## Quy trình hoàn thành mỗi API

- [ ] Chốt quyền truy cập và business rules.
- [ ] Triển khai validation, transaction/concurrency khi cần.
- [ ] Test thành công, dữ liệu rỗng, quyền, edge cases và rollback.
- [ ] Chạy hồi quy các feature liên quan.
- [ ] Cập nhật Swagger, ví dụ request/response và checklist.

## Thứ tự ưu tiên tiếp theo

1. Quản lý đơn/chuyển trạng thái/thu tiền COD, customer hủy đơn.
2. CRUD địa chỉ và tài khoản.
3. Coupon, cấu hình cửa hàng, dashboard.
4. Wishlist, đánh giá và tích hợp storefront.
