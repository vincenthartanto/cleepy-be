CREATE TABLE social_connections (
    id UUID PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    platform VARCHAR(32) NOT NULL,
    provider_account_id VARCHAR(255) NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL,
    scopes TEXT,
    access_token_encrypted TEXT NOT NULL,
    refresh_token_encrypted TEXT,
    token_expires_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uk_social_connections_user_platform
    ON social_connections(user_id, platform);

CREATE TABLE publish_batches (
    id UUID PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    project_id UUID NOT NULL,
    status VARCHAR(32) NOT NULL,
    total_jobs INT NOT NULL DEFAULT 0,
    completed_jobs INT NOT NULL DEFAULT 0,
    failed_jobs INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_publish_batches_user_project
    ON publish_batches(user_id, project_id);

CREATE TABLE publish_jobs (
    id UUID PRIMARY KEY,
    batch_id UUID NOT NULL,
    user_id VARCHAR(255) NOT NULL,
    project_id UUID NOT NULL,
    clip_id UUID NOT NULL,
    connection_id UUID NOT NULL,
    platform VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    requested_title VARCHAR(255),
    requested_description TEXT,
    requested_privacy_level VARCHAR(64),
    provider_publish_id VARCHAR(255),
    provider_video_id VARCHAR(255),
    provider_url VARCHAR(500),
    provider_status VARCHAR(128),
    error_message TEXT,
    retry_count INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP,
    published_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_publish_jobs_batch_id
    ON publish_jobs(batch_id);

CREATE INDEX idx_publish_jobs_project_id
    ON publish_jobs(project_id);

CREATE INDEX idx_publish_jobs_status_next_attempt
    ON publish_jobs(status, next_attempt_at, created_at);
