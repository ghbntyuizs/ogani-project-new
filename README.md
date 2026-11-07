# Ogani Food Store

Ogani Food Store là project thương mại điện tử bán thực phẩm, gồm backend REST API, website mua hàng và giao diện quản trị. Project tập trung vào luồng mua hàng COD: xem sản phẩm, quản lý giỏ hàng, nhập hoặc chọn địa chỉ, đặt hàng, theo dõi/hủy đơn và xử lý giao hàng, thu tiền ở phía Admin/Staff.

**Trạng thái: đang phát triển.** Luồng mua hàng cơ bản đã kết nối API, bao gồm mua không cần tài khoản. Một số trang quản trị vẫn là giao diện preview; có giao diện hoặc entity trong database không đồng nghĩa tính năng đã hoàn thành.

## Mục lục

- [Tính năng hiện tại](#tính-năng-hiện-tại)
- [Công nghệ sử dụng](#công-nghệ-sử-dụng)
- [Cấu trúc thư mục](#cấu-trúc-thư-mục)
- [Cài đặt môi trường](#cài-đặt-môi-trường)
- [Chạy project](#chạy-project)
- [Cấu hình](#cấu-hình)
- [Luồng nghiệp vụ chính](#luồng-nghiệp-vụ-chính)
- [API và tài liệu](#api-và-tài-liệu)
- [Kiểm thử](#kiểm-thử)
- [Tính năng đang phát triển](#tính-năng-đang-phát-triển)
- [Xử lý lỗi thường gặp](#xử-lý-lỗi-thường-gặp)
- [Đóng góp và ghi nhận](#đóng-góp-và-ghi-nhận)

## Tính năng hiện tại

| Nhóm | Đã triển khai | Trạng thái giao diện |
| --- | --- | --- |
| Xác thực | Đăng ký, đăng nhập, JWT, refresh-token rotation, logout; phân quyền ADMIN/STAFF/CUSTOMER | Một trang đăng nhập chung cho storefront và admin |
| Sản phẩm | Danh sách, tìm kiếm, lọc danh mục/giá/giảm giá, sắp xếp, phân trang, chi tiết theo slug | Storefront đã nối API |
| Quản lý catalog | CRUD sản phẩm/danh mục theo quyền; upload ảnh, gallery, chọn thumbnail; kiểm tra version khi sửa | Admin đã nối API |
| Giỏ Customer | Xem, thêm, thay đổi số lượng, xóa; kiểm tra tồn kho và ghi đồng thời | Storefront đã nối API |
| Giỏ Guest | Lưu riêng trên trình duyệt, thêm/sửa/xóa không cần đăng nhập; backend xác định giá và tình trạng sản phẩm | Storefront đã nối API |
| Địa chỉ | Tạo/sửa/xóa/chọn mặc định, giới hạn 10 địa chỉ mỗi Customer, kiểm tra quyền sở hữu | Trang địa chỉ và checkout đã nối API |
| Checkout COD | Preview giá/phí ship, xác nhận đặt hàng, snapshot, trừ kho nguyên tử, chống tạo trùng bằng Idempotency-Key | Có luồng Customer và Guest |
| Đơn Customer | Danh sách, lọc trạng thái, phân trang, chi tiết và hủy đơn đủ điều kiện | Storefront đã nối API |
| Đơn Admin/Staff | Tìm kiếm/lọc tất cả đơn, chi tiết, lịch sử, chuyển trạng thái và xác nhận thu tiền COD | Admin đã nối API, xử lý cả đơn Guest |
| Tài khoản Customer | API cập nhật tên/số điện thoại, đổi mật khẩu và thu hồi toàn bộ phiên cũ | Chưa có trang quản lý hồ sơ/đổi mật khẩu riêng trong storefront |
| Quên mật khẩu | SMTP, reset token có hạn/dùng một lần, lưu hash, giới hạn yêu cầu, thu hồi phiên sau reset | Có trang quên mật khẩu và form reset do backend phục vụ |

Storefront và admin dùng chung cơ chế phiên đăng nhập. Logout đồng bộ các tab của cùng tài khoản trên cùng origin. ADMIN/STAFF có thể chuyển sang storefront bằng logo **Ogani Store** mà vẫn giữ tài khoản.

## Công nghệ sử dụng

| Thành phần | Công nghệ |
| --- | --- |
| Backend | Java 17, Spring Boot 4.1, Spring Web MVC |
| Database | PostgreSQL, Spring Data JPA, Hibernate, Flyway |
| Bảo mật | Spring Security, JWT, BCrypt |
| Validation và mapping | Bean Validation, MapStruct, Lombok |
| Frontend | HTML/CSS, JavaScript ES modules, Fetch API; admin dựa trên Spark Admin/Bootstrap |
| Dev server FE | Node.js, không cần cài thêm package để chạy |
| Email | Spring Mail, SMTP; Mailpit cho local |
| Tài liệu API | OpenAPI / Swagger UI |
| Kiểm thử và build | Maven, JUnit, Mockito, MockMvc, Node test runner; GitHub Actions cho backend |

## Cấu trúc thư mục

```text
ogani-project/
├── backend/
│   ├── src/main/java/com/ogani/store/
│   │   ├── auth/          # Đăng nhập, token, khôi phục mật khẩu
│   │   ├── catalog/       # Sản phẩm, danh mục, ảnh
│   │   ├── order/         # Giỏ hàng, checkout, đơn hàng, COD
│   │   ├── user/          # Tài khoản và địa chỉ
│   │   ├── security/      # JWT và phân quyền
│   │   ├── common/        # Response, exception, tiện ích dùng chung
│   │   └── content/       # Entity nội dung; các API còn đang phát triển
│   ├── src/main/resources/db/migration/  # Flyway migrations
│   ├── src/test/          # Unit/integration tests
│   ├── docker-compose.yml
│   └── pom.xml
├── storefront/            # Website mua hàng và dev server dùng chung
├── admin/                 # Giao diện quản trị
├── database/              # Schema/seed tham khảo
├── docs/                  # Checklist API, nghiệp vụ, báo cáo kiểm thử
├── .github/workflows/     # Backend CI
├── start-web.cmd          # Chạy FE trên Windows
└── README.md
```

Backend tổ chức theo module, với các lớp controller → service → repository và DTO/entity/mapper. **Flyway migrations là nguồn quản lý schema chính**; không cần import thêm các bản SQL tham khảo trong `database/` khi đã khởi tạo bằng Flyway.

## Cài đặt môi trường

### Yêu cầu

| Công cụ | Yêu cầu |
| --- | --- |
| JDK | 17 trở lên; cấu hình `JAVA_HOME` trỏ vào thư mục JDK |
| Maven | 3.9 trở lên; lệnh `mvn` có trong PATH |
| PostgreSQL | Có server đang chạy; Docker Compose dùng PostgreSQL 17, CI dùng PostgreSQL 16 |
| Node.js | 18 trở lên; lệnh `node` có trong PATH |
| IDE | IntelliJ IDEA cho backend, VS Code cho FE hoặc mở cả project bằng một IDE |
| Docker | Không bắt buộc; dùng nếu muốn chạy PostgreSQL/Mailpit bằng container |

Trên Windows, đặt `JAVA_HOME` là thư mục cài JDK (không phải thư mục `bin`), thêm `%JAVA_HOME%\bin` và thư mục `bin` của Maven vào PATH. Khi cài Node.js, bật tùy chọn thêm vào PATH. PostgreSQL cần chạy service và bạn cần nhớ mật khẩu đã đặt cho user `postgres`; cài thêm pgAdmin nếu muốn quản lý bằng giao diện.

Maven tích hợp trong IntelliJ có thể chạy được dù terminal chưa nhận lệnh `mvn`. Nếu chạy bằng terminal như hướng dẫn này, vẫn cần Maven có trong PATH.

Sau khi cài công cụ, mở terminal mới và kiểm tra:

```powershell
java -version
mvn -version
node --version
```

Các lệnh phía dưới chạy từ **thư mục gốc project**, nơi chứa `backend/`, `storefront/` và `admin/`. Không cần `npm install` cho frontend hiện tại.

### Khởi tạo database local

Trong pgAdmin hoặc psql, kết nối PostgreSQL và chạy từng câu lệnh sau riêng để tạo hai database:

```sql
CREATE DATABASE ogani_store;
CREATE DATABASE ogani_store_test;
```

- `ogani_store`: dữ liệu khi chạy ứng dụng local.
- `ogani_store_test`: chỉ dành cho integration test; không gộp với database ứng dụng.

Flyway tự tạo bảng và seed dữ liệu khi backend khởi động. Các bản cập nhật schema tiếp tục được áp dụng khi restart backend; hiện có migrations đến **V16** cho guest checkout.

Nếu dùng Docker thay PostgreSQL cài trực tiếp, từ thư mục gốc chạy:

```powershell
$env:DB_PASSWORD="mat-khau-postgres-local"
$env:JWT_SECRET="local-only-change-this-secret-at-least-32-characters"
docker compose -f backend/docker-compose.yml up -d db
```

PostgreSQL trong Compose được map ra **localhost:5433**. Khi chạy backend trên máy, đổi `DB_URL` sang `jdbc:postgresql://localhost:5433/ogani_store`. Container chỉ tạo sẵn `ogani_store`; nếu cần chạy test, tạo thêm `ogani_store_test` trên instance này và đổi `TEST_DB_URL` sang port 5433.

## Chạy project

### 1. Chạy backend

Mở terminal thứ nhất tại thư mục gốc, cấu hình đúng tài khoản PostgreSQL của bạn:

```powershell
$env:DB_URL="jdbc:postgresql://localhost:5432/ogani_store"
$env:DB_USER="postgres"
$env:DB_PASSWORD="mat-khau-postgres-cua-ban"
mvn -f backend/pom.xml spring-boot:run "-Dspring-boot.run.profiles=local"
```

Profile `local` có JWT secret phục vụ phát triển. Với môi trường khác, cung cấp secret riêng bằng `JWT_SECRET` và chọn profile phù hợp, không dùng secret local.

Sau khi ứng dụng khởi động:

- Backend: [http://localhost:8080](http://localhost:8080)
- Swagger: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- Health: [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health)

**Chạy bằng IntelliJ IDEA:** mở project, liên kết `backend/pom.xml` như một Maven project bằng **Add as Maven Project** nếu chưa nhận diện, rồi dùng **Maven → Sync Project**. Đặt Project SDK là JDK17+ và chạy `OganiStoreApplication` thuộc module backend, profile `local`. Nhập các biến DB ở Run Configuration → Environment variables. Cấu hình JUnit test dùng các biến `TEST_DB_*` riêng.

### 2. Chạy frontend dùng chung

Mở terminal thứ hai tại thư mục gốc:

```powershell
node storefront/dev-server.mjs
```

Trên Windows có thể dùng:

```powershell
.\start-web.cmd
```

Trong VS Code, mở **Terminal → New Terminal**, chọn PowerShell và chạy một trong hai lệnh trên. FE proxy `/api/*` về backend 8080; backend cần tiếp tục chạy ở terminal thứ nhất.

| Trang | URL |
| --- | --- |
| Cửa hàng | [http://localhost:5500/index.html](http://localhost:5500/index.html) |
| Đăng nhập chung | [http://localhost:5500/login.html](http://localhost:5500/login.html) |
| Dashboard Admin/Staff | [http://localhost:5500/](http://localhost:5500/) |

CUSTOMER đăng nhập vào storefront; ADMIN/STAFF vào dashboard. Không chạy `admin/` bằng server riêng, không mở HTML bằng `file://`. Dùng thống nhất `localhost` trong cả quá trình để tránh tách phiên với `127.0.0.1`.

Backend cũng có thể phục vụ riêng storefront tại [http://localhost:8080/shop/](http://localhost:8080/shop/), nhưng đây không phải host FE dùng chung với admin. Maven copy các file FE khi build, nên phải rebuild/restart backend để cập nhật bản này. Cách chạy Node 5500 ở trên là luồng chính cho phát triển cả hai giao diện.

### 3. Tài khoản demo

Flyway seed các tài khoản sau, cùng mật khẩu **`123456`**:

| Role | Email |
| --- | --- |
| ADMIN | `admin@ogani.local` |
| STAFF | `staff@ogani.local` |
| CUSTOMER | `eleanor@example.com` |

Dữ liệu demo chỉ dành cho local/test. Trước khi triển khai thực tế, thay thông tin đăng nhập và chuẩn bị chiến lược seed riêng.

## Cấu hình

Biến môi trường cần được đặt trong terminal chạy backend hoặc Run Configuration của IDE. File `backend/.env.example` là mẫu tham khảo; Spring Boot và lệnh Node hiện tại **không tự nạp file `.env`**. Docker Compose có cơ chế nạp env riêng.

| Biến | Ý nghĩa |
| --- | --- |
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | Kết nối database ứng dụng |
| `JWT_SECRET` | Secret ký JWT, ít nhất 32 ký tự; cung cấp riêng ngoài local |
| `SERVER_PORT` | Port backend; mặc định8080 |
| `TEST_DB_URL`, `TEST_DB_USER`, `TEST_DB_PASSWORD` | Kết nối database kiểm thử riêng |
| `STOREFRONT_PORT` | Port Node FE; mặc định5500 |
| `OGANI_API_TARGET` | Backend local mà Node FE proxy đến; mặc định `http://localhost:8080` |
| `OGANI_CURRENCY` | Tiền tệ hiển thị catalog/giỏ; mặc địnhUSD, cần khớp cấu hình backend |
| `APP_UPLOAD_DIRECTORY` | Thư mục lưu ảnh; mặc định `./uploads`, tương đối với working directory backend |
| `APP_UPLOAD_PUBLIC_ORIGIN` | Origin API phục vụ ảnh; mặc định `http://localhost:8080` |
| `CORS_ORIGINS` | Danh sách origin được phép khi gọi API khác origin |
| `SMTP_HOST`, `SMTP_PORT` | SMTP server; mặc địnhlocalhost:1025 |
| `SMTP_USERNAME`, `SMTP_PASSWORD`, `SMTP_AUTH` | Thông tin xác thực SMTP |
| `SMTP_STARTTLS`, `SMTP_SSL`, `SMTP_FROM` | TLS và địa chỉ email gửi |
| `PASSWORD_RESET_URL` | Trang nhận reset link; mặc định `http://localhost:8080/reset-password.html` |

Ví dụ đổi port FE/backend proxy, đặt trong terminal chạy FE:

```powershell
$env:STOREFRONT_PORT="5501"
$env:OGANI_API_TARGET="http://localhost:8081"
node storefront/dev-server.mjs
```

Backend phải được cấu hình chạy8081 tương ứng. Node proxy hiện chỉ chấp nhận backend HTTP local.

### Email local bằng Mailpit

Đặt `JWT_SECRET` như ví dụ Compose ở trên, sau đó chạy:

```powershell
docker compose -f backend/docker-compose.yml --profile mail up -d mailpit
```

Mailpit nhận SMTP ở localhost:1025 và hiển thị email tại [http://localhost:8025](http://localhost:8025). Backend chạy trực tiếp trên máy dùng các giá trị mặc định này. Test quên mật khẩu bằng tài khoản Customer, mở email trong Mailpit và dùng reset link. Mailpit giữ email local, không gửi đến hộp thư thật. Chi tiết cấu hình SMTP: [password recovery](docs/password-recovery-verification.md).

### Ảnh và dữ liệu lưu trữ

Upload hiện hỗ trợ PNG/JPEG, tối đa5 MB và 8 triệu pixel; backend kiểm tra và ghi lại ảnh với tên được sinh. Gallery quản lý thumbnail của sản phẩm. Các thao tác sửa sản phẩm/ảnh dùng version để tránh ghi đè thay đổi đồng thời; khi nhận409, tải lại dữ liệu trước khi sửa tiếp.

Khi triển khai, lưu ảnh vào thư mục bền vững và sao lưu cùng database. Không sửa/xóa migration Flyway đã chạy để cập nhật schema; tạo migration mới. Tác vụ maintenance dọn refresh token hết hạn và upload chưa từng được sử dụng; xem [backend/admin verification](docs/backend-admin-verification.md) và cấu hình trong source.

## Luồng nghiệp vụ chính

### Customer

1. Đăng nhập, xem/tìm sản phẩm và thêm vào giỏ.
2. Sửa số lượng hoặc xóa; số lượng0 cũng xóa sản phẩm.
3. Vào checkout, chọn địa chỉ đã lưu hoặc tạo địa chỉ mới.
4. Xem preview, kiểm tra giá/phí ship và xác nhận COD.
5. Xem đơn vừa tạo trong danh sách; có thể tự hủy khi **PENDING + UNPAID + COD**.

### Guest — không cần tài khoản

1. Xem sản phẩm, thêm/sửa/xóa giỏ trên trình duyệt.
2. Checkout yêu cầu tự nhập họ tên, email, số điện thoại, tỉnh/thành, quận/huyện, phường/xã và địa chỉ chi tiết.
3. Xác nhận COD và xem trang xác nhận đơn bằng chính phiên trình duyệt đã đặt.

Giỏ Guest và Customer **giữ riêng**, không merge khi đăng nhập. Guest không truy cập danh sách địa chỉ của tài khoản và checkout không tạo ngầm user/địa chỉ đã lưu. Đơn Guest có snapshot đầy đủ, `user_id=NULL`; quyền xem xác nhận dựa trên cookie HttpOnly của trình duyệt. Guest chưa có danh sách đơn hay chức năng tự hủy; liên hệ cửa hàng để thay đổi đơn.

### Admin/Staff

1. Đăng nhập qua trang chung, mở Orders để tìm/lọc và xem chi tiết.
2. Chuyển **PENDING → CONFIRMED → SHIPPING**.
3. Xác nhận thu tiền COD khi **SHIPPING**, cập nhật thanh toán thành **PAID**.
4. Chuyển **SHIPPING → COMPLETED** sau khi đã PAID.

Admin/Staff chỉ hủy ở PENDING hoặc CONFIRMED khi đáp ứng điều kiện thanh toán. CANCELLED/COMPLETED là trạng thái kết thúc. Hủy hoàn kho, hoàn tất ghi nhận số lượng bán và các tác động chỉ thực hiện một lần.

### Các quy tắc checkout

- Thêm vào giỏ chỉ kiểm tra tồn kho, chưa giữ chỗ. Backend kiểm tra lại sản phẩm, danh mục, giá và tồn kho khi đặt hàng.
- Phí ship hiện dựa trên **mức phí cố định và ngưỡng miễn phí** trong settings, chưa tính theo khoảng cách/khu vực/đơn vị vận chuyển.
- Giá sản phẩm, tên và địa chỉ được lưu snapshot; sửa catalog/địa chỉ sau này không sửa đơn cũ.
- FE tạo UUID `Idempotency-Key` cho một lần đặt hàng; retry sau timeout dùng lại đúng key/body. Backend lưu key/hash và trả lại đơn cũ thay vì tạo trùng.
- Hiện hỗ trợ COD; chưa hỗ trợ áp coupon hay thanh toán online. Giá sale của sản phẩm là tính năng khác với coupon.

## API và tài liệu

Swagger là nơi xem contract và thử request hiện tại. API dùng JSON response thống nhất, validation và xử lý lỗi tập trung; API cần đăng nhập gửi `Authorization: Bearer <accessToken>`.

| Nhóm | Endpoint chính |
| --- | --- |
| Auth | `/api/auth/register`, `/login`, `/me`, `/refresh`, `/logout`, `/forgot-password`, `/reset-password` dưới prefix `/api/auth` |
| Catalog public | `GET /api/products`, `GET /api/products/{slug}`, `GET /api/categories` |
| Catalog admin | `/api/admin/products`, `/api/admin/categories`, `/api/admin/products/{id}/images`, `/api/admin/uploads` |
| Giỏ Customer | `GET /api/cart`, `POST /api/cart/items`, `PUT/DELETE /api/cart/items/{itemId}` |
| Địa chỉ Customer | `GET /api/checkout/addresses`, `POST /api/addresses`, `PUT/DELETE /api/addresses/{id}`, `PATCH /api/addresses/{id}/default` |
| Checkout Customer | `POST /api/checkout/preview`, `POST /api/checkout` |
| Guest | `POST /api/guest/session`, `/api/guest/cart`, `/api/guest/checkout/preview`, `/api/guest/checkout`; `GET /api/guest/orders/{id}` |
| Đơn Customer | `GET /api/orders`, `GET /api/orders/{id}`, `POST /api/orders/{id}/cancel` |
| Đơn Admin/Staff | `GET /api/admin/orders`, `GET /api/admin/orders/{id}`, `PATCH /api/admin/orders/{id}/status`, `PATCH /api/admin/orders/{id}/payment-status` |
| Tài khoản Customer | `PATCH /api/users/me`, `PUT /api/users/me/password` |

Tài liệu chi tiết:

- [Checklist API theo feature](docs/api-feature-checklist.md): theo dõi API đã làm và đề xuất còn thiếu.
- [COD checkout](docs/cod-checkout-verification.md), [guest checkout và kiểm thử workflow](docs/guest-checkout-verification.md).
- [Admin order status](docs/admin-order-status-verification.md), [COD payment](docs/cod-payment-verification.md).
- [Tài khoản Customer](docs/customer-account-verification.md), [khôi phục mật khẩu](docs/password-recovery-verification.md).
- [Unified frontend](docs/unified-web-verification.md), [đồng bộ logout](docs/logout-sync-verification.md), [auth redirect](docs/auth-redirect-verification.md).

Các báo cáo kiểm thử ghi nhận trạng thái tại thời điểm thực hiện; một số báo cáo cũ mô tả phiên bản trước khi gộp frontend. Dùng hướng dẫn chạy trong README này cho cấu trúc hiện tại.

## Kiểm thử

### Backend

Từ thư mục gốc, đặt thông tin của database test riêng:

```powershell
$env:TEST_DB_URL="jdbc:postgresql://localhost:5432/ogani_store_test"
$env:TEST_DB_USER="postgres"
$env:TEST_DB_PASSWORD="mat-khau-postgres-cua-ban"
mvn -f backend/pom.xml test
mvn -f backend/pom.xml verify
```

`test` chạy unit tests; `verify` chạy thêm integration tests trên PostgreSQL thật. Không trỏ `TEST_DB_URL` vào database dev/production. Các test concurrency có commit yêu cầu tên database kết thúc bằng `_test` và tự dọn fixture. Trong IntelliJ, cấu hình `TEST_DB_*` trên JUnit Run Configuration, không chỉ trên cấu hình chạy ứng dụng.

### Frontend

```powershell
node --test admin/tests/history.test.mjs storefront/tests/*.test.mjs backend/src/test/javascript/password-reset.test.mjs
node --input-type=module -e "import('./admin/tests/api.test.mjs').then(async m => console.log(await m.run()))"
node --input-type=module -e "import('./admin/tests/product-editor.test.mjs').then(async m => console.log(await m.run()))"
```

Báo cáo regression ngày 07/11/2026 ghi nhận **25 unit + 709 integration tests backend** và **84 tình huống FE** đều qua. Luồng Guest đặt COD, Customer đặt/hủy và Admin thu tiền/hoàn tất đã được kiểm tra bằng browser trên database test. Xem [báo cáo và giới hạn kiểm thử](docs/guest-checkout-verification.md). Số lượng test có thể tăng khi phát triển tiếp.

## Tính năng đang phát triển

Danh sách dưới đây là roadmap, chưa phải cam kết đã có API hoạt động:

- [ ] Coupon: áp/bỏ mã, điều kiện sử dụng, hạn mức và quản lý ở Admin.
- [ ] Thanh toán online/sandbox và xử lý callback thanh toán.
- [ ] Hoàn tiền, giao thất bại, trả hàng và các quy tắc tồn kho liên quan.
- [ ] Phí vận chuyển theo khu vực/khoảng cách hoặc tích hợp đơn vị giao hàng.
- [ ] Email xác nhận đơn và thông báo thay đổi trạng thái.
- [ ] Trang hồ sơ Customer và đổi mật khẩu kết nối các API đã có.
- [ ] Quản lý người dùng ở Admin: tìm kiếm, khóa/mở, thay role theo quyền.
- [ ] CRUD cấu hình cửa hàng, phí ship/ngưỡng miễn phí qua Admin.
- [ ] Dashboard sử dụng số liệu doanh thu, đơn hàng, sản phẩm từ API thật.
- [ ] Wishlist, đánh giá sản phẩm và kiểm duyệt.
- [ ] Blog, banner, liên hệ, newsletter và API cho các trang preview tương ứng.

Database đã có entity cho một số nhóm trên. Dashboard và các trang quản trị chưa kết nối API không được dùng làm bằng chứng dữ liệu nghiệp vụ thật. Theo dõi endpoint và business case chi tiết tại [API checklist](docs/api-feature-checklist.md).

## Xử lý lỗi thường gặp

| Hiện tượng | Cách kiểm tra |
| --- | --- |
| `node` hoặc `mvn` is not recognized | Cài đúng công cụ, thêm PATH, đóng/mở lại VS Code/IntelliJ và terminal; chạy các lệnh kiểm tra phiên bản |
| Không tìm thấy main class, `pom.xml` bị gạch | Kiểm tra Maven project có bị Ignore không; bỏ Ignore, liên kết `backend/pom.xml`, Sync Project, chọn SDK và module backend cho Run Configuration |
| Port 5500 đang được sử dụng | Dừng Live Server/server FE cũ hoặc đổi `STOREFRONT_PORT`; không chạy hai server cùng port |
| FE báo không kết nối được backend | Kiểm tra backend 8080/health, log kết nối DB và `OGANI_API_TARGET` |
| API guest trả404 sau khi cập nhật code | Rebuild/restart backend để nhận controller mới và Flyway V16; tải lại FE |
| Test yêu cầu database `*_test` | Tạo `ogani_store_test`, đặt đúng `TEST_DB_*` trên tiến trình/JUnit đang chạy; không bỏ guard |
| Đăng nhập hoặc logout không đồng nhất giữa URL | Dùng duy nhất host/port FE chung, không trộn localhost/127.0.0.1, server admin riêng hoặc file:// |
| Checkout trả409 | Kiểm tra lại preview vì giá/tồn kho/phí ship có thể đổi; cùng key nhưng body khác cũng trả409 |
| Không nhận email reset local | Kiểm tra Mailpit 1025/8025 và cấu hình SMTP; phản hồi forgot-password không tiết lộ email có tồn tại hay việc gửi thành công |

## Đóng góp và ghi nhận

Khi bổ sung feature, cập nhật business rules, validation/quyền truy cập, migration nếu đổi schema, kiểm thử các luồng liên quan và checklist/tài liệu. Không đưa credential, token, file `.env` hoặc ảnh upload cá nhân lên Git.

- Storefront được triển khai bằng HTML/CSS/JS với ngôn ngữ hình ảnh cửa hàng Ogani; không bao gồm nguyên bộ source template storefront gốc.
- Admin dựa trên **Spark Admin của ThemeWagon**; template có giấy phép MIT.
- Một số ảnh mẫu có nguồn từ template Ogani của Colorlib.
- Repository chưa có file LICENSE riêng cho toàn bộ project; không suy diễn giấy phép của template thành giấy phép cho toàn bộ mã nguồn.
