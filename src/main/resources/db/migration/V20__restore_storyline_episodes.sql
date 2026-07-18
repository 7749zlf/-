INSERT INTO episodes (
  id,
  drama_id,
  episode_no,
  title,
  storyline_id,
  cover_url,
  video_url,
  status,
  pay_node,
  unlock_price
)
SELECT CONCAT(s.id, '-', n.episode_no),
       s.drama_id,
       n.episode_no,
       CONCAT(s.name, ' ', n.episode_no),
       s.id,
       s.cover_url,
       CASE MOD(s.sort_order + n.episode_no, 6)
         WHEN 0 THEN 'https://samplelib.com/mp4/sample-5s-360p.mp4'
         WHEN 1 THEN 'https://samplelib.com/mp4/sample-10s-360p.mp4'
         WHEN 2 THEN 'https://samplelib.com/mp4/sample-15s-360p.mp4'
         WHEN 3 THEN 'https://samplelib.com/mp4/sample-20s-360p.mp4'
         WHEN 4 THEN 'https://samplelib.com/mp4/sample-30s-360p.mp4'
         ELSE 'https://test-videos.co.uk/vids/bigbuckbunny/mp4/h264/360/Big_Buck_Bunny_360_10s_1MB.mp4'
       END,
       'PUBLISHED',
       FALSE,
       COALESCE(p.draw_price, 6.00)
FROM storylines s
LEFT JOIN story_pools p ON p.id = s.pool_id
JOIN (
  SELECT 11 AS episode_no UNION ALL
  SELECT 12 UNION ALL
  SELECT 13 UNION ALL
  SELECT 14 UNION ALL
  SELECT 15 UNION ALL
  SELECT 16
) n
WHERE s.status = 'ENABLED'
ON DUPLICATE KEY UPDATE
  drama_id = VALUES(drama_id),
  episode_no = VALUES(episode_no),
  title = VALUES(title),
  storyline_id = VALUES(storyline_id),
  cover_url = VALUES(cover_url),
  video_url = VALUES(video_url),
  status = 'PUBLISHED',
  pay_node = FALSE,
  unlock_price = VALUES(unlock_price);
