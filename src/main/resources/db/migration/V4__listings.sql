create schema listings;
create extension if not exists pg_trgm;
create table listings.seller (
    member_id uuid primary key,
    status varchar(16) not null check (status in ('PENDING', 'APPROVED', 'REJECTED')),
    updated_at timestamptz not null default now()
);
create table listings.listing (
    id uuid primary key,
    owner_id uuid not null,
    scope varchar(24) not null check (scope in ('EXPLORE', 'CAMPUS_MATERIALS', 'CAMPUS_MARKETPLACE')),
    institution_id uuid,
    title varchar(120) not null,
    description varchar(2000) not null,
    subject varchar(80) not null,
    price_minor integer not null default 0 check (price_minor >= 0),
    asset_id uuid,
    status varchar(16) not null default 'DRAFT' check (status in ('DRAFT', 'PENDING', 'APPROVED', 'REJECTED', 'ARCHIVED')),
    revision integer not null default 1,
    created_at timestamptz not null default now(),
    published_at timestamptz,
    search_document tsvector generated always as (
        setweight(to_tsvector('simple', title), 'A') ||
        setweight(to_tsvector('simple', subject), 'B') ||
        setweight(to_tsvector('simple', description), 'C')
    ) stored,
    check ((scope = 'EXPLORE') = (institution_id is null)),
    check (scope = 'CAMPUS_MARKETPLACE' or price_minor = 0)
);
create index listing_owner on listings.listing(owner_id, created_at desc);
create index listing_discovery on listings.listing(scope, institution_id, published_at desc, id) where status = 'APPROVED';
create index listing_search on listings.listing using gin(search_document) where status = 'APPROVED';
create index listing_typo on listings.listing using gin(title gin_trgm_ops) where status = 'APPROVED';
