-- Lets an OWNER invite a WORKER into their account by email, without needing a real
-- email provider yet: the invite just sits here until the invited user's own login
-- finds it and accepts it (see AcceptInvite in the identity module).
create table invites (
    id           uuid primary key,
    account_id   uuid not null references accounts (id) on delete cascade,
    email        text not null,
    role         text not null check (role in ('WORKER')),
    status       text not null default 'PENDING' check (status in ('PENDING', 'ACCEPTED', 'REVOKED')),
    created_at   timestamptz not null default now(),
    accepted_at  timestamptz
);
create index idx_invites_email_status on invites (email, status);
create index idx_invites_account on invites (account_id);

-- Who actually did the work / who a job is scheduled for, distinct from account_id
-- (whose workspace the row lives in) - needed so a WORKER's own report can be scoped
-- to just their rows while an OWNER's report sees everyone's.
alter table tracking_sessions add column created_by_user_id uuid references users (id);
alter table planned_jobs add column created_by_user_id uuid references users (id);
alter table planned_jobs add column assigned_user_id uuid references users (id);
