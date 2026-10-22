-- Dữ liệu mẫu bám theo nội dung demo của Ogani.
-- Mật khẩu của TẤT CẢ tài khoản mẫu: 123456 (BCrypt)

INSERT INTO roles (id, name) VALUES (1,'ADMIN'), (2,'STAFF'), (3,'CUSTOMER');

INSERT INTO users (id, role_id, email, password_hash, first_name, last_name, phone, status) VALUES
(1,1,'admin@ogani.local',   '$2b$10$AaIc0jnieWMo8WDBjK4pWOqNnHeYO8tuCofHkGBEpuYVWb1mTK3bC','Michael','Scofield','0900000001','ACTIVE'),
(2,2,'staff@ogani.local',   '$2b$10$AaIc0jnieWMo8WDBjK4pWOqNnHeYO8tuCofHkGBEpuYVWb1mTK3bC','Linda','Burrows','0900000002','ACTIVE'),
(3,3,'eleanor@example.com', '$2b$10$AaIc0jnieWMo8WDBjK4pWOqNnHeYO8tuCofHkGBEpuYVWb1mTK3bC','Eleanor','Pena','0911111111','ACTIVE'),
(4,3,'wade@example.com',    '$2b$10$AaIc0jnieWMo8WDBjK4pWOqNnHeYO8tuCofHkGBEpuYVWb1mTK3bC','Wade','Warren','0922222222','ACTIVE'),
(5,3,'jane@example.com',    '$2b$10$AaIc0jnieWMo8WDBjK4pWOqNnHeYO8tuCofHkGBEpuYVWb1mTK3bC','Jane','Cooper','0933333333','ACTIVE');

INSERT INTO customer_addresses (user_id, recipient_name, phone, country, address_line, city, state, postcode, is_default) VALUES
(3,'Eleanor Pena','0911111111','Vietnam','12 Nguyen Hue','Ho Chi Minh','HCM','700000',TRUE),
(4,'Wade Warren','0922222222','Vietnam','45 Tran Phu','Da Nang','DN','550000',TRUE),
(5,'Jane Cooper','0933333333','Vietnam','8 Le Loi','Ha Noi','HN','100000',TRUE);

-- Categories = "All departments" của Ogani
INSERT INTO categories (id, name, slug, image_url, sort_order) VALUES
(1,'Fresh Meat','fresh-meat',NULL,1),
(2,'Vegetables','vegetables',NULL,2),
(3,'Fruit & Nut Gifts','fruit-nut-gifts',NULL,3),
(4,'Fresh Berries','fresh-berries',NULL,4),
(5,'Ocean Foods','ocean-foods',NULL,5),
(6,'Butter & Eggs','butter-eggs',NULL,6),
(7,'Fastfood','fastfood',NULL,7),
(8,'Fresh Onion','fresh-onion',NULL,8),
(9,'Papaya & Crisps','papaya-crisps',NULL,9),
(10,'Oatmeal','oatmeal',NULL,10),
(11,'Fresh Bananas','fresh-bananas',NULL,11);

INSERT INTO products (id, category_id, name, slug, sku, short_description, description, information, price, sale_price,
                      weight_value, weight_unit, stock_quantity, shipping_info, thumbnail_url, status, is_featured,
                      avg_rating, review_count, sold_count) VALUES
