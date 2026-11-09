-- Giữ nguyên dữ liệu cũ để đối soát; không suy đoán ánh xạ BIGINT sang tài khoản UUID.
CREATE TABLE notifications_legacy_id_archive AS SELECT * FROM notifications;
DELETE FROM notifications;

ALTER TABLE notifications ALTER COLUMN appointment_id TYPE UUID USING NULL::UUID;
ALTER TABLE notifications ALTER COLUMN recipient_user_id DROP NOT NULL;
ALTER TABLE notifications ALTER COLUMN recipient_user_id TYPE UUID USING NULL::UUID;

-- Một email có thể được dùng bởi nhiều tài khoản. Không gộp hai người nhận theo email.
ALTER TABLE notifications DROP CONSTRAINT uk_notification_event_recipient_type_channel;
ALTER TABLE notifications ADD CONSTRAINT uk_notification_event_recipient_type_channel
    UNIQUE (event_id, recipient_type, notification_type, channel);
