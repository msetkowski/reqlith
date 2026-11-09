-- Global non-functional requirements that every project inherits, read-only. They are not part of project
-- snapshots, so adding, changing or removing one never changes a project's state or its approved versions.
create table baseline_requirement (
    id           uniqueidentifier not null primary key,
    baseline_key nvarchar(16)     not null,
    category     nvarchar(32)     not null,
    title        nvarchar(200)    not null,
    body_md      nvarchar(max)    null,
    created_at   datetimeoffset   not null,
    updated_at   datetimeoffset   not null,
    updated_by   uniqueidentifier not null references app_user (id),
    constraint uq_baseline_key unique (baseline_key)
);
