ALTER TABLE projects
    ADD COLUMN thumbnail_storage_uri varchar,
    ADD COLUMN thumbnail_bucket varchar,
    ADD COLUMN thumbnail_object_path varchar;

ALTER TABLE clips
    ADD COLUMN storage_provider varchar,
    ADD COLUMN video_storage_uri varchar,
    ADD COLUMN video_bucket varchar,
    ADD COLUMN video_object_path varchar,
    ADD COLUMN thumbnail_storage_uri varchar,
    ADD COLUMN thumbnail_bucket varchar,
    ADD COLUMN thumbnail_object_path varchar;
