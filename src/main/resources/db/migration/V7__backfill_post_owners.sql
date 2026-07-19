-- Only unambiguous legacy rows are backfilled. A duplicated or missing email
-- cannot prove ownership and therefore deliberately remains non-editable.
UPDATE posts p
JOIN (
    SELECT email, MIN(id) AS user_id
    FROM users
    GROUP BY email
    HAVING COUNT(*) = 1
) u ON u.email = p.author_email
SET p.author_user_id = u.user_id
WHERE p.author_user_id IS NULL;
