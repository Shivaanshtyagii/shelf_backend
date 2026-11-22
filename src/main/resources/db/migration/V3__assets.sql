create schema assets;
create table assets.quota (
    id boolean primary key default true check (id)
);
insert into assets.quota default values;
create table assets.file (
    id uuid primary key,
    owner_id uuid not null,
    storage_key text not null unique,
    media_type varchar(40) not null,
    byte_size bigint not null check (byte_size > 0),
    kind varchar(10) not null check (kind in ('PDF', 'PHOTO')),
    state varchar(16) not null check (state in ('UPLOADING', 'CLEAN', 'PURGING')),
    claimed boolean not null default false,
    created_at timestamptz not null default now()
);
create index file_owner on assets.file(owner_id);
create index unclaimed_file on assets.file(created_at) where not claimed;
