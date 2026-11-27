INSERT INTO activity_type(activity_category_id,name)
SELECT c.activity_category_id,'스노클링' FROM activity_category c
WHERE c.name='스노클링' AND NOT EXISTS (
  SELECT 1 FROM activity_type a WHERE a.activity_category_id=c.activity_category_id AND a.name='스노클링');
