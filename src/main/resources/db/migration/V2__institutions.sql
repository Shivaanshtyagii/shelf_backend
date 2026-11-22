create schema institutions;
create table institutions.institution (
    id uuid primary key,
    name varchar(140) not null unique,
    email_domain varchar(253) unique
);
create table institutions.course (
    id uuid primary key,
    institution_id uuid not null references institutions.institution(id),
    code varchar(30) not null,
    name varchar(140) not null,
    unique (institution_id, code)
);
create table institutions.affiliation (
    member_id uuid primary key,
    institution_id uuid not null references institutions.institution(id),
    verified_at timestamptz not null default now()
);
create table institutions.request (
    id uuid primary key,
    member_id uuid not null,
    kind varchar(16) not null check (kind in ('INSTITUTION', 'COURSE', 'AFFILIATION')),
    institution_id uuid references institutions.institution(id),
    name varchar(140),
    detail varchar(600),
    status varchar(16) not null default 'PENDING' check (status in ('PENDING', 'APPROVED', 'REJECTED')),
    reviewed_by uuid,
    created_at timestamptz not null default now()
);
create index request_status_date on institutions.request(status, created_at);
