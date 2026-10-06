-- Cursor for the periodic reconciliation sweep against Bastion's
-- /internal/posts/changed. Single row keyed by a constant id.

create table reconciliation_cursor (
    id text primary key,
    cursor text,
    updated_at_utc timestamptz not null default now()
);
