CREATE TABLE evaluation_commit_receipt (
    doc_name VARCHAR(64) NOT NULL,
    operation_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    actor_id INT NOT NULL,
    expected_revision BIGINT NOT NULL,
    committed_revision BIGINT NOT NULL,
    request_hash BINARY(32) NOT NULL,
    state_hash BINARY(32) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (doc_name, operation_id),
    CONSTRAINT fk_evaluation_receipt_doc FOREIGN KEY (doc_name) REFERENCES collab_doc(doc_name) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
