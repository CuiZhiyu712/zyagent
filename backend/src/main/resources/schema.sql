CREATE TABLE IF NOT EXISTS chat_session (
    id VARCHAR(64) PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    agent_mode VARCHAR(64) NOT NULL,
    task_type VARCHAR(64),
    active_skill VARCHAR(64),
    current_day INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS chat_message (
    id VARCHAR(64) PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL,
    role VARCHAR(32) NOT NULL,
    content TEXT NOT NULL,
    references_json TEXT,
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS tool_call_record (
    id VARCHAR(64) PRIMARY KEY,
    session_id VARCHAR(64),
    tool_name VARCHAR(128) NOT NULL,
    input_json TEXT,
    output_summary TEXT,
    success BOOLEAN NOT NULL,
    error_message TEXT,
    called_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS agent_task (
    id VARCHAR(64) PRIMARY KEY,
    owner_id VARCHAR(128) NOT NULL,
    session_id VARCHAR(64),
    agent_mode VARCHAR(64) NOT NULL,
    state VARCHAR(32) NOT NULL,
    request_text TEXT NOT NULL,
    result_text MEDIUMTEXT,
    error_code VARCHAR(64),
    error_message TEXT,
    created_at TIMESTAMP NOT NULL,
    started_at TIMESTAMP NULL,
    finished_at TIMESTAMP NULL,
    updated_at TIMESTAMP NOT NULL,
    idempotency_key VARCHAR(128) NULL,
    INDEX idx_agent_task_owner_updated (owner_id, updated_at),
    INDEX idx_agent_task_state (state),
    UNIQUE KEY uk_agent_task_idem (idempotency_key)
);

CREATE TABLE IF NOT EXISTS agent_task_step (
    id VARCHAR(64) PRIMARY KEY,
    task_id VARCHAR(64) NOT NULL,
    step_no INT NOT NULL,
    title VARCHAR(255) NOT NULL,
    tool_name VARCHAR(128),
    state VARCHAR(32) NOT NULL,
    input_summary TEXT,
    output_summary TEXT,
    error_message TEXT,
    attempt INT NOT NULL DEFAULT 1,
    duration_ms BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    UNIQUE KEY uk_agent_task_step (task_id, step_no),
    INDEX idx_agent_task_step_task (task_id)
);

CREATE TABLE IF NOT EXISTS document (
    id VARCHAR(64) PRIMARY KEY,
    filename VARCHAR(255) NOT NULL,
    knowledge_type VARCHAR(64) NOT NULL,
    parse_status VARCHAR(64) NOT NULL,
    chunk_count INT NOT NULL DEFAULT 0,
    uploaded_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS document_chunk (
    id VARCHAR(96) PRIMARY KEY,
    document_id VARCHAR(64) NOT NULL,
    chunk_index INT NOT NULL,
    content TEXT NOT NULL,
    vector_id VARCHAR(128),
    metadata_json TEXT,
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS job_posting (
    id VARCHAR(64) PRIMARY KEY,
    company VARCHAR(128),
    title VARCHAR(255),
    city VARCHAR(128),
    job_type VARCHAR(64),
    direction VARCHAR(64),
    skills_json TEXT,
    source_url VARCHAR(1024),
    application_status VARCHAR(64),
    raw_text TEXT,
    collected_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS interview_session (
    id VARCHAR(64) PRIMARY KEY,
    owner_id VARCHAR(128),
    job_id VARCHAR(64),
    jd_snapshot TEXT,
    interview_type VARCHAR(64) NOT NULL,
    difficulty VARCHAR(32) NOT NULL,
    state VARCHAR(32) NOT NULL,
    current_turn INT NOT NULL DEFAULT 0,
    max_turns INT NOT NULL DEFAULT 8,
    max_follow_ups INT NOT NULL DEFAULT 1,
    summary TEXT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    INDEX idx_interview_session_owner (owner_id, updated_at)
);

CREATE TABLE IF NOT EXISTS interview_turn (
    id VARCHAR(64) PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL,
    turn_no INT NOT NULL,
    state VARCHAR(32) NOT NULL DEFAULT 'NEXT_QUESTION_READY',
    question TEXT NOT NULL,
    answer TEXT,
    evaluation TEXT,
    next_question TEXT,
    request_id VARCHAR(128),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NULL,
    UNIQUE KEY uk_interview_turn (session_id, turn_no),
    INDEX idx_interview_turn_request (session_id, request_id)
);

CREATE TABLE IF NOT EXISTS profile_suggestion (
    id VARCHAR(64) PRIMARY KEY,
    owner_id VARCHAR(128) NOT NULL,
    skill_key VARCHAR(128) NOT NULL,
    suggested_level VARCHAR(32) NOT NULL,
    previous_level VARCHAR(32),
    evidence TEXT NOT NULL,
    rationale TEXT,
    source_type VARCHAR(64) NOT NULL,
    source_id VARCHAR(128),
    model VARCHAR(64),
    state VARCHAR(32) NOT NULL,
    note TEXT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NULL,
    INDEX idx_profile_suggestion_owner (owner_id, state)
);

CREATE TABLE IF NOT EXISTS user_skill (
    owner_id VARCHAR(128) NOT NULL,
    skill_key VARCHAR(128) NOT NULL,
    level VARCHAR(32) NOT NULL,
    confidence DECIMAL(4,3) NOT NULL,
    evidence TEXT NOT NULL,
    source_type VARCHAR(64) NOT NULL,
    source_id VARCHAR(128),
    version INT NOT NULL DEFAULT 1,
    updated_at TIMESTAMP NOT NULL,
    PRIMARY KEY (owner_id, skill_key)
);

CREATE TABLE IF NOT EXISTS profile_skill_evidence (
    id VARCHAR(64) PRIMARY KEY,
    owner_id VARCHAR(128) NOT NULL,
    skill_key VARCHAR(128) NOT NULL,
    summary TEXT,
    source_type VARCHAR(64),
    source_id VARCHAR(128),
    created_at TIMESTAMP NOT NULL,
    INDEX idx_profile_evidence_skill (owner_id, skill_key, created_at)
);

CREATE TABLE IF NOT EXISTS profile_skill_history (
    id VARCHAR(64) PRIMARY KEY,
    owner_id VARCHAR(128) NOT NULL,
    skill_key VARCHAR(128) NOT NULL,
    old_level VARCHAR(32),
    new_level VARCHAR(32),
    old_confidence DECIMAL(4,3),
    new_confidence DECIMAL(4,3),
    change_type VARCHAR(32) NOT NULL,
    source_type VARCHAR(64),
    source_id VARCHAR(128),
    note TEXT,
    changed_at TIMESTAMP NOT NULL,
    INDEX idx_profile_history_skill (owner_id, skill_key, changed_at)
);

CREATE TABLE IF NOT EXISTS job_source (
    id VARCHAR(64) PRIMARY KEY,
    company VARCHAR(128) NOT NULL,
    name VARCHAR(255) NOT NULL,
    url VARCHAR(1024) NOT NULL,
    enabled BOOLEAN NOT NULL,
    keywords VARCHAR(512),
    last_collected_at TIMESTAMP NULL,
    last_status VARCHAR(64)
);

CREATE TABLE IF NOT EXISTS job_collect_log (
    id VARCHAR(64) PRIMARY KEY,
    source_id VARCHAR(64),
    source_name VARCHAR(255),
    started_at TIMESTAMP NOT NULL,
    finished_at TIMESTAMP NOT NULL,
    status VARCHAR(64) NOT NULL,
    added INT NOT NULL DEFAULT 0,
    updated INT NOT NULL DEFAULT 0,
    skipped INT NOT NULL DEFAULT 0,
    failed INT NOT NULL DEFAULT 0,
    error_message TEXT
);
