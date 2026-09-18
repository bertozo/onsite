-- Bridges the "worker manages everything alone" and "worker joined the employer's team
-- account" models: a worker keeps their own account and invoicing, but can link one of
-- their own Company entries to a client's real account (if that client also uses the
-- app), so the client can drop jobs onto the worker's calendar and see hours logged
-- against them - without the worker ever becoming a member of the client's account.
create table connection_invites (
    id                 uuid primary key,
    employer_account_id uuid not null references accounts (id) on delete cascade,
    email              text not null,
    status             text not null default 'PENDING' check (status in ('PENDING', 'ACCEPTED', 'REVOKED')),
    created_at         timestamptz not null default now(),
    accepted_at        timestamptz
);
create index idx_connection_invites_email_status on connection_invites (email, status);

create table connections (
    id                   uuid primary key,
    employer_account_id  uuid not null references accounts (id) on delete cascade,
    worker_account_id    uuid not null references accounts (id) on delete cascade,
    worker_user_id       uuid not null references users (id) on delete cascade,
    worker_company_id    uuid not null references companies (id) on delete cascade,
    status               text not null default 'ACTIVE' check (status in ('ACTIVE', 'REVOKED')),
    created_at           timestamptz not null default now(),
    revoked_at           timestamptz
);
create index idx_connections_employer on connections (employer_account_id) where status = 'ACTIVE';
create index idx_connections_worker on connections (worker_account_id) where status = 'ACTIVE';
