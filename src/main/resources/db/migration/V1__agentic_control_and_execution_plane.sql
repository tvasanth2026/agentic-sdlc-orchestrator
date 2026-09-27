create table workflows (
    id uuid primary key,
    original_requirement text not null,
    repository_path varchar(1024) not null,
    status varchar(64) not null,
    current_revision integer not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create table workflow_revisions (
    id uuid primary key,
    workflow_id uuid not null references workflows(id),
    revision_number integer not null,
    parent_revision_id uuid null references workflow_revisions(id),
    requirement_hash varchar(64) not null,
    state varchar(64) not null,
    created_at timestamp with time zone not null,
    unique (workflow_id, revision_number)
);

create table agent_tasks (
    id uuid primary key,
    revision_id uuid not null references workflow_revisions(id),
    task_key varchar(160) not null,
    agent_role varchar(80) not null,
    state varchar(64) not null,
    attempt_count integer not null default 0,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    unique (revision_id, task_key)
);

create table task_dependencies (
    task_id uuid not null references agent_tasks(id),
    depends_on_task_id uuid not null references agent_tasks(id),
    primary key (task_id, depends_on_task_id),
    check (task_id <> depends_on_task_id)
);

create table execution_attempts (
    id uuid primary key,
    task_id uuid not null references agent_tasks(id),
    attempt_number integer not null,
    executor_type varchar(120) not null,
    status varchar(64) not null,
    started_at timestamp with time zone not null,
    completed_at timestamp with time zone null,
    failure_classification varchar(120) null,
    unique (task_id, attempt_number)
);

create table engineering_artifacts (
    id uuid primary key,
    revision_id uuid not null references workflow_revisions(id),
    task_id uuid null references agent_tasks(id),
    artifact_type varchar(100) not null,
    artifact_key varchar(200) not null,
    schema_version varchar(40) not null,
    storage_location varchar(2048) not null,
    sha256 varchar(64) not null,
    validation_status varchar(64) not null,
    created_at timestamp with time zone not null,
    unique (revision_id, artifact_key)
);

create table validation_results (
    id uuid primary key,
    artifact_id uuid not null references engineering_artifacts(id),
    validator varchar(160) not null,
    status varchar(64) not null,
    summary text not null,
    evidence_location varchar(2048) null,
    created_at timestamp with time zone not null
);

create table approvals (
    id uuid primary key,
    revision_id uuid not null references workflow_revisions(id),
    gate varchar(80) not null,
    evidence_hash varchar(64) not null,
    approver varchar(160) not null,
    decision varchar(40) not null,
    created_at timestamp with time zone not null
);

create table policy_decisions (
    id uuid primary key,
    revision_id uuid not null references workflow_revisions(id),
    task_id uuid null references agent_tasks(id),
    policy_key varchar(160) not null,
    decision varchar(40) not null,
    reason text not null,
    created_at timestamp with time zone not null
);

create table audit_events (
    id uuid primary key,
    workflow_id uuid not null references workflows(id),
    revision_id uuid null references workflow_revisions(id),
    task_id uuid null references agent_tasks(id),
    event_type varchar(120) not null,
    actor varchar(160) not null,
    details text not null,
    occurred_at timestamp with time zone not null
);

create index idx_agent_tasks_revision_state on agent_tasks(revision_id, state);
create index idx_attempts_task on execution_attempts(task_id, attempt_number);
create index idx_artifacts_revision on engineering_artifacts(revision_id, artifact_type);
create index idx_audit_workflow_time on audit_events(workflow_id, occurred_at);

