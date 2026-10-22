# Ogani Store – Backend (Spring Boot 4)

Java 17+ · **Spring Boot 4.1** (Spring Framework 7, Security 7, Hibernate 7, Jackson 3) · Spring Data JPA · **PostgreSQL** · Flyway · JWT · MapStruct · springdoc 3 · Testcontainers 2

> **Trạng thái:** khung để vừa học vừa làm. **Chưa được build** cùng thư viện thật (môi trường tạo ra nó không có Maven/Docker).
> Việc đầu tiên: `mvn verify`, đọc lỗi (nếu có) và sửa. Phiên bản `4.1.0` là bản mới nhất mình xác nhận được – bạn có thể nâng lên patch mới hơn của 4.1.x.

## Chạy nhanh (PostgreSQL + pgAdmin 4)
1. Mở **pgAdmin 4** → chuột phải *Databases* → *Create → Database…* → tên **`ogani_store`** → Save. Để **rỗng**, không tạo bảng.
2. Đưa thông tin kết nối cho app. Mặc định app dùng `localhost:5432`, user `postgres`, mật khẩu `postgres`. Nếu mật khẩu của bạn khác, đặt biến môi trường `DB_PASSWORD` (và `DB_USER`, `DB_URL` nếu cần):
   - IntelliJ: *Run → Edit Configurations → Environment variables* → `DB_PASSWORD=mat-khau-cua-ban`
   - PowerShell: `$env:DB_PASSWORD="mat-khau-cua-ban"; mvn spring-boot:run`
   - Linux/macOS: `DB_PASSWORD=mat-khau-cua-ban mvn spring-boot:run`
3. Chạy `mvn spring-boot:run` (hoặc chạy `OganiStoreApplication` trong IntelliJ). Flyway tự tạo 24 bảng + dữ liệu mẫu.
4. Quay lại pgAdmin → *ogani_store → Schemas → public → Tables* (chuột phải → Refresh) sẽ thấy các bảng; có thêm bảng `flyway_schema_history` ghi lại các migration đã chạy.
5. Swagger UI: http://localhost:8080/swagger-ui.html

**Không có PostgreSQL trên máy?** Dùng Docker: `docker compose up db` (PostgreSQL ở cổng **5433**, nên đặt `DB_URL=jdbc:postgresql://localhost:5433/ogani_store`), hoặc `docker compose up --build` để chạy cả app.

> File `.env` chỉ được `docker compose` đọc; `mvn spring-boot:run` **không** tự đọc `.env`, nên dùng biến môi trường như bước 2.

Tài khoản seed (mật khẩu `123456`): `admin@ogani.local` (ADMIN), `staff@ogani.local` (STAFF), `eleanor@example.com` (CUSTOMER).

## Cái gì đã có / cái gì để bạn tự làm
Database **đã đủ 24 bảng** (Flyway V1–V3) và **đủ entity** (bảng nối `blog_post_tags` nằm trong `BlogPost`). Việc của bạn ở mỗi module là viết `repository → dto → mapper → service → controller → test`.

| Module | Đã có (làm mẫu) | Để bạn làm (bảng trong DB) |
|---|---|---|
| `common`, `security`, `auth` | Response/Exception chung, JWT, register/login/refresh/logout/me | – |
| `user` | Entity: `Role`, `User`, `RefreshToken`, `CustomerAddress` | repository/service/controller cho địa chỉ giao hàng, quản lý khách hàng |
| `catalog` | Entity đủ 5 bảng + **toàn bộ lát cắt sản phẩm** (public + admin) | repository/service/controller cho ảnh, review, wishlist; CRUD category |
| `order` | **Entity đủ** (`Cart`, `CartItem`, `Coupon`, `Order`, `OrderItem`, `OrderStatusHistory`, enum) | repository, DTO, mapper, service, controller; state machine cho `OrderStatus` |
| `content` | **Entity đủ** (blog, banner, liên hệ, newsletter, settings) | repository, DTO, mapper, service, controller |

Package trống (repository, service, controller, dto, mapper của `order`/`content`...) có `package-info.java` ghi sẵn việc cần làm.

**Lát cắt sản phẩm là khuôn mẫu** – đọc theo thứ tự này để hiểu cách mọi thứ nối nhau:
`ProductController` → `ProductServiceImpl` → `ProductSpecifications` / `ProductRepository` → `Product` (entity) → `ProductMapper` → `ProductRequest/Response`.

