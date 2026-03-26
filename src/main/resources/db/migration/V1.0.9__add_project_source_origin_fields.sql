ALTER TABLE projects
    ADD COLUMN source_kind varchar,
    ADD COLUMN source_origin_url text,
    ADD COLUMN source_provider varchar;
