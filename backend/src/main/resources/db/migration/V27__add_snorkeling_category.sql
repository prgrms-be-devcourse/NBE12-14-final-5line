INSERT INTO activity_category(name)
SELECT '스노클링' WHERE NOT EXISTS (SELECT 1 FROM activity_category WHERE name = '스노클링');