(1,2,'Vegetable''s Package','vegetables-package','VEG-001','Mixed seasonal vegetables, washed and packed.','Fresh mixed vegetables picked from our partner farms.','Grown without synthetic pesticides.',50.0,NULL,0.5,'kg',8,'01 day shipping. Free pickup today','img/product/details/product-details-1.jpg','ACTIVE',TRUE,4.5,2,120),
(2,2,'Fresh Garden Vegetable','fresh-garden-vegetable','VEG-002','Crunchy bell peppers from the garden.','Fresh vegetables harvested the same morning.','Keep refrigerated.',39.0,35.0,1,'kg',60,'01 day shipping. Free pickup today','img/product/details/product-details-2.jpg','ACTIVE',TRUE,4.0,1,85),
(3,11,'Organic Bananas','organic-bananas','FRU-001','Sweet organic bananas.','Organic bananas from certified farms.','Store at room temperature.',69.0,NULL,1,'kg',45,'01 day shipping.','img/product/product-2.jpg','ACTIVE',TRUE,5.0,1,60),
(4,3,'Raisin''n''nuts','raisin-n-nuts','NUT-001','Dried fruit and mixed nuts.','Healthy snack mix.','Contains tree nuts.',36.0,30.0,0.4,'kg',100,'01 day shipping.','img/product/details/thumb-4.jpg','ACTIVE',FALSE,4.0,0,40),
(5,3,'Mixed Fruits','mixed-fruits','NUT-002','A basket of mixed seasonal fruits.','Seasonal fruit basket, ideal as a gift.','Keep in a cool place.',36.0,30.0,1.5,'kg',90,'01 day shipping.','img/product/product-12.jpg','ACTIVE',FALSE,0,0,22),
(6,1,'Fresh Beef Steak','fresh-beef-steak','MEA-001','Tender diced beef.','Premium cut, chilled.','Keep at 0-4°C.',18.0,NULL,0.5,'kg',30,'Same-day delivery.','img/product/product-1.jpg','ACTIVE',FALSE,0,0,15),
(7,3,'Watermelon Slices','watermelon-slices','FRU-002','Juicy red watermelon.','Seedless watermelon, sliced.','Keep refrigerated.',30.0,NULL,1,'kg',0,'Same-day delivery.','img/product/product-7.jpg','OUT_OF_STOCK',FALSE,0,0,9),
(8,3,'Red Apples','red-apples','FRU-003','Crisp red apples.','Sweet and crunchy apples.','Wash before eating.',8.5,NULL,1,'kg',200,'01 day shipping.','img/product/product-8.jpg','ACTIVE',FALSE,0,0,70),
(9,4,'Black Grapes','black-grapes','BER-001','Sweet seedless black grapes.','Hand-picked grapes.','Refrigerate.',12.0,10.0,0.5,'kg',25,'01 day shipping.','img/product/product-4.jpg','ACTIVE',TRUE,0,0,33),
(10,7,'Fried Chicken Leg','fried-chicken-leg','FAS-001','Crispy fried chicken leg.','Freshly fried, served hot.','Best eaten the same day.',3.5,NULL,0.15,'kg',300,'Same-day delivery.','img/product/product-10.jpg','ACTIVE',FALSE,0,0,12),
(11,7,'Double Burger','double-burger','FAS-002','Double beef burger.','Two beef patties, cheese and salad.','Best eaten the same day.',6.0,NULL,0.3,'kg',80,'Same-day delivery.','img/product/product-5.jpg','ACTIVE',FALSE,0,0,18),
(12,3,'Orange Juice 1L','orange-juice-1l','DRI-001','100% pure orange juice.','Not from concentrate.','Keep refrigerated.',7.5,NULL,1,'l',5,'01 day shipping.','img/product/product-11.jpg','ACTIVE',FALSE,0,0,28);

INSERT INTO product_images (product_id, image_url, sort_order) VALUES
(1,'img/product/details/product-details-1.jpg',1),(1,'img/product/details/thumb-1.jpg',2),(1,'img/product/details/thumb-2.jpg',3);

INSERT INTO product_reviews (product_id, user_id, rating, comment, status) VALUES
(1,3,5,'Very fresh, arrived the next morning.','APPROVED'),
(1,4,4,'Good quality, a bit pricey.','APPROVED'),
(2,5,4,'Tasty vegetables.','APPROVED'),
(3,3,5,'Best bananas I have bought online.','APPROVED'),
(9,4,3,'Some grapes were soft.','PENDING');

INSERT INTO coupons (id, code, description, discount_type, discount_value, min_order_value, max_discount, usage_limit, used_count, starts_at, expires_at) VALUES
(1,'WELCOME10','10% off for new customers','PERCENT',10,50,20,500,1,'2026-01-01 00:00:00','2026-12-31 23:59:59'),
(2,'FREESHIP','Flat $5 off','FIXED',5,30,NULL,NULL,0,'2026-01-01 00:00:00','2026-12-31 23:59:59');

INSERT INTO orders (id, order_code, user_id, first_name, last_name, email, phone, ship_country, ship_address, ship_city, ship_state, ship_postcode,
                    order_notes, subtotal, discount_amount, shipping_fee, total, coupon_id, coupon_code, payment_method, payment_status, order_status, created_at) VALUES
(1,'ORD-20261010-0001',3,'Eleanor','Pena','eleanor@example.com','0911111111','Vietnam','12 Nguyen Hue','Ho Chi Minh','HCM','700000',NULL,169.00,0,0,169.00,NULL,NULL,'COD','PAID','COMPLETED','2026-10-10 09:15:00'),
(2,'ORD-20261015-0002',4,'Wade','Warren','wade@example.com','0922222222','Vietnam','45 Tran Phu','Da Nang','DN','550000','Call before delivery',90.00,0,5.00,95.00,NULL,NULL,'PAYPAL','PAID','SHIPPING','2026-10-15 14:30:00'),
(3,'ORD-20261017-0003',5,'Jane','Cooper','jane@example.com','0933333333','Vietnam','8 Le Loi','Ha Noi','HN','100000',NULL,80.00,8.00,5.00,77.00,1,'WELCOME10','COD','UNPAID','PENDING','2026-10-17 18:05:00');

INSERT INTO order_items (order_id, product_id, product_name, unit_price, quantity, line_total) VALUES
(1,1,'Vegetable''s Package',50.00,2,100.00),(1,3,'Organic Bananas',69.00,1,69.00),
(2,4,'Raisin''n''nuts',30.00,3,90.00),
(3,2,'Fresh Garden Vegetable',35.00,2,70.00),(3,9,'Black Grapes',10.00,1,10.00);

