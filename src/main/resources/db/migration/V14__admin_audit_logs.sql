CREATE TABLE admin_audit_logs (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  actor_id BIGINT NULL,
  actor_username VARCHAR(64) NOT NULL DEFAULT 'system',
  action VARCHAR(64) NOT NULL,
  target_type VARCHAR(64) NOT NULL,
  target_id VARCHAR(128) NOT NULL,
  details JSON NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_admin_audit_actor_created (actor_id, created_at),
  INDEX idx_admin_audit_target_created (target_type, target_id, created_at),
  INDEX idx_admin_audit_action_created (action, created_at)
);
