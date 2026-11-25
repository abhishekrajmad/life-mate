ALTER TABLE categories ADD COLUMN user_id VARCHAR(50);

UPDATE categories SET user_id = 'usr_abhishek' WHERE user_id IS NULL;

ALTER TABLE categories ALTER COLUMN user_id SET NOT NULL;
ALTER TABLE categories DROP CONSTRAINT IF EXISTS categories_name_key;
DROP INDEX IF EXISTS uq_categories_name_lower;

CREATE UNIQUE INDEX uq_categories_user_name_lower
    ON categories (user_id, LOWER(name));
CREATE INDEX idx_accounts_user_id ON accounts (user_id);
CREATE INDEX idx_categories_user_id ON categories (user_id);
