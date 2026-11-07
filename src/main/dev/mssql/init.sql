-- Local development only. Creates the database and the two application logins:
--   reqlith_app  owns the schema (Flyway migrations, read/write)
--   reqlith_mcp  read-only, used by the MCP server
if db_id('reqlith') is null
    create database reqlith;
go

use reqlith;
go

if suser_id('reqlith_app') is null
    create login reqlith_app with password = '$(APP_PASSWORD)', check_policy = off;
if user_id('reqlith_app') is null
begin
    create user reqlith_app for login reqlith_app;
    alter role db_owner add member reqlith_app;
end
go

if suser_id('reqlith_mcp') is null
    create login reqlith_mcp with password = '$(MCP_PASSWORD)', check_policy = off;
if user_id('reqlith_mcp') is null
begin
    create user reqlith_mcp for login reqlith_mcp;
    alter role db_datareader add member reqlith_mcp;
end
go