## Thứ tự làm gợi ý
1. **Cart** (`/api/cart`) – dễ nhất, quen với quan hệ nhiều-nhiều qua bảng trung gian.
2. **Coupon** – validate hạn dùng, giá trị tối thiểu, giới hạn lượt dùng, trần giảm.
3. **Checkout** – phần đáng giá nhất cho CV: *một transaction* gồm kiểm tra tồn kho, trừ kho (dùng `@Version` có sẵn trên `Product`), snapshot giá + địa chỉ vào `order_items`/`orders`, ghi `order_status_history`.
4. **Order state machine** – chỉ cho phép PENDING → CONFIRMED → SHIPPING → COMPLETED (PENDING/CONFIRMED → CANCELLED). Tự viết trong enum + unit test.
5. Review/Wishlist → Blog/Contact/Newsletter/Settings/Banner → thanh toán sandbox → Redis cache, email `@Async`.

## API đang có
| Method | Path | Quyền |
|---|---|---|
| POST | `/api/auth/register` `/login` `/refresh` `/logout` | public |
| GET | `/api/auth/me` | đã đăng nhập |
| GET | `/api/categories` | public |
| GET | `/api/products?keyword&categoryId&minPrice&maxPrice&onSale&featured&sort&page&size` | public |
| GET | `/api/products/{slug}` | public |
| GET/POST/PUT | `/api/admin/products[/{id}]` | ADMIN, STAFF |
| DELETE | `/api/admin/products/{id}` | ADMIN |

## Spring Boot 4 – những chỗ khác Boot 3 đã áp dụng trong khung
- Starter đổi tên/tách: `spring-boot-starter-webmvc` (thay `-web`), `spring-boot-starter-flyway` (bắt buộc, không chỉ `flyway-core`), mỗi công nghệ có starter test riêng (`...-webmvc-test`, `...-security-test`).
- `@AutoConfigureMockMvc` chuyển sang `org.springframework.boot.webmvc.test.autoconfigure`.
- **Jackson 3**: package `tools.jackson.*` (riêng annotation vẫn là `com.fasterxml.jackson.annotation`). Test không dùng `ObjectMapper` nên tránh được; khi bạn cần JSON trong code mới, import từ `tools.jackson`.
- **Testcontainers 2**: artifact `testcontainers-postgresql`, class `org.testcontainers.postgresql.PostgreSQLContainer` (không còn generic `<?>`).
- Hibernate 7, Spring Security 7, springdoc 3.x.
- `jjwt` vẫn dùng Jackson 2 bên trong (độc lập với Jackson 3 của Spring) – bình thường.

## Quyết định thiết kế (nói được khi phỏng vấn)
Refresh-token rotation lưu SHA-256 hash · `@Version` optimistic lock cho `products` · sort whitelist (`ProductSort`) · entity không ra thẳng API (DTO + MapStruct) · `open-in-view: false` · lỗi thống nhất qua `GlobalExceptionHandler` · schema do Flyway quản lý, `ddl-auto: none`.

## Test
`mvn test` – unit test (`SlugUtilTest`, `ProductServiceTest`). `mvn verify` – thêm `ProductApiIT` (Testcontainers chạy Flyway thật trên PostgreSQL rồi gọi API; cần Docker, không dùng database trong pgAdmin của bạn).

## Ghi chú riêng cho PostgreSQL
- **Enum** (status, payment_method...) lưu dạng `VARCHAR` + `CHECK`, không dùng kiểu ENUM của PostgreSQL – đơn giản hơn khi map với `@Enumerated(EnumType.STRING)`.
- **Khoá chính** dùng `GENERATED BY DEFAULT AS IDENTITY`. Seed chèn id có sẵn nên cuối `V2__seed_data.sql` có đoạn đồng bộ lại bộ đếm; nếu bạn tự thêm seed có id tường minh, nhớ chạy lại đoạn đó.
- PostgreSQL **không có** `ON UPDATE CURRENT_TIMESTAMP`: cột `updated_at` do Hibernate (`@UpdateTimestamp`) cập nhật, nên sửa trực tiếp bằng SQL trong pgAdmin sẽ không tự đổi `updated_at`.
- Ô tìm kiếm tên sản phẩm hiện dùng `LIKE`; đã có sẵn index GIN full-text (`ft_prod_name`) nếu sau này bạn muốn nâng cấp.
