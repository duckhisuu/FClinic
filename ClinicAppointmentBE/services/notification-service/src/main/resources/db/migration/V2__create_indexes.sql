CREATE INDEX idx_notification_delivery_due ON notifications(status, next_attempt_at);
CREATE INDEX idx_notification_recipient_user ON notifications(recipient_user_id, created_at DESC);
CREATE INDEX idx_notification_appointment ON notifications(appointment_id);
CREATE INDEX idx_notification_event ON notifications(event_id);
