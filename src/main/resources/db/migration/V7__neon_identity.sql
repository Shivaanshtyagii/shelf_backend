alter table accounts.member add column neon_subject text unique;
alter table accounts.member alter column password_hash drop not null;
alter table accounts.member add constraint member_has_identity
    check (password_hash is not null or neon_subject is not null);
