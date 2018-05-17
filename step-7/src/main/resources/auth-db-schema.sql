create table if not exists user (username varchar(255), password varchar(255), password_salt varchar(255));
create table if not exists user_roles (username varchar(255), role varchar(255));
create table if not exists roles_perms (role varchar(255), perm varchar(255));

insert into roles_perms values ('writer', 'update');
insert into roles_perms values ('editor', 'create');
insert into roles_perms values ('editor', 'delete');
insert into roles_perms values ('editor', 'update');
insert into roles_perms values ('writer', 'update');
insert into roles_perms values ('admin', 'create');
insert into roles_perms values ('admin', 'delete');
insert into roles_perms values ('admin', 'update');
