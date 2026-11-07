-- Reqlith core schema (SQL Server).
-- Two layers that never mix: the mutable working copy (project, element, project_role, ...)
-- and immutable version snapshots (project_version).

-- ---------------------------------------------------------------- users & workspaces

create table app_user (
    id           uniqueidentifier not null primary key,
    subject      nvarchar(255)    not null,
    email        nvarchar(320)    null,
    display_name nvarchar(200)    not null,
    initials     nvarchar(4)      not null,
    created_at   datetimeoffset   not null,
    last_login_at datetimeoffset  null,
    constraint uq_app_user_subject unique (subject)
);
create index ix_app_user_email on app_user (email);

create table workspace (
    id         uniqueidentifier not null primary key,
    ws_key     nvarchar(16)     not null,
    name       nvarchar(200)    not null,
    created_at datetimeoffset   not null,
    constraint uq_workspace_key unique (ws_key),
    constraint ck_workspace_key check (ws_key not like '%[^A-Z0-9]%')
);

-- Monotonic counters for human readable keys. Values are never reused.
-- scope = 'PROJECT_KEY:<workspace id>' or 'ELEMENT_KEY:<project id>', name = 'PRJ' / 'BR' / 'FR' / ...
create table key_counter (
    scope      nvarchar(64) not null,
    name       nvarchar(16) not null,
    last_value int          not null,
    constraint pk_key_counter primary key (scope, name)
);

-- ---------------------------------------------------------------- working copy

create table project (
    id                      uniqueidentifier not null primary key,
    project_key             nvarchar(32)     not null,
    workspace_id            uniqueidentifier not null references workspace (id),
    title                   nvarchar(200)    not null,
    summary                 nvarchar(1000)   null,
    basic_info_md           nvarchar(max)    null,
    status                  nvarchar(16)     not null default 'DRAFT',
    latest_approved_version int              null,
    current_candidate_id    uniqueidentifier null,
    row_version             int              not null default 0,
    created_at              datetimeoffset   not null,
    created_by              uniqueidentifier not null references app_user (id),
    updated_at              datetimeoffset   not null,
    updated_by              uniqueidentifier not null references app_user (id),
    constraint uq_project_key unique (project_key),
    constraint ck_project_status check (status in ('DRAFT', 'IN_REVIEW'))
);
create index ix_project_workspace on project (workspace_id);

create table project_tag (
    project_id uniqueidentifier not null references project (id) on delete cascade,
    tag        nvarchar(64)     not null,
    constraint pk_project_tag primary key (project_id, tag)
);

create table project_domain (
    project_id uniqueidentifier not null references project (id) on delete cascade,
    domain     nvarchar(64)     not null,
    constraint pk_project_domain primary key (project_id, domain)
);

create table project_member (
    project_id uniqueidentifier not null references project (id) on delete cascade,
    user_id    uniqueidentifier not null references app_user (id),
    member_role nvarchar(16)    not null,
    added_at   datetimeoffset   not null,
    constraint pk_project_member primary key (project_id, user_id),
    constraint ck_project_member_role check (member_role in ('OWNER', 'CONTRIBUTOR', 'REVIEWER'))
);
create index ix_project_member_user on project_member (user_id);

-- Invitation by e-mail; bound to an app_user on their first login.
create table invitation (
    id          uniqueidentifier not null primary key,
    project_id  uniqueidentifier not null references project (id) on delete cascade,
    email       nvarchar(320)    not null,
    member_role nvarchar(16)     not null,
    invited_by  uniqueidentifier not null references app_user (id),
    invited_at  datetimeoffset   not null,
    constraint uq_invitation unique (project_id, email),
    constraint ck_invitation_role check (member_role in ('OWNER', 'CONTRIBUTOR', 'REVIEWER'))
);
create index ix_invitation_email on invitation (email);

create table element (
    id           uniqueidentifier not null primary key,
    project_id   uniqueidentifier not null references project (id) on delete cascade,
    element_type nvarchar(8)      not null,
    element_key  nvarchar(16)     not null,
    title        nvarchar(300)    not null,
    body_md      nvarchar(max)    null,
    priority     nvarchar(8)      null,
    sort_order   int              not null default 0,
    row_version  int              not null default 0,
    created_at   datetimeoffset   not null,
    updated_at   datetimeoffset   not null,
    updated_by   uniqueidentifier not null references app_user (id),
    constraint uq_element_key unique (project_id, element_key),
    constraint ck_element_type check (element_type in ('BR', 'SR', 'FR', 'NFR', 'SC', 'AS', 'RK', 'DP')),
    constraint ck_element_priority check (priority is null or priority in ('MUST', 'SHOULD', 'COULD', 'WONT'))
);

create table element_tag (
    element_id uniqueidentifier not null references element (id) on delete cascade,
    tag        nvarchar(64)     not null,
    constraint pk_element_tag primary key (element_id, tag)
);

-- Links reference the target by key so they survive unchanged in snapshots.
create table trace_link (
    from_element_id uniqueidentifier not null references element (id) on delete cascade,
    to_key          nvarchar(16)     not null,
    link_kind       nvarchar(16)     not null,
    constraint pk_trace_link primary key (from_element_id, to_key, link_kind),
    constraint ck_trace_link_kind check (link_kind in ('DERIVED_FROM', 'VERIFIED_BY', 'DEPENDS_ON', 'MITIGATES'))
);

