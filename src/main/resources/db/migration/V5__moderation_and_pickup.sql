create schema moderation;
create table moderation.decision (
    id bigint generated always as identity primary key,
    target_type varchar(16) not null check (target_type in ('LISTING', 'SELLER')),
    target_id uuid not null,
    revision integer,
    moderator_id uuid not null,
    decision varchar(16) not null check (decision in ('APPROVED', 'REJECTED', 'ARCHIVED')),
    reason varchar(800) not null,
    created_at timestamptz not null default now()
);
create index decision_target on moderation.decision(target_type, target_id, id desc);
create table moderation.report (
    id uuid primary key,
    listing_id uuid not null,
    reporter_id uuid not null,
    reason varchar(800) not null,
    status varchar(16) not null default 'PENDING' check (status in ('PENDING', 'RESOLVED', 'DISMISSED')),
    response varchar(800),
    reviewed_by uuid,
    created_at timestamptz not null default now()
);
create unique index open_report on moderation.report(listing_id, reporter_id) where status = 'PENDING';
create table moderation.appeal (
    id uuid primary key,
    decision_id bigint not null references moderation.decision(id),
    member_id uuid not null,
    reason varchar(800) not null,
    status varchar(16) not null default 'PENDING' check (status in ('PENDING', 'UPHELD', 'DISMISSED')),
    response varchar(800),
    reviewed_by uuid,
    created_at timestamptz not null default now(),
    unique (decision_id, member_id)
);
create schema pickup;
create table pickup.request (
    id uuid primary key,
    listing_id uuid not null,
    listing_title varchar(120) not null,
    institution_id uuid not null,
    buyer_id uuid not null,
    seller_id uuid not null,
    status varchar(16) not null check (status in ('REQUESTED', 'ACCEPTED', 'DECLINED', 'CANCELLED', 'COLLECTED')),
    meeting_location varchar(160),
    meeting_time timestamptz,
    created_at timestamptz not null default now(),
    check (buyer_id <> seller_id)
);
create unique index one_pickup_per_buyer on pickup.request(listing_id, buyer_id)
where status in ('REQUESTED', 'ACCEPTED', 'COLLECTED');
create unique index one_reserved_item on pickup.request(listing_id)
where status in ('ACCEPTED', 'COLLECTED');
