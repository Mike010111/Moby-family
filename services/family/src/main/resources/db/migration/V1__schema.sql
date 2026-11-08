create table members (
    id             varchar(64) primary key,
    family_id      varchar(64)  not null,
    kind           varchar(16)  not null check (kind in ('RELATIVE', 'CHILD')),
    name           varchar(100) not null,
    label          varchar(50),
    genitive       varchar(50),
    dative         varchar(50),
    accusative     varchar(50),
    main           boolean      not null default false,
    female         boolean      not null default false,
    position       integer      not null default 0,
    surname        varchar(100),
    name_gen       varchar(100),
    age            integer,
    cohort         varchar(16) check (cohort in ('JUNIOR', 'TEEN')),
    frozen         boolean      not null default false,
    frozen_changed_at timestamptz,
    geo_place      varchar(100),
    geo_x          double precision,
    geo_y          double precision,
    geo_distance   double precision,
    geo_updated_at timestamptz,
    battery        integer,
    version        bigint       not null default 0
);
create index members_family_idx on members (family_id, position);
-- ровно один главный родственник в семье
create unique index members_main_uq on members (family_id) where main;

create table requests (
    id           varchar(64) primary key,
    family_id    varchar(64)    not null,
    child_id     varchar(64)    not null references members (id),
    relative_id  varchar(64)    not null references members (id),
    kind         varchar(16)    not null check (kind in ('TOPUP', 'P2P_OVER', 'PIGGY_UPDATE', 'PIGGY_DELETE')),
    amount       numeric(19, 2) not null check (amount >= 0),
    reason       varchar(200)   not null,
    contact_name varchar(100),
    piggy_id     varchar(64),
    status       varchar(16)    not null check (status in ('SENT', 'PENDING', 'APPROVED', 'DECLINED', 'CANCELLED')),
    resolution   varchar(200),
    created_at   timestamptz    not null,
    resolved_at  timestamptz,
    version      bigint         not null default 0
);
create index requests_family_created_idx on requests (family_id, created_at desc);
create index requests_child_created_idx on requests (child_id, created_at desc);
-- по копилке — не больше одного ожидающего запроса
create unique index requests_piggy_pending_uq on requests (piggy_id) where status = 'PENDING';

create table tasks (
    id         varchar(64) primary key,
    family_id  varchar(64)    not null,
    child_id   varchar(64)    not null references members (id),
    title      varchar(50)    not null,
    reward     numeric(19, 2) not null check (reward >= 0),
    status     varchar(16)    not null check (status in ('OPEN', 'REVIEW', 'DONE')),
    created_at timestamptz    not null,
    version    bigint         not null default 0
);
create index tasks_child_created_idx on tasks (child_id, created_at desc);

create table piggies (
    id         varchar(64) primary key,
    family_id  varchar(64)    not null,
    child_id   varchar(64)    not null references members (id),
    goal       varchar(30)    not null,
    target     numeric(19, 2) not null check (target > 0),
    saved      numeric(19, 2) not null check (saved >= 0),
    status     varchar(16)    not null check (status in ('ACTIVE', 'COMPLETED', 'CLOSED')),
    created_at timestamptz    not null,
    version    bigint         not null default 0
);
create index piggies_child_active_idx on piggies (child_id, created_at) where status = 'ACTIVE';
create unique index piggies_goal_uq on piggies (child_id, lower(goal)) where status = 'ACTIVE';

create table lesson_progress (
    child_id  varchar(64) primary key references members (id),
    family_id varchar(64) not null,
    done      integer     not null check (done >= 0),
    version   bigint      not null default 0
);

-- Идемпотентные консьюмеры Kafka: событие обрабатывается одним консьюмером один раз
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
