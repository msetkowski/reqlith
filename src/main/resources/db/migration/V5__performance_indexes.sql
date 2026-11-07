-- Indexes for lookups that scanned their table (found in a query review).

-- Which projects use a role (role page, permission to edit a shared role).
create index ix_project_role_role on project_role (role_id);

-- A user's access tokens, newest first.
create index ix_access_token_user on access_token (user_id, created_at desc);

-- A user's recent agent access (token page).
create index ix_mcp_access_log_user on mcp_access_log (user_id, occurred_at desc);

-- Approved versions of all projects (home page states); includes what the list needs.
create index ix_project_version_status on project_version (status) include (project_id, version_no, closed_at);
