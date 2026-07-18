UPDATE app_users
SET avatar = '/images/default-avatar.svg'
WHERE avatar IS NULL OR avatar = '';

ALTER TABLE app_users
  MODIFY COLUMN avatar VARCHAR(512) NOT NULL DEFAULT '/images/default-avatar.svg';
