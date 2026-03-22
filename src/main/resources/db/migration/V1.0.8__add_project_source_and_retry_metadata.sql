ALTER TABLE projects
    ADD COLUMN source_storage_uri varchar,
    ADD COLUMN source_bucket varchar,
    ADD COLUMN source_object_path varchar,
    ADD COLUMN source_file_name varchar,
    ADD COLUMN source_content_type varchar,
    ADD COLUMN source_size_bytes bigint,
    ADD COLUMN worker_retry_count integer DEFAULT 0,
    ADD COLUMN last_failed_stage varchar,
    ADD COLUMN last_failure_reason text;
