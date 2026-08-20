CREATE TABLE IF NOT EXISTS api_football_fixture_statistics_fetches (
    fixture_id BIGINT PRIMARY KEY
        REFERENCES api_football_fixtures(fixture_id),
    status VARCHAR(32) NOT NULL,
    source VARCHAR(32) NOT NULL,
    http_status INTEGER,
    returned_team_count INTEGER NOT NULL DEFAULT 0,
    error_message TEXT,
    unknown_labels JSONB NOT NULL DEFAULT '[]'::jsonb,
    raw_json JSONB,
    fetched_at TIMESTAMPTZ NOT NULL,
    parser_version INTEGER NOT NULL,
    CONSTRAINT chk_fixture_statistics_fetch_status CHECK (
        status IN ('COMPLETE', 'PARTIAL', 'UNAVAILABLE', 'UNSUPPORTED',
                   'FETCH_FAILED', 'API_ERROR', 'PARSE_ERROR')
    )
);

CREATE INDEX IF NOT EXISTS idx_fixture_statistics_fetch_status
    ON api_football_fixture_statistics_fetches(status, fetched_at);

CREATE TABLE IF NOT EXISTS api_football_fixture_statistics (
    fixture_id BIGINT NOT NULL
        REFERENCES api_football_fixtures(fixture_id),
    team_id BIGINT NOT NULL,
    team_side VARCHAR(8) NOT NULL,
    statistic_type VARCHAR(32) NOT NULL,
    value_numeric NUMERIC,
    value_status VARCHAR(16) NOT NULL,
    source_label TEXT,
    source VARCHAR(32) NOT NULL,
    fetched_at TIMESTAMPTZ NOT NULL,
    parser_version INTEGER NOT NULL,
    PRIMARY KEY (fixture_id, team_side, statistic_type),
    UNIQUE (fixture_id, team_id, statistic_type),
    CONSTRAINT chk_fixture_statistics_side CHECK (team_side IN ('HOME', 'AWAY')),
    CONSTRAINT chk_fixture_statistics_value_status CHECK (
        value_status IN ('KNOWN', 'ABSENT', 'INVALID')
    ),
    CONSTRAINT chk_fixture_statistics_value_shape CHECK (
        (value_status = 'KNOWN' AND value_numeric IS NOT NULL AND value_numeric >= 0)
        OR (value_status <> 'KNOWN' AND value_numeric IS NULL)
    )
);

CREATE INDEX IF NOT EXISTS idx_fixture_statistics_fixture_type
    ON api_football_fixture_statistics(fixture_id, statistic_type);

CREATE INDEX IF NOT EXISTS idx_fixture_statistics_type_status
    ON api_football_fixture_statistics(statistic_type, value_status);
