-- The invoice-header details (business name, ABN, bank account) used to live only on each
-- device (Android's Room `profile` table, the web app's localStorage). One row per user
-- here lets both apps show the same profile. updated_at_millis is the client's own edit
-- time, not a server timestamp, so devices can resolve last-write-wins against it.
create table user_profiles (
    user_id           uuid primary key references users (id) on delete cascade,
    name              text not null,
    role              text,
    phone             text,
    email             text,
    abn               text,
    bank_bsb          text,
    bank_account      text,
    updated_at_millis bigint not null
);
