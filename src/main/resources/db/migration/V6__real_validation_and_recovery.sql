alter table execution_attempts add column capability varchar(80) null;
alter table execution_attempts add column exit_code integer null;
alter table execution_attempts add column duration_ms bigint null;
alter table execution_attempts add column timed_out boolean not null default false;
alter table execution_attempts add column stdout_text text null;
alter table execution_attempts add column stderr_text text null;
alter table execution_attempts add column discovered_tests integer null;
alter table execution_attempts add column failed_tests integer null;
alter table execution_attempts add column coverage_summary text null;
alter table execution_attempts add column recovery_decision varchar(80) null;
alter table execution_attempts add column decision_reason text null;
alter table execution_attempts add column repair_proposal_id uuid null references patch_proposals(id);

create table rollback_actions (
    id uuid primary key,
    revision_id uuid not null references workflow_revisions(id),
    reason text not null,
    expected_manifest_hash varchar(64) not null,
    restored_manifest_hash varchar(64) not null,
    verified boolean not null,
    created_at timestamp with time zone not null
);

create index idx_execution_attempts_status on execution_attempts(status, failure_classification);
