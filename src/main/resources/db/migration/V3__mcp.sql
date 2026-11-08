-- MCP access log entries are shown per project.
alter table mcp_access_log add project_key nvarchar(32) null;
create index ix_mcp_access_log_project on mcp_access_log (project_key, occurred_at desc);
