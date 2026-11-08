-- Счета. Баланс клиентских счетов кэшируется и сверяется с Σ ledger_entries; у SYSTEM — только Σ проводок.
create table accounts (
    id              varchar(64) primary key,
    family_id       varchar(64),
    owner_id        varchar(64)    not null,
    kind            varchar(16)    not null,
    cohort          varchar(16),
    holder_name     varchar(100),
    holder_label    varchar(50),
    holder_genitive varchar(50),
    holder_dative   varchar(50),
    holder_female   boolean        not null default false,
    balance         numeric(19, 2) not null default 0,
    frozen          boolean        not null default false,
    card_title      varchar(100),
    card_mask       varchar(20),
    card_number     varchar(25),
    card_valid_thru varchar(5),
    card_student_id varchar(20),
    card_holder     varchar(100),
    card_image      varchar(200),
    package_name    varchar(100),
    external_ref    varchar(64),
    stats_tx_count  integer,
    stats_since     varchar(10),
    version         bigint         not null default 0,
    constraint accounts_kind_chk check (kind in ('PARENT', 'CHILD', 'SAVINGS', 'SYSTEM')),
    constraint accounts_balance_chk check (kind = 'SYSTEM' or balance >= 0)
);
-- поиск счёта ребёнка / родителя семьи / копилки — и уникальность этих ролей
create unique index accounts_child_owner_uq on accounts (owner_id) where kind = 'CHILD';
create unique index accounts_parent_family_uq on accounts (family_id) where kind = 'PARENT';
create unique index accounts_savings_ref_uq on accounts (external_ref) where kind = 'SAVINGS';

create table category_permissions (
    account_id   varchar(64) not null references accounts (id) on delete cascade,
    category_key varchar(32) not null,
    allowed      boolean     not null,
    primary key (account_id, category_key)
);

-- Двойная запись: сумма amount по transaction_id всегда 0
create table ledger_transactions (
    id          uuid primary key,
    kind        varchar(32)  not null,
    description varchar(200),
    created_at  timestamptz  not null
);

create table ledger_entries (
    id             bigint generated always as identity primary key,
    transaction_id uuid           not null references ledger_transactions (id),
    account_id     varchar(64)    not null references accounts (id),
    amount         numeric(19, 2) not null check (amount <> 0)
);
create index ledger_entries_account_idx on ledger_entries (account_id);
create index ledger_entries_transaction_idx on ledger_entries (transaction_id);

-- Выписка ребёнка; по ней же считаются траты за период лимита
create table account_operations (
    id                    varchar(64) primary key,
    account_id            varchar(64)    not null references accounts (id),
    title                 varchar(200)   not null,
    amount                numeric(19, 2) not null,
    mcc                   varchar(16)    not null,
    category              varchar(32)    not null,
    online                boolean        not null default false,
    status                varchar(16)    not null check (status in ('COMPLETED', 'DECLINED')),
    decline_code          varchar(32),
    note                  varchar(300),
    created_at            timestamptz    not null,
    ledger_transaction_id uuid references ledger_transactions (id)
);
create index account_operations_account_created_idx on account_operations (account_id, created_at desc);

create table spending_limits (
    id           varchar(64) primary key,
    account_id   varchar(64)    not null references accounts (id) on delete cascade,
    period_count integer        not null check (period_count between 1 and 50),
    unit         varchar(8)     not null check (unit in ('day', 'week', 'month')),
    amount       numeric(19, 2) not null check (amount > 0),
    categories   text[]         not null default '{}',
    start_at     timestamptz
);
create index spending_limits_account_idx on spending_limits (account_id);

create table p2p_approvals (
    id          varchar(64) primary key,
    account_id  varchar(64)    not null references accounts (id) on delete cascade,
    request_id  varchar(64)    not null unique,
    max_amount  numeric(19, 2) not null check (max_amount > 0),
    status      varchar(16)    not null check (status in ('ACTIVE', 'USED', 'REPLACED')),
    used_amount numeric(19, 2),
    created_at  timestamptz    not null
);
-- не больше одного активного одобрения на счёт
create unique index p2p_approvals_active_uq on p2p_approvals (account_id) where status = 'ACTIVE';

create table subscriptions (
    id         varchar(64) primary key,
    account_id varchar(64)    not null references accounts (id) on delete cascade,
    title      varchar(100)   not null,
    price      numeric(19, 2) not null check (price > 0),
    next_label varchar(20),
    active     boolean        not null,
    icon       varchar(20),
    position   integer        not null default 0
);
create index subscriptions_account_idx on subscriptions (account_id, position);

create table autopay_rules (
    account_id  varchar(64) primary key references accounts (id) on delete cascade,
    enabled     boolean        not null,
    amount      numeric(19, 2) not null check (amount > 0),
    every       varchar(8)     not null check (every in ('day', 'week', 'month')),
    weekday     integer        not null check (weekday between 0 and 6),
    month_day   integer        not null check (month_day between 1 and 28),
    last_run_on date
);
create index autopay_rules_enabled_idx on autopay_rules (enabled) where enabled;

-- Idempotency-Key: уникальность на пользователя; повтор возвращает сохранённый ответ
create table idempotency_records (
    owner_id      varchar(64)  not null,
    idem_key      varchar(100) not null,
    operation     varchar(50)  not null,
    request_hash  char(64)     not null,
    response_body jsonb,
    created_at    timestamptz  not null,
    primary key (owner_id, idem_key)
);
create index idempotency_records_created_idx on idempotency_records (created_at);

create table shedlock (
    name       varchar(64) primary key,
    lock_until timestamptz  not null,
    locked_at  timestamptz  not null,
    locked_by  varchar(255) not null
);

-- Transactional outbox (Spring Modulith Event Publication Registry, схема v2)
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
