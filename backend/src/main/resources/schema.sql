CREATE TABLE IF NOT EXISTS chat_session (
    id VARCHAR(64) PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    agent_mode VARCHAR(64) NOT NULL,
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
