create table notifications (
    id         varchar(64) primary key,
    family_id  varchar(64)  not null,
    audience   varchar(8)   not null check (audience in ('FEED', 'INBOX')),
    child_id   varchar(64),
    kind       varchar(16),
    tone       varchar(8),
    title      varchar(300),
    text       varchar(500) not null,
    request_id varchar(64),
    task_id    varchar(64),
    created_at timestamptz  not null,
    seen       boolean      not null default false
);
-- лента семьи (последние 40) и inbox ребёнка (последние 20, непрочитанные)
create index notifications_feed_idx on notifications (family_id, audience, created_at desc);
create index notifications_inbox_idx on notifications (family_id, audience, child_id, created_at desc);

create table processed_events (
    event_id     uuid        not null,
    consumer     varchar(64) not null,
    processed_at timestamptz not null,
    primary key (event_id, consumer)
);

create table shedlock (
    name       varchar(64) primary key,
    lock_until timestamptz  not null,
    locked_at  timestamptz  not null,
    locked_by  varchar(255) not null
);
