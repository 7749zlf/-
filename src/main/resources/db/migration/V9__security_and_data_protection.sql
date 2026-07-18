ALTER TABLE app_users
  MODIFY COLUMN phone VARCHAR(512) NULL,
  MODIFY COLUMN email VARCHAR(512) NOT NULL DEFAULT '',
  ADD COLUMN phone_hash VARCHAR(64) NULL AFTER phone,
  ADD COLUMN email_hash VARCHAR(64) NULL AFTER email;

CREATE INDEX idx_app_users_phone_hash
  ON app_users (phone_hash);

CREATE INDEX idx_app_users_email_hash
  ON app_users (email_hash);

CREATE TABLE admin_permissions (
  permission_key VARCHAR(64) PRIMARY KEY,
  permission_name VARCHAR(64) NOT NULL,
  description VARCHAR(255) NOT NULL DEFAULT '',
  sort_order INT NOT NULL DEFAULT 0,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE admin_roles (
  role_key VARCHAR(64) PRIMARY KEY,
  role_name VARCHAR(64) NOT NULL,
  description VARCHAR(255) NOT NULL DEFAULT '',
  status VARCHAR(32) NOT NULL DEFAULT 'ENABLED',
  sort_order INT NOT NULL DEFAULT 0,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE admin_role_permissions (
  role_key VARCHAR(64) NOT NULL,
  permission_key VARCHAR(64) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (role_key, permission_key),
  CONSTRAINT fk_admin_role_permissions_role FOREIGN KEY (role_key) REFERENCES admin_roles(role_key),
  CONSTRAINT fk_admin_role_permissions_permission FOREIGN KEY (permission_key) REFERENCES admin_permissions(permission_key)
);

CREATE INDEX idx_admin_users_role_key
  ON admin_users (role_key);

INSERT INTO admin_permissions (permission_key, permission_name, description, sort_order) VALUES
  ('dashboard', 'Dashboard', 'View business overview and core metrics', 10),
  ('content', 'Content', 'Manage dramas, episodes, and publishing state', 20),
  ('storyline', 'Storyline', 'Manage storyline pools, weights, and unlock settings', 30),
  ('orders', 'Orders', 'View and operate payment orders and entitlements', 40),
  ('channels', 'Channels', 'Manage acquisition channels and campaigns', 50),
  ('media', 'Media', 'Manage media assets and upload review', 60),
  ('finance', 'Finance', 'View finance reports and profit data', 70),
  ('users', 'Users', 'Manage user state, levels, and risk controls', 80),
  ('roles', 'Roles', 'Manage admin roles and permissions', 90),
  ('settings', 'Settings', 'Manage system configuration and high-risk writes', 100);

INSERT INTO admin_roles (role_key, role_name, description, status, sort_order) VALUES
  ('administrator', 'Administrator', 'Full platform administrator', 'ENABLED', 10),
  ('content_manager', 'Content Manager', 'Content, storyline, and media operations', 'ENABLED', 20),
  ('operations_manager', 'Operations Manager', 'Orders, channels, and finance operations', 'ENABLED', 30),
  ('support_manager', 'Support Manager', 'User support and order lookup', 'ENABLED', 40);

INSERT INTO admin_role_permissions (role_key, permission_key) VALUES
  ('administrator', 'dashboard'),
  ('administrator', 'content'),
  ('administrator', 'storyline'),
  ('administrator', 'orders'),
  ('administrator', 'channels'),
  ('administrator', 'media'),
  ('administrator', 'finance'),
  ('administrator', 'users'),
  ('administrator', 'roles'),
  ('administrator', 'settings'),
  ('content_manager', 'dashboard'),
  ('content_manager', 'content'),
  ('content_manager', 'storyline'),
  ('content_manager', 'media'),
  ('operations_manager', 'dashboard'),
  ('operations_manager', 'orders'),
  ('operations_manager', 'channels'),
  ('operations_manager', 'finance'),
  ('support_manager', 'dashboard'),
  ('support_manager', 'users'),
  ('support_manager', 'orders');
