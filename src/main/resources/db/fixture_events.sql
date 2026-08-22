CREATE TABLE IF NOT EXISTS api_football_fixture_event_fetches (
    fixture_id BIGINT PRIMARY KEY REFERENCES api_football_fixtures(fixture_id),
    status VARCHAR(32) NOT NULL,
    source VARCHAR(32) NOT NULL,
    http_status INTEGER,
    returned_event_count INTEGER NOT NULL DEFAULT 0,
    error_message TEXT,
    raw_json JSONB,
    fetched_at TIMESTAMPTZ NOT NULL,
    parser_version INTEGER NOT NULL,
    CONSTRAINT chk_fixture_event_fetch_status CHECK (
        status IN ('COMPLETE','UNSUPPORTED_OR_EMPTY','FETCH_FAILED','API_ERROR','PARSE_ERROR')
    )
);

CREATE INDEX IF NOT EXISTS idx_fixture_event_fetch_status
    ON api_football_fixture_event_fetches(status, fetched_at);

CREATE TABLE IF NOT EXISTS api_football_fixture_events (
    fixture_id BIGINT NOT NULL REFERENCES api_football_fixtures(fixture_id),
    source_index INTEGER NOT NULL,
    team_id BIGINT,
    team_name TEXT,
    player_id BIGINT,
    player_name TEXT,
    assist_player_id BIGINT,
    assist_name TEXT,
    event_type TEXT,
    event_detail TEXT,
    minute INTEGER,
    extra_minute INTEGER,
    comments TEXT,
    source VARCHAR(32) NOT NULL,
    fetched_at TIMESTAMPTZ NOT NULL,
    parser_version INTEGER NOT NULL,
    PRIMARY KEY (fixture_id, source_index)
);

CREATE INDEX IF NOT EXISTS idx_fixture_events_type
    ON api_football_fixture_events(fixture_id, event_type, event_detail);

CREATE INDEX IF NOT EXISTS idx_fixture_events_player
    ON api_football_fixture_events(player_id);
