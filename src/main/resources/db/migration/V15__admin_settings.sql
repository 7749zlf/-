CREATE TABLE admin_settings (
  id VARCHAR(64) PRIMARY KEY,
  payload JSON NOT NULL,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

INSERT INTO admin_settings (id, payload)
VALUES (
  'default',
  JSON_OBJECT(
    'autoReview', TRUE,
    'watermark', TRUE,
    'smsNotify', TRUE,
    'riskControl', TRUE,
    'payment', 'PayPal 演示通道',
    'storage', '本地视频库',
    'callback', 'https://example.com/payment/callback',
    'minBalance', 30,
    'refundWindow', 24,
    'maxDrawPerDay', 20,
    'defaultUnlockPrice', '¥18',
    'defaultDrawPrice', '¥6'
  )
);