INSERT INTO order_status_history (order_id, from_status, to_status, note, changed_by, changed_at) VALUES
(1,NULL,'PENDING','Order placed',NULL,'2026-10-10 09:15:00'),(1,'PENDING','CONFIRMED','Confirmed by staff',2,'2026-10-10 09:40:00'),
(1,'CONFIRMED','SHIPPING','Handed to courier',2,'2026-10-10 13:00:00'),(1,'SHIPPING','COMPLETED','Delivered',2,'2026-10-11 10:20:00'),
(2,NULL,'PENDING','Order placed',NULL,'2026-10-15 14:30:00'),(2,'PENDING','CONFIRMED','PayPal payment received',1,'2026-10-15 14:45:00'),
(2,'CONFIRMED','SHIPPING','Handed to courier',1,'2026-10-16 08:00:00'),
(3,NULL,'PENDING','Order placed',NULL,'2026-10-17 18:05:00');

INSERT INTO blog_categories (id, name, slug) VALUES (1,'Food','food'),(2,'Beauty','beauty'),(3,'Life Style','life-style'),(4,'Travel','travel');
INSERT INTO blog_tags (id, name, slug) VALUES (1,'Apple','apple'),(2,'Vegetables','vegetables'),(3,'Fruit','fruit'),(4,'Healthy Food','healthy-food'),(5,'Cooking','cooking'),(6,'Lifestyle','lifestyle');

INSERT INTO blog_posts (id, author_id, category_id, title, slug, excerpt, content, thumbnail_url, status, view_count, published_at) VALUES
(1,1,1,'Cooking tips make cooking simple','cooking-tips-make-cooking-simple','Simple tips that save time in the kitchen.','<p>Full article content...</p>','img/blog/blog-1.jpg','PUBLISHED',320,'2026-05-04 08:00:00'),
(2,1,1,'6 ways to prepare breakfast for 30','6-ways-to-prepare-breakfast-for-30','Feeding a crowd without stress.','<p>Full article content...</p>','img/blog/blog-2.jpg','PUBLISHED',210,'2026-05-04 08:00:00'),
(3,1,3,'Visit the clean farm in the US','visit-the-clean-farm','A trip to an organic farm.','<p>Full article content...</p>','img/blog/blog-3.jpg','PUBLISHED',150,'2026-05-04 08:00:00'),
(4,2,1,'09 Kinds Of Vegetables Protect The Liver','kinds-of-vegetables-protect-the-liver','Vegetables that are good for the liver.','<p>Draft...</p>','img/blog/blog-4.jpg','DRAFT',0,NULL);
INSERT INTO blog_post_tags (post_id, tag_id) VALUES (1,5),(1,4),(2,5),(3,2),(3,6),(4,2),(4,4);
INSERT INTO blog_comments (post_id, user_id, author_name, author_email, content, status) VALUES
(1,3,'Eleanor Pena','eleanor@example.com','Great tips, thanks!','APPROVED'),
(1,NULL,'Guest','guest@example.com','Buy cheap watches here!!!','SPAM'),
(3,4,'Wade Warren','wade@example.com','Where exactly is this farm?','PENDING');

INSERT INTO banners (position, title, subtitle, image_url, link_url, sort_order) VALUES
('HERO','Fruit Fresh Vegetable 100% Organic','Free Pickup and Delivery Available','img/hero/banner.jpg','/shop',1),
('HOME_SALE','Sale Off -20%','Dried fruit and vegetables','img/banner/banner-1.jpg','/shop?sale=1',2);

INSERT INTO contact_messages (name, email, message, status) VALUES
('Alice Nguyen','alice@example.com','Do you deliver to Hue?','NEW'),
('Bob Tran','bob@example.com','Can I change my delivery address?','READ');
INSERT INTO newsletter_subscribers (email) VALUES ('news1@example.com'),('news2@example.com'),('news3@example.com');

INSERT INTO settings (setting_key, setting_value, description) VALUES
('store_name','Ogani','Tên cửa hàng'),
('phone','+65 11.188.888','Hotline hiển thị ở header/footer'),
('support_time','support 24/7 time','Dòng nhỏ dưới hotline'),
('email','hello@colorlib.com','Email liên hệ'),
('address','60-49 Road 11378 New York','Địa chỉ cửa hàng'),
('open_time','10:00 am to 23:00 pm','Giờ mở cửa (trang Contact)'),
('free_shipping_threshold','99','Miễn phí ship cho đơn từ mức này'),
('shipping_fee','5','Phí ship khi chưa đạt ngưỡng miễn phí'),
('currency','USD','Đơn vị tiền tệ');

-- Seed chèn id tường minh nên phải đồng bộ lại bộ đếm identity, nếu không INSERT mới sẽ trùng id=1.
DO $$
DECLARE r record;
BEGIN
  FOR r IN SELECT table_name FROM information_schema.columns
           WHERE table_schema = 'public' AND column_name = 'id' AND is_identity = 'YES'
  LOOP
    EXECUTE format('SELECT setval(pg_get_serial_sequence(%L, ''id''), COALESCE((SELECT MAX(id) FROM %I), 0) + 1, false)',
                   r.table_name, r.table_name);
  END LOOP;
END $$;
