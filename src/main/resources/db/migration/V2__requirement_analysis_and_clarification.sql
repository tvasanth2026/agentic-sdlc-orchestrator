create table requirement_analyses (
    id uuid primary key,
    revision_id uuid not null unique references workflow_revisions(id),
    normalized_problem text not null,
    ambiguity_required boolean not null,
    risk_level varchar(32) not null,
    source_mutation_allowed boolean not null,
    created_at timestamp with time zone not null
);

create table requirement_items (
    id uuid primary key,
    analysis_id uuid not null references requirement_analyses(id),
    item_type varchar(40) not null,
    item_key varchar(80) not null,
    content text not null,
    behavioral boolean not null,
    unique (analysis_id, item_type, item_key)
);

create table clarification_questions (
    id uuid primary key,
    analysis_id uuid not null references requirement_analyses(id),
    question_key varchar(80) not null,
    dimension varchar(80) not null,
    prompt text not null,
    resolved boolean not null,
    unique (analysis_id, question_key)
);

create table clarifications (
    id uuid primary key,
    workflow_id uuid not null references workflows(id),
    from_revision_id uuid not null references workflow_revisions(id),
    new_revision_id uuid not null references workflow_revisions(id),
    actor varchar(160) not null,
    answers_json text not null,
    clarified_requirement text not null,
    created_at timestamp with time zone not null
);

create table revision_outputs (
    id uuid primary key,
    revision_id uuid not null references workflow_revisions(id),
    output_key varchar(120) not null,
    input_dimension varchar(80) not null,
    status varchar(40) not null,
    reused_from_output_id uuid null references revision_outputs(id),
    unique (revision_id, output_key)
);

create index idx_requirement_items_analysis on requirement_items(analysis_id, item_type);
create index idx_questions_analysis on clarification_questions(analysis_id, resolved);
create index idx_clarifications_workflow on clarifications(workflow_id, created_at);
create index idx_revision_outputs_revision on revision_outputs(revision_id, status);

