-- "Company" became "Client" across the whole product (Android, web and this API): the
-- record is the customer a tradesperson works for, and the two apps had drifted into
-- calling the same thing "Clientes" and "Empresas".
alter table companies rename to clients;
alter table clients rename constraint companies_pkey to clients_pkey;
alter table clients rename constraint companies_account_id_fkey to clients_account_id_fkey;
alter index idx_companies_account rename to idx_clients_account;

alter table invoices rename column company_name to client_name;
alter table tracking_sessions rename column company_name to client_name;
alter table planned_jobs rename column company_name to client_name;

alter table connections rename column worker_company_id to worker_client_id;
alter table connections rename constraint connections_worker_company_id_fkey to connections_worker_client_id_fkey;
