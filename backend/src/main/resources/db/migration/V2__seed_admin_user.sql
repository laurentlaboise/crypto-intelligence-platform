-- Seed admin user (password: Admin123!)
-- BCrypt hash for 'Admin123!' with default strength
INSERT INTO users (email, password_hash, name, role, created_at, updated_at)
VALUES ('admin@cryptointel.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'Platform Admin', 'ADMIN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO user_balances (user_id, balance)
VALUES (1, 10000.00);
