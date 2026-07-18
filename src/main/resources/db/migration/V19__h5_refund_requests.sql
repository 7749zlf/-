CREATE TABLE h5_refund_requests (
  id VARCHAR(40) PRIMARY KEY,
  order_id VARCHAR(40) NOT NULL,
  user_id BIGINT NOT NULL,
  amount DECIMAL(10, 2) NOT NULL,
  reason VARCHAR(512) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING_REVIEW',
  review_reason VARCHAR(512) NULL,
  reviewer_username VARCHAR(128) NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  reviewed_at TIMESTAMP NULL,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_refund_requests_order FOREIGN KEY (order_id) REFERENCES orders(id),
  CONSTRAINT fk_refund_requests_user FOREIGN KEY (user_id) REFERENCES app_users(id)
);

CREATE INDEX idx_h5_refund_requests_order_created
  ON h5_refund_requests (order_id, created_at, id);

CREATE INDEX idx_h5_refund_requests_user_status
  ON h5_refund_requests (user_id, status, created_at);
