UPDATE users
SET created_date = COALESCE(created_date, modified_date, CURRENT_TIMESTAMP(6)),
    modified_date = COALESCE(modified_date, created_date, CURRENT_TIMESTAMP(6));

UPDATE posts
SET created_date = COALESCE(created_date, modified_date, CURRENT_TIMESTAMP(6)),
    modified_date = COALESCE(modified_date, created_date, CURRENT_TIMESTAMP(6));
