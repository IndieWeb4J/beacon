-- Beacon's own schema: webmention state. Beacon owns no content, so posts are
-- referenced only by the content service's post id (no cross-service FK).

create type webmention_state as enum ('ACTIVE', 'INACTIVE');
create type received_webmention_state as enum ('PENDING', 'VERIFIED', 'REJECTED', 'DELETED', 'ERROR');
create type webmention_interaction as enum ('REPLY', 'LIKE', 'REPOST', 'BOOKMARK', 'MENTION', 'RSVP');

-- Outbound notifications: one row per (source post URL, target URL) we send a
-- webmention for, with delivery/retry state.
create table webmention_notifications (
    id uuid primary key,
    source_url text not null,
    target_url text not null,
    state webmention_state not null,
    delivered boolean not null default false,
    attempts int not null default 0,
    last_error text,
    last_status_code int,
    last_attempt_at timestamptz,
    next_attempt_at timestamptz,
    created_at_utc timestamptz not null default now(),
    updated_at_utc timestamptz not null default now()
);

create unique index uq_webmention_source_target on webmention_notifications (source_url, target_url);
create index idx_webmention_source_state on webmention_notifications (source_url, state);
create index idx_webmention_due on webmention_notifications (state, delivered, next_attempt_at);

-- Discovered webmention endpoints, cached only while the target advertises
-- cacheability.
create table webmention_endpoint_cache (
    target_url text primary key,
    endpoint_url text,
    discovered_at timestamptz not null default now(),
    expires_at timestamptz not null,
    updated_at timestamptz not null default now()
);

create index idx_endpoint_cache_expires_at on webmention_endpoint_cache (expires_at);

-- Inbound webmentions, keyed by (source_url, post_id). post_id is the content
-- service's post id; there is deliberately no foreign key across services.
create table received_webmentions (
    id uuid primary key,
    post_id uuid not null,
    source_url text not null,
    target_url text not null,
    state received_webmention_state not null default 'PENDING',
    interaction webmention_interaction,
    author_name text,
    author_url text,
    author_photo text,
    content_text text[],
    content_html text[],
    raw_mf2 jsonb,
    last_error text,
    first_seen_at timestamptz not null default now(),
    verified_at timestamptz,
    updated_at_utc timestamptz not null default now()
);

create unique index uq_received_webmention_source_post on received_webmentions (source_url, post_id);
create index idx_received_webmention_post_state on received_webmentions (post_id, state);
create index idx_received_webmention_source_state on received_webmentions (source_url, state);

-- Per-post high-water mark for consumed content.post.* events (version guard).
create table content_event_checkpoint (
    post_id uuid primary key,
    version bigint not null,
    updated_at_utc timestamptz not null default now()
);
