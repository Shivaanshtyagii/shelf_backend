create schema accounts;
create table accounts.member (
    id uuid primary key,
    email varchar(254) not null unique,
    display_name varchar(80) not null,
    password_hash varchar(100) not null,
    verified boolean not null default false,
    role varchar(16) not null default 'MEMBER' check (role in ('MEMBER', 'MODERATOR')),
    credential_version integer not null default 0,
    created_at timestamptz not null default now()
);
create table accounts.challenge (
    token_hash char(64) primary key,
    member_id uuid not null references accounts.member(id) on delete cascade,
    purpose varchar(16) not null check (purpose in ('VERIFY', 'RESET')),
    expires_at timestamptz not null,
    unique (member_id, purpose)
);
create table spring_session (
    primary_id char(36) primary key,
    session_id char(36) not null,
    creation_time bigint not null,
    last_access_time bigint not null,
    max_inactive_interval integer not null,
    expiry_time bigint not null,
    principal_name varchar(100)
);
create unique index spring_session_ix1 on spring_session(session_id);
create index spring_session_ix2 on spring_session(expiry_time);
create index spring_session_ix3 on spring_session(principal_name);
create table spring_session_attributes (
    session_primary_id char(36) not null references spring_session(primary_id) on delete cascade,
    attribute_name varchar(200) not null,
    attribute_bytes bytea not null,
    primary key (session_primary_id, attribute_name)
);
