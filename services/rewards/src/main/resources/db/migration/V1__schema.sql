create table point_accounts (
    member_id varchar(64) primary key,
    family_id varchar(64) not null,
    points    integer     not null check (points >= 0),
    version   bigint      not null default 0
);

create table point_entries (
    id         bigint generated always as identity primary key,
    member_id  varchar(64)  not null references point_accounts (member_id),
    delta      integer      not null,
    reason     varchar(200) not null,
    created_at timestamptz  not null
);
create index point_entries_member_idx on point_entries (member_id, created_at desc);

create table artifact_states (
    child_id   varchar(64) primary key,
    family_id  varchar(64) not null,
    cohort     varchar(16) not null check (cohort in ('JUNIOR', 'TEEN')),
    week_slot  varchar(16) not null,
    week_start timestamptz not null
);

create table artifact_sold_out (
    child_id    varchar(64) not null references artifact_states (child_id) on delete cascade,
    artifact_id varchar(32) not null,
    primary key (child_id, artifact_id)
);

-- один артефакт на слот у ребёнка; новая покупка заменяет строку
create table owned_artifacts (
    id              bigint generated always as identity primary key,
    child_id        varchar(64)    not null references artifact_states (child_id) on delete cascade,
    slot            varchar(16)    not null,
    artifact_id     varchar(32)    not null,
    name            varchar(100)   not null,
    rarity          varchar(16)    not null check (rarity in ('COMMON', 'RARE', 'LEGENDARY')),
    category        varchar(32)    not null,
    cashback        integer        not null check (cashback between 1 and 100),
    cycles          integer        not null check (cycles >= 1),
    bought_at       timestamptz    not null,
    active_until    timestamptz    not null,
    cashback_earned numeric(19, 2) not null default 0,
    unique (child_id, slot)
);

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

create table event_publication (
    id                     uuid primary key,
    listener_id            text        not null,
    event_type             text        not null,
    serialized_event       text        not null,
    publication_date       timestamptz not null,
    completion_date        timestamptz,
    status                 text,
    completion_attempts    integer,
    last_resubmission_date timestamptz
);
create index event_publication_serialized_event_hash_idx on event_publication using hash (serialized_event);
create index event_publication_by_completion_date_idx on event_publication (completion_date);
