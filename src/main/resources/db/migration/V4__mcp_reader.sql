-- The MCP server reads through its own login with SELECT rights only (created per environment,
-- see src/main/dev/mssql/init.sql). Map it into this database when the login exists.
if suser_id('reqlith_mcp') is not null and user_id('reqlith_mcp') is null
begin
    create user reqlith_mcp for login reqlith_mcp;
end;
if user_id('reqlith_mcp') is not null
begin
    alter role db_datareader add member reqlith_mcp;
end;
