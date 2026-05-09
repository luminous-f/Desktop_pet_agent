ALTER TABLE long_term_memories
    ADD COLUMN access_count INT NOT NULL DEFAULT 0,
    ADD COLUMN last_accessed_at TIMESTAMP NULL;

CREATE TABLE IF NOT EXISTS session_summaries (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id VARCHAR(64) NOT NULL,
    summary_text TEXT NOT NULL,
    message_count INT NOT NULL DEFAULT 0,
    importance INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_session_summaries_created (created_at)
);
