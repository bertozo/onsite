-- Port of the Android app's Room entities, scoped per account, with the columns every
-- table needs for the future offline sync engine (Phase 3): updated_at drives the
-- last-write-wins cursor, deleted_at is a soft delete so removals replicate too.
create table companies (
    id                uuid primary key,
    account_id        uuid not null references accounts (id) on delete cascade,
    name              text not null,
    abn               text,
    phone             text,
    email             text,
    created_at_millis bigint not null,
    hourly_rate       double precision,
    updated_at        timestamptz not null default now(),
    deleted_at        timestamptz
);
create index idx_companies_account on companies (account_id) where deleted_at is null;

create table sites (
    id                uuid primary key,
    account_id        uuid not null references accounts (id) on delete cascade,
    label             text not null,
    address           text,
    latitude          double precision not null,
    longitude         double precision not null,
    created_at_millis bigint not null,
    updated_at        timestamptz not null default now(),
    deleted_at        timestamptz
);
create index idx_sites_account on sites (account_id) where deleted_at is null;

create table job_types (
    id                uuid primary key,
    account_id        uuid not null references accounts (id) on delete cascade,
    name              text not null,
    created_at_millis bigint not null,
    updated_at        timestamptz not null default now(),
    deleted_at        timestamptz
);
create index idx_job_types_account on job_types (account_id) where deleted_at is null;

create table invoices (
    id                       uuid primary key,
    account_id               uuid not null references accounts (id) on delete cascade,
    number                   text not null,
    company_name             text not null,
    period_start_epoch_day   bigint not null,
    period_end_epoch_day     bigint not null,
    issue_date_epoch_day     bigint not null,
    total_hours              double precision not null,
    total_amount             double precision,
    hourly_rate              double precision,
    status                   text not null default 'DRAFT',
    sent_at_millis           bigint,
    paid_at_millis           bigint,
    pdf_path                 text,
    notes                    text,
    created_at_millis        bigint not null,
    updated_at               timestamptz not null default now(),
    deleted_at               timestamptz
);
create index idx_invoices_account on invoices (account_id) where deleted_at is null;

create table tracking_sessions (
    id                      uuid primary key,
    account_id              uuid not null references accounts (id) on delete cascade,
    company_name            text,
    site_label              text,
    job_type_label          text,
    start_timestamp_millis  bigint not null,
    start_latitude          double precision not null,
    start_longitude         double precision not null,
    stop_timestamp_millis   bigint,
    stop_latitude           double precision,
    stop_longitude          double precision,
    hourly_rate             double precision,
    invoice_id              uuid references invoices (id) on delete set null,
    updated_at              timestamptz not null default now(),
    deleted_at              timestamptz
);
create index idx_tracking_sessions_account on tracking_sessions (account_id) where deleted_at is null;

create table planned_jobs (
    id                uuid primary key,
    account_id        uuid not null references accounts (id) on delete cascade,
    date_epoch_day    bigint not null,
    start_minute      integer not null,
    end_minute        integer,
    company_name      text,
    site_label        text,
    job_type_label    text,
    notes             text,
    updated_at        timestamptz not null default now(),
    deleted_at        timestamptz
);
create index idx_planned_jobs_account on planned_jobs (account_id) where deleted_at is null;