-- Roles are global (visible across projects); project_role describes them in a project's context.
create table role (
    id             uniqueidentifier not null primary key,
    role_key       nvarchar(16)     not null,
    name           nvarchar(200)    not null,
    description_md nvarchar(max)    null,
    updated_at     datetimeoffset   not null,
    updated_by     uniqueidentifier not null references app_user (id),
    constraint uq_role_key unique (role_key),
    constraint uq_role_name unique (name)
);

create table project_role (
    project_id uniqueidentifier not null references project (id) on delete cascade,
    role_id    uniqueidentifier not null references role (id),
    context_md nvarchar(max)    null,
    sort_order int              not null default 0,
    constraint pk_project_role primary key (project_id, role_id)
);

-- ---------------------------------------------------------------- history (immutable once approved)

create table project_version (
    id               uniqueidentifier not null primary key,
    project_id       uniqueidentifier not null references project (id),
    version_no       int              null,
    candidate_no     int              not null,
    target_version   int              not null,
    status           nvarchar(24)     not null,
    snapshot         nvarchar(max)    not null,
    schema_version   int              not null,
    content_hash     char(64)         not null,
    based_on_version int              null,
    submitted_by     uniqueidentifier not null references app_user (id),
    submitted_at     datetimeoffset   not null,
    closed_at        datetimeoffset   null,
    constraint ck_project_version_status
        check (status in ('IN_REVIEW', 'APPROVED', 'CHANGES_REQUESTED', 'REJECTED', 'WITHDRAWN')),
    constraint ck_project_version_snapshot check (isjson(snapshot) = 1),
    constraint ck_project_version_no check ((status = 'APPROVED' and version_no is not null)
                                            or (status <> 'APPROVED' and version_no is null)),
    constraint uq_project_version_candidate unique (project_id, target_version, candidate_no)
);
create unique index uq_project_version_no on project_version (project_id, version_no) where version_no is not null;

alter table project add constraint fk_project_current_candidate
    foreign key (current_candidate_id) references project_version (id);

create table review_assignment (
    version_id  uniqueidentifier not null references project_version (id),
    reviewer_id uniqueidentifier not null references app_user (id),
    decision    nvarchar(16)     not null default 'PENDING',
    comment_md  nvarchar(max)    null,
    decided_at  datetimeoffset   null,
    constraint pk_review_assignment primary key (version_id, reviewer_id),
    constraint ck_review_decision check (decision in ('PENDING', 'ACCEPT', 'CHANGES', 'REJECT'))
);
create index ix_review_assignment_reviewer on review_assignment (reviewer_id, decision);

create table suggestion (
    id          uniqueidentifier not null primary key,
    version_id  uniqueidentifier not null references project_version (id),
    element_key nvarchar(16)     not null, -- element key or 'BASIC_INFO'
    proposed_md nvarchar(max)    null,
    comment_md  nvarchar(max)    null,
    author_id   uniqueidentifier not null references app_user (id),
    status      nvarchar(16)     not null default 'OPEN',
    created_at  datetimeoffset   not null,
    resolved_at datetimeoffset   null,
    resolved_by uniqueidentifier null references app_user (id),
    constraint ck_suggestion_status check (status in ('OPEN', 'APPLIED', 'DISMISSED'))
);
create index ix_suggestion_version on suggestion (version_id);

create table review_comment (
    id          uniqueidentifier not null primary key,
    version_id  uniqueidentifier not null references project_version (id),
    element_key nvarchar(16)     null,
    author_id   uniqueidentifier not null references app_user (id),
    body_md     nvarchar(max)    not null,
    created_at  datetimeoffset   not null
);
create index ix_review_comment_version on review_comment (version_id);

-- ---------------------------------------------------------------- outside the snapshot

-- Links can be added after approval, so they are not part of any snapshot.
create table external_link (
    id          uniqueidentifier not null primary key,
    project_id  uniqueidentifier not null references project (id) on delete cascade,
    version_no  int              null,
    element_key nvarchar(16)     null,
    link_system nvarchar(8)      not null,
    url         nvarchar(2000)   not null,
    label       nvarchar(300)    not null,
    created_at  datetimeoffset   not null,
    created_by  uniqueidentifier not null references app_user (id),
    constraint ck_external_link_system check (link_system in ('JIRA', 'ADO', 'URL'))
);
create index ix_external_link_project on external_link (project_id);

create table export_cache (
    content_hash char(64)        not null,
    format       nvarchar(8)     not null,
    options_key  nvarchar(64)    not null,
    bytes        varbinary(max)  not null,
    created_at   datetimeoffset  not null,
    constraint pk_export_cache primary key (content_hash, format, options_key)
);

-- ---------------------------------------------------------------- MCP

create table access_token (
    id           uniqueidentifier not null primary key,
    user_id      uniqueidentifier not null references app_user (id),
    name         nvarchar(100)    not null,
    token_hash   char(64)         not null,
    token_prefix nvarchar(16)     not null,
    created_at   datetimeoffset   not null,
    last_used_at datetimeoffset   null,
    revoked_at   datetimeoffset   null,
    constraint uq_access_token_hash unique (token_hash)
);

create table mcp_access_log (
    id          bigint identity (1, 1) primary key,
    occurred_at datetimeoffset   not null,
    client_name nvarchar(200)    null,
    user_id     uniqueidentifier not null references app_user (id),
    token_id    uniqueidentifier null references access_token (id),
    tool        nvarchar(64)     not null,
    args_json   nvarchar(max)    null
);
create index ix_mcp_access_log_time on mcp_access_log (occurred_at desc);

-- ---------------------------------------------------------------- seed

insert into workspace (id, ws_key, name, created_at)
values (newid(), 'GEN', 'General', sysdatetimeoffset());
