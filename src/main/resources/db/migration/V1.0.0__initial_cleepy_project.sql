CREATE TABLE clips (
    id UUID primary key,
    project_id UUID not null,
    title varchar not null,
    description varchar,
    video_url varchar,
    thumbnail_url varchar,
    start_time TIME,
    end_time TIME,
    viral_score integer,
    analysis_result text,
    created_at timestamp not null,
    updated_at timestamp not null
);

CREATE TABLE projects (
    id UUID primary key,
    title varchar not null,
    status varchar not null,
    thumbnail_url varchar,
    user_id UUID not null,
    created_at timestamp not null,
    updated_at timestamp not null
);
