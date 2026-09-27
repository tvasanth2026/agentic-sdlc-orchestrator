create table task_claims (
    task_id uuid primary key references agent_tasks(id),
    worker_id varchar(160) not null,
    fencing_token bigint not null,
    lease_expires_at timestamp with time zone not null,
    heartbeat_at timestamp with time zone not null,
    claimed_at timestamp with time zone not null
);

create table idempotent_effects (
    effect_key varchar(240) primary key,
    revision_id uuid not null references workflow_revisions(id),
    effect_type varchar(120) not null,
    evidence_hash varchar(64) not null,
    created_at timestamp with time zone not null
);

create index idx_task_claims_expiry on task_claims(lease_expires_at);
create index idx_effects_revision on idempotent_effects(revision_id, effect_type);
