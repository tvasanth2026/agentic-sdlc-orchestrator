alter table engineering_artifacts add column input_hashes text not null default '[]';
alter table engineering_artifacts add column producing_agent varchar(100) null;
alter table engineering_artifacts add column provider varchar(80) null;
alter table engineering_artifacts add column model varchar(160) null;

create table agent_invocations (
    id uuid primary key,
    workflow_id uuid not null references workflows(id),
    revision_id uuid not null references workflow_revisions(id),
    task_id uuid not null references agent_tasks(id),
    agent_role varchar(100) not null,
    provider varchar(80) not null,
    model varchar(160) not null,
    attempt_number integer not null,
    input_artifact_hashes text not null,
    output_json text not null,
    output_hash varchar(64) not null,
    decisions text not null,
    assumptions text not null,
    risks text not null,
    duration_ms bigint not null,
    generated_artifact_ids text not null,
    created_at timestamp with time zone not null,
    unique (task_id, attempt_number)
);

create index idx_agent_invocations_revision on agent_invocations(revision_id, created_at);
