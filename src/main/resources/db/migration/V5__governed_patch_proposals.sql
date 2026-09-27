create table patch_proposals (
    id uuid primary key,
    revision_id uuid not null references workflow_revisions(id),
    agent_role varchar(100) not null,
    provider varchar(80) not null,
    model varchar(160) not null,
    proposal_json text not null,
    proposal_hash varchar(64) not null,
    baseline_manifest_hash varchar(64) not null,
    applied_manifest_hash varchar(64) not null,
    unified_diff text not null,
    status varchar(40) not null,
    created_at timestamp with time zone not null,
    unique (revision_id, proposal_hash)
);

create table proposed_file_operations (
    id uuid primary key,
    proposal_id uuid not null references patch_proposals(id),
    operation_index integer not null,
    relative_path varchar(1024) not null,
    operation_type varchar(20) not null,
    expected_sha256 varchar(64) null,
    content_sha256 varchar(64) null,
    reason text not null,
    requirement_id varchar(160) not null,
    acceptance_criterion_ids text not null,
    task_id varchar(200) not null,
    input_artifact_hashes text not null,
    unique (proposal_id, operation_index),
    unique (proposal_id, relative_path)
);

create table applied_file_operations (
    id uuid primary key,
    proposal_id uuid not null references patch_proposals(id),
    relative_path varchar(1024) not null,
    operation_type varchar(20) not null,
    before_sha256 varchar(64) null,
    after_sha256 varchar(64) null,
    applied_at timestamp with time zone not null,
    unique (proposal_id, relative_path)
);

create index idx_patch_proposals_revision on patch_proposals(revision_id, created_at);
