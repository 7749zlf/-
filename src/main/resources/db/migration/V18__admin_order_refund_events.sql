CREATE TABLE admin_order_events (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_id VARCHAR(40) NOT NULL,
  event_type VARCHAR(64) NOT NULL,
  from_status VARCHAR(32) NULL,
  to_status VARCHAR(32) NOT NULL,
  amount DECIMAL(10, 2) NULL,
  reason VARCHAR(512) NOT NULL DEFAULT '',
  actor_username VARCHAR(128) NOT NULL DEFAULT 'system',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_admin_order_events_order_created
  ON admin_order_events (order_id, created_at, id);
