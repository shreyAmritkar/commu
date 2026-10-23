CREATE TABLE notifications (
    id          BIGSERIAL PRIMARY KEY,
    user_id     UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type        VARCHAR(30)  NOT NULL,
    session_id  UUID         REFERENCES sessions (id) ON DELETE CASCADE,
    title       VARCHAR(120) NOT NULL,
    body        VARCHAR(255),
    dedupe_key  VARCHAR(120) NOT NULL,
    read_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (user_id, dedupe_key)
);
CREATE INDEX idx_notifications_user_created ON notifications (user_id, created_at DESC);
CREATE INDEX idx_notifications_unread ON notifications (user_id) WHERE read_at IS NULL;

-- the cleanup job deletes old rows; the limited app role otherwise has no DELETE
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'engish_app') THEN
        GRANT DELETE ON notifications TO engish_app;
    END IF;
END $$;