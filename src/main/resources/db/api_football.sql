CREATE TABLE IF NOT EXISTS api_football_fixtures (
                                                     fixture_id BIGINT PRIMARY KEY,

                                                     kickoff_at TIMESTAMPTZ NOT NULL,
                                                     fixture_date DATE NOT NULL,

                                                     league_id BIGINT,
                                                     league_name TEXT,
                                                     league_country TEXT,
                                                     season INTEGER,
                                                     round TEXT,

                                                     home_team_id BIGINT,
                                                     home_team_name TEXT NOT NULL,

                                                     away_team_id BIGINT,
                                                     away_team_name TEXT NOT NULL,

                                                     goals_home INTEGER,
                                                     goals_away INTEGER,

                                                     status_short TEXT,
                                                     status_long TEXT,

                                                     raw_json JSONB NOT NULL,

                                                     fetched_at TIMESTAMPTZ NOT NULL
                                                     DEFAULT NOW()
    );

CREATE INDEX IF NOT EXISTS idx_api_football_fixture_date
    ON api_football_fixtures(fixture_date);

CREATE INDEX IF NOT EXISTS idx_api_football_home_team
    ON api_football_fixtures(home_team_name);

CREATE INDEX IF NOT EXISTS idx_api_football_away_team
    ON api_football_fixtures(away_team_name);


CREATE TABLE IF NOT EXISTS api_football_fetch_days (
                                                       fixture_date DATE PRIMARY KEY,

                                                       fixture_count INTEGER NOT NULL,

                                                       fetched_at TIMESTAMPTZ NOT NULL
                                                       DEFAULT NOW()
    );


ALTER TABLE bet_legs
    ADD COLUMN IF NOT EXISTS resolved_provider VARCHAR(32);

ALTER TABLE bet_legs
    ADD COLUMN IF NOT EXISTS resolved_external_event_id TEXT;

CREATE INDEX IF NOT EXISTS idx_bet_legs_resolved_provider_event
    ON bet_legs(
    resolved_provider,
    resolved_external_event_id
    );CREATE TABLE IF NOT EXISTS api_football_fixtures (
                                                           fixture_id BIGINT PRIMARY KEY,

                                                           kickoff_at TIMESTAMPTZ NOT NULL,
                                                           fixture_date DATE NOT NULL,

                                                           league_id BIGINT,
                                                           league_name TEXT,
                                                           league_country TEXT,
                                                           season INTEGER,
                                                           round TEXT,

                                                           home_team_id BIGINT,
                                                           home_team_name TEXT NOT NULL,

                                                           away_team_id BIGINT,
                                                           away_team_name TEXT NOT NULL,

                                                           goals_home INTEGER,
                                                           goals_away INTEGER,

                                                           status_short TEXT,
                                                           status_long TEXT,

                                                           raw_json JSONB NOT NULL,

                                                           fetched_at TIMESTAMPTZ NOT NULL
                                                           DEFAULT NOW()
    );

CREATE INDEX IF NOT EXISTS idx_api_football_fixture_date
    ON api_football_fixtures(fixture_date);

CREATE INDEX IF NOT EXISTS idx_api_football_home_team
    ON api_football_fixtures(home_team_name);

CREATE INDEX IF NOT EXISTS idx_api_football_away_team
    ON api_football_fixtures(away_team_name);


CREATE TABLE IF NOT EXISTS api_football_fetch_days (
                                                       fixture_date DATE PRIMARY KEY,

                                                       fixture_count INTEGER NOT NULL,

                                                       fetched_at TIMESTAMPTZ NOT NULL
                                                       DEFAULT NOW()
    );


ALTER TABLE bet_legs
    ADD COLUMN IF NOT EXISTS resolved_provider VARCHAR(32);

ALTER TABLE bet_legs
    ADD COLUMN IF NOT EXISTS resolved_external_event_id TEXT;

CREATE INDEX IF NOT EXISTS idx_bet_legs_resolved_provider_event
    ON bet_legs(
    resolved_provider,
    resolved_external_event_id
    );