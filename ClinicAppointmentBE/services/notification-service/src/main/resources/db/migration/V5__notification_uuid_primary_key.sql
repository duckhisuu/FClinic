-- Cấp UUIDv4 mới cho notification; không chuyển đổi số thành UUID giả.
CREATE TABLE notification_id_migration_map AS
SELECT
    id AS legacy_id,
    gen_random_uuid() AS notification_id
FROM notifications;

ALTER TABLE notifications
    ADD COLUMN uuid_id UUID;

UPDATE notifications n
SET uuid_id = m.notification_id
FROM notification_id_migration_map m
WHERE n.id = m.legacy_id;

ALTER TABLE notifications
    DROP CONSTRAINT notifications_pkey;

ALTER TABLE notifications
    DROP COLUMN id;

ALTER TABLE notifications
    RENAME COLUMN uuid_id TO id;

ALTER TABLE notifications
    ADD PRIMARY KEY (id);

ALTER TABLE notification_templates
    ALTER COLUMN id DROP DEFAULT;

ALTER TABLE notification_templates
    ALTER COLUMN id TYPE UUID USING gen_random_uuid();
