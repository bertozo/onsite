-- Identity model: an account is a workspace (a personal workspace or a business/employer
-- workspace); a user can hold a membership, with a role, in more than one account.
create table accounts (
    id           uuid primary key default gen_random_uuid(),
    kind         text not null check (kind in ('PERSONAL', 'BUSINESS')),
    name         text not null,
    created_at   timestamptz not null default now(),
    updated_at   timestamptz not null default now()
);

-- id matches the Supabase Auth user id (the JWT "sub" claim) -- this table never stores
-- a password, Supabase Auth owns credentials entirely.
create table users (
    id           uuid primary key,
    email        text not null unique,
    display_name text,
    created_at   timestamptz not null default now(),
    updated_at   timestamptz not null default now()
);

create table memberships (
    id           uuid primary key default gen_random_uuid(),
    user_id      uuid not null references users (id) on delete cascade,
    account_id   uuid not null references accounts (id) on delete cascade,
    role         text not null check (role in ('OWNER', 'WORKER')),
    created_at   timestamptz not null default now(),
    updated_at   timestamptz not null default now(),
    unique (user_id, account_id)
);

create index idx_memberships_user on memberships (user_id);
create index idx_memberships_account on memberships (account_id);
