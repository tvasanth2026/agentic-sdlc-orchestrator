create table engineering_outcomes (
    id uuid primary key,
    revision_id uuid not null unique references workflow_revisions(id),
    outcome_json text not null,
    outcome_hash varchar(64) not null,
    release_ready boolean not null,
    created_at timestamp with time zone not null
);

create table criterion_traceability (
    id uuid primary key,
    revision_id uuid not null references workflow_revisions(id),
    requirement_id varchar(160) not null,
    criterion_id varchar(160) not null,
    criterion_text text not null,
    behavioral boolean not null,
    planning_task_ids text not null,
    production_paths text not null,
    test_paths text not null,
    validation_attempt_ids text not null,
    artifact_hashes text not null,
    feature_proof text not null,
    completion_status varchar(40) not null,
    unique (revision_id, criterion_id)
);

alter table approvals add column workflow_revision integer null;
alter table approvals add column role varchar(80) null;
alter table approvals add column invalidated_at timestamp with time zone null;

create index idx_traceability_revision on criterion_traceability(revision_id, completion_status);
