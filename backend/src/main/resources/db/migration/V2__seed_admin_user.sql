-- Seed admin user (password: Admin123!)
-- BCrypt hash generated with cost factor 10
INSERT INTO users (email, password_hash, name, role, created_at, updated_at)
VALUES ('admin@cryptointel.com', '$2a$10$tLbj8bqK/uFN5bpeJd123OtScc.VrYfTI6.vaqXWDJjjZB./wlq0a', 'Platform Admin', 'ADMIN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO user_balances (user_id, balance)
SELECT id, 10000.00 FROM users WHERE email = 'admin@cryptointel.com';
