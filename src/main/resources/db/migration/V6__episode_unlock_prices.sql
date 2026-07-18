ALTER TABLE episodes
  ADD COLUMN unlock_price DECIMAL(10, 2) NULL AFTER pay_node;

UPDATE episodes
SET unlock_price = CASE
  WHEN drama_id = 1 THEN
    CASE
      WHEN episode_no <= 12 THEN 6.00
      WHEN episode_no <= 14 THEN 7.00
      ELSE 8.00
    END
  WHEN drama_id = 2 THEN
    CASE
      WHEN episode_no <= 12 THEN 8.00
      WHEN episode_no <= 14 THEN 9.00
      ELSE 10.00
    END
  WHEN drama_id = 3 THEN
    CASE
      WHEN episode_no <= 12 THEN 10.00
      WHEN episode_no <= 14 THEN 12.00
      ELSE 15.00
    END
  WHEN drama_id = 4 THEN
    CASE
      WHEN episode_no <= 12 THEN 7.00
      WHEN episode_no <= 14 THEN 8.00
      ELSE 9.00
    END
  ELSE 6.00
END
WHERE storyline_id IS NOT NULL OR pay_node = TRUE;
