create table repository_analyses (
    id uuid primary key,
    revision_id uuid not null unique references workflow_revisions(id),
    workspace_location varchar(2048) not null,
    baseline_manifest_hash varchar(64) not null,
    analysis_json text not null,
    analysis_hash varchar(64) not null,
    created_at timestamp with time zone not null
);

create table engineering_plans (
    id uuid primary key,
    revision_id uuid not null unique references workflow_revisions(id),
    requirement_hash varchar(64) not null,
    repository_analysis_hash varchar(64) not null,
    plan_json text not null,
    plan_hash varchar(64) not null,
    created_at timestamp with time zone not null
);

create index idx_repository_analysis_revision on repository_analyses(revision_id);
create index idx_engineering_plan_revision on engineering_plans(revision_id);

