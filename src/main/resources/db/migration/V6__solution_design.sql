-- Solution design: one markdown section per project, versioned with the rest of the document.
alter table project add solution_design_md nvarchar(max) null;
