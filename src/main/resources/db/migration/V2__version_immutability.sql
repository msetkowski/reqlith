-- Approved versions are immutable and snapshots can never change, whatever the application does.
create trigger trg_project_version_immutable on project_version
after update, delete
as
begin
    set nocount on;

    if exists (select 1 from deleted d where d.status = 'APPROVED')
        throw 51000, 'Approved project version is immutable', 1;

    if exists (select 1
               from deleted d
                        join inserted i on i.id = d.id
               where i.snapshot <> d.snapshot
                  or i.content_hash <> d.content_hash
                  or i.project_id <> d.project_id)
        throw 51001, 'Project version snapshot can never be modified', 1;
end
