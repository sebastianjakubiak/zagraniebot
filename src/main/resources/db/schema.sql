CREATE TABLE IF NOT EXISTS authors (
                                       wp_author_id BIGINT PRIMARY KEY,
                                       display_name TEXT NOT NULL,

                                       slug TEXT,
                                       sample_post_id BIGINT,
                                       sample_post_url TEXT,

                                       is_tipster_candidate BOOLEAN NOT NULL DEFAULT FALSE,

                                       last_discovered_at TIMESTAMPTZ,
                                       last_discovery_from TIMESTAMPTZ,
                                       last_discovery_to TIMESTAMPTZ,

                                       last_discovery_editorial_posts INTEGER NOT NULL DEFAULT 0,
                                       last_discovery_editorial_legs INTEGER NOT NULL DEFAULT 0,

                                       first_seen_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
    );

ALTER TABLE authors
    ADD COLUMN IF NOT EXISTS slug TEXT;

ALTER TABLE authors
    ADD COLUMN IF NOT EXISTS sample_post_id BIGINT;

ALTER TABLE authors
    ADD COLUMN IF NOT EXISTS sample_post_url TEXT;

ALTER TABLE authors
    ADD COLUMN IF NOT EXISTS is_tipster_candidate BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE authors
    ADD COLUMN IF NOT EXISTS last_discovered_at TIMESTAMPTZ;

ALTER TABLE authors
    ADD COLUMN IF NOT EXISTS last_discovery_from TIMESTAMPTZ;

ALTER TABLE authors
    ADD COLUMN IF NOT EXISTS last_discovery_to TIMESTAMPTZ;

ALTER TABLE authors
    ADD COLUMN IF NOT EXISTS last_discovery_editorial_posts INTEGER NOT NULL DEFAULT 0;

ALTER TABLE authors
    ADD COLUMN IF NOT EXISTS last_discovery_editorial_legs INTEGER NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_authors_tipster_candidate
    ON authors(is_tipster_candidate);

CREATE INDEX IF NOT EXISTS idx_authors_slug
    ON authors(slug);


CREATE TABLE IF NOT EXISTS posts (
                                     wp_post_id BIGINT PRIMARY KEY,
                                     wp_author_id BIGINT NOT NULL REFERENCES authors(wp_author_id),
    slug TEXT,
    title TEXT NOT NULL,
    url TEXT NOT NULL,
    published_at TIMESTAMPTZ NOT NULL,
    modified_at TIMESTAMPTZ,
    raw_html TEXT NOT NULL,
    raw_metadata_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    content_hash CHAR(64) NOT NULL,
    imported_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
    );

CREATE INDEX IF NOT EXISTS idx_posts_author_published
    ON posts(wp_author_id, published_at);


CREATE TABLE IF NOT EXISTS bets (
                                    id BIGSERIAL PRIMARY KEY,

                                    wp_post_id BIGINT NOT NULL
                                    REFERENCES posts(wp_post_id)
    ON DELETE CASCADE,

    ordinal INTEGER NOT NULL,
    source_fingerprint CHAR(64) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,

    bet_type VARCHAR(32) NOT NULL,

    displayed_odds NUMERIC(12, 4),
    calculated_odds NUMERIC(12, 4),

    odds_source VARCHAR(32) NOT NULL,
    odds_verified BOOLEAN NOT NULL DEFAULT FALSE,

    odds_consistency VARCHAR(32) NOT NULL
    DEFAULT 'NOT_CHECKABLE',

    settlement_status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    settlement_source VARCHAR(16) NOT NULL DEFAULT 'NONE',

    settled_at TIMESTAMPTZ,
    manual_note TEXT,

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_bet_source
    UNIQUE (
               wp_post_id,
               source_fingerprint
           ),

    CONSTRAINT chk_bet_type
    CHECK (
              bet_type IN (
              'SINGLE',
              'COMBINED',
              'MULTI_UNVERIFIED'
                          )
    ),

    CONSTRAINT chk_bet_odds_source
    CHECK (
              odds_source IN (
              'SINGLE_LEG',
              'TITLE',
              'CALCULATED',
              'NONE'
                             )
    ),

    CONSTRAINT chk_bet_odds_consistency
    CHECK (
              odds_consistency IN (
              'MATCH',
              'MISMATCH',
              'NOT_CHECKABLE'
                                  )
    ),

    CONSTRAINT chk_bet_settlement_status
    CHECK (
              settlement_status IN (
              'PENDING',
              'W',
              'L',
              'V'
                                   )
    ),

    CONSTRAINT chk_bet_settlement_source
    CHECK (
              settlement_source IN (
              'NONE',
              'AUTO',
              'MANUAL'
                                   )
    )
    );

ALTER TABLE bets
    ALTER COLUMN calculated_odds DROP NOT NULL;

ALTER TABLE bets
    ADD COLUMN IF NOT EXISTS odds_consistency VARCHAR(32)
    NOT NULL DEFAULT 'NOT_CHECKABLE';

ALTER TABLE bets
DROP CONSTRAINT IF EXISTS chk_bet_odds_source;

ALTER TABLE bets
    ADD CONSTRAINT chk_bet_odds_source
        CHECK (
            odds_source IN (
                            'SINGLE_LEG',
                            'TITLE',
                            'CALCULATED',
                            'NONE'
                )
            );

ALTER TABLE bets
DROP CONSTRAINT IF EXISTS chk_bet_odds_consistency;

ALTER TABLE bets
    ADD CONSTRAINT chk_bet_odds_consistency
        CHECK (
            odds_consistency IN (
                                 'MATCH',
                                 'MISMATCH',
                                 'NOT_CHECKABLE'
                )
            );

CREATE INDEX IF NOT EXISTS idx_bets_post_active
    ON bets(wp_post_id, active);

CREATE INDEX IF NOT EXISTS idx_bets_active_pending
    ON bets(active, settlement_status);


CREATE TABLE IF NOT EXISTS bet_legs (
                                        id BIGSERIAL PRIMARY KEY,

                                        bet_id BIGINT NOT NULL
                                        REFERENCES bets(id)
    ON DELETE CASCADE,

    ordinal INTEGER NOT NULL,
    source_fingerprint CHAR(64) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,

    operator TEXT,
    tip_title TEXT NOT NULL,
    tip_odds NUMERIC(12, 4),

    event_external_id TEXT,
    event_home TEXT,
    event_away TEXT,
    event_competition TEXT,
    event_start_at TIMESTAMPTZ,
    event_start_raw TEXT,

    source_attributes JSONB NOT NULL DEFAULT '{}'::jsonb,
    event_attributes JSONB NOT NULL DEFAULT '{}'::jsonb,

    settlement_status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    settlement_source VARCHAR(16) NOT NULL DEFAULT 'NONE',

    settled_at TIMESTAMPTZ,
    manual_note TEXT,

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_bet_leg_source
    UNIQUE (
               bet_id,
               source_fingerprint
           ),

    CONSTRAINT chk_leg_settlement_status
    CHECK (
              settlement_status IN (
              'PENDING',
              'W',
              'L',
              'V'
                                   )
    ),

    CONSTRAINT chk_leg_settlement_source
    CHECK (
              settlement_source IN (
              'NONE',
              'AUTO',
              'MANUAL'
                                   )
    )
    );

ALTER TABLE bet_legs
    ALTER COLUMN tip_odds DROP NOT NULL;

CREATE INDEX IF NOT EXISTS idx_bet_legs_bet_active
    ON bet_legs(bet_id, active);

CREATE INDEX IF NOT EXISTS idx_bet_legs_event_external_id
    ON bet_legs(event_external_id);

CREATE INDEX IF NOT EXISTS idx_bet_legs_active_pending
    ON bet_legs(active, settlement_status);


CREATE TABLE IF NOT EXISTS import_runs (
                                           id BIGSERIAL PRIMARY KEY,

                                           wp_author_id BIGINT NOT NULL,

                                           date_from TIMESTAMPTZ NOT NULL,
                                           date_to TIMESTAMPTZ NOT NULL,

                                           started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    finished_at TIMESTAMPTZ,

    status VARCHAR(16) NOT NULL DEFAULT 'RUNNING',

    pages_fetched INTEGER NOT NULL DEFAULT 0,
    posts_seen INTEGER NOT NULL DEFAULT 0,
    posts_saved INTEGER NOT NULL DEFAULT 0,
    bets_saved INTEGER NOT NULL DEFAULT 0,
    legs_saved INTEGER NOT NULL DEFAULT 0,

    error_message TEXT,

    CONSTRAINT chk_import_run_status
    CHECK (
              status IN (
              'RUNNING',
              'SUCCESS',
              'FAILED'
                        )
    )
    );

ALTER TABLE import_runs
    ADD COLUMN IF NOT EXISTS bets_saved INTEGER NOT NULL DEFAULT 0;

ALTER TABLE import_runs
    ADD COLUMN IF NOT EXISTS legs_saved INTEGER NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS api_football_fixture_player_stat_fetches (
    fixture_id BIGINT PRIMARY KEY REFERENCES api_football_fixtures(fixture_id) ON DELETE CASCADE,
    status VARCHAR(32) NOT NULL,
    source VARCHAR(32) NOT NULL,
    http_status INTEGER,
    returned_player_count INTEGER NOT NULL DEFAULT 0,
    error_message TEXT,
    raw_json JSONB,
    fetched_at TIMESTAMPTZ NOT NULL,
    parser_version INTEGER NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK(status IN('COMPLETE','UNSUPPORTED_OR_EMPTY','FETCH_FAILED','API_ERROR','PARSE_ERROR'))
);

CREATE TABLE IF NOT EXISTS api_football_fixture_player_statistics (
    fixture_id BIGINT NOT NULL REFERENCES api_football_fixtures(fixture_id) ON DELETE CASCADE,
    team_id BIGINT NOT NULL,
    team_name TEXT,
    player_id BIGINT NOT NULL,
    player_name TEXT,
    minutes INTEGER,
    position TEXT,
    rating TEXT,
    captain BOOLEAN,
    substitute BOOLEAN,
    offsides INTEGER,
    shots_total INTEGER,
    shots_on_target INTEGER,
    goals INTEGER,
    assists INTEGER,
    saves INTEGER,
    passes_total INTEGER,
    passes_key INTEGER,
    passes_accuracy TEXT,
    tackles INTEGER,
    blocks INTEGER,
    interceptions INTEGER,
    duels_total INTEGER,
    duels_won INTEGER,
    dribbles_attempts INTEGER,
    dribbles_success INTEGER,
    dribbles_past INTEGER,
    fouls_drawn INTEGER,
    fouls_committed INTEGER,
    yellow_cards INTEGER,
    red_cards INTEGER,
    penalties JSONB,
    raw_statistics JSONB NOT NULL,
    source VARCHAR(32) NOT NULL,
    fetched_at TIMESTAMPTZ NOT NULL,
    parser_version INTEGER NOT NULL,
    PRIMARY KEY(fixture_id,team_id,player_id)
);
CREATE INDEX IF NOT EXISTS idx_fixture_player_stats_player ON api_football_fixture_player_statistics(player_id);

CREATE TABLE IF NOT EXISTS api_football_competition_progression_fetches(
 league_id BIGINT NOT NULL,season INTEGER NOT NULL,status VARCHAR(32) NOT NULL,raw_json JSONB,error_message TEXT,fetched_at TIMESTAMPTZ NOT NULL,PRIMARY KEY(league_id,season)
);
CREATE TABLE IF NOT EXISTS api_football_competition_progression_fixtures(
 league_id BIGINT NOT NULL,season INTEGER NOT NULL,fixture_id BIGINT NOT NULL,round TEXT,status_short TEXT,kickoff TEXT,home_team_id BIGINT,home_team_name TEXT,home_winner BOOLEAN,away_team_id BIGINT,away_team_name TEXT,away_winner BOOLEAN,goals_home INTEGER,goals_away INTEGER,ft_home INTEGER,ft_away INTEGER,et_home INTEGER,et_away INTEGER,pen_home INTEGER,pen_away INTEGER,PRIMARY KEY(league_id,season,fixture_id)
);

CREATE TABLE IF NOT EXISTS football_secondary_fixture_mappings(
    fixture_id BIGINT NOT NULL REFERENCES api_football_fixtures(fixture_id) ON DELETE CASCADE,
    provider VARCHAR(32) NOT NULL,
    provider_event_id VARCHAR(64),
    status VARCHAR(32) NOT NULL,
    evidence TEXT,
    mapped_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY(fixture_id,provider),
    CHECK(status IN('MAPPED','UNRESOLVED','AMBIGUOUS','FETCH_FAILED','API_ERROR','PARSE_ERROR'))
);

CREATE TABLE IF NOT EXISTS football_period_stat_fetches(
    fixture_id BIGINT NOT NULL REFERENCES api_football_fixtures(fixture_id) ON DELETE CASCADE,
    provider VARCHAR(32) NOT NULL,
    provider_event_id VARCHAR(64),
    status VARCHAR(32) NOT NULL,
    error_message TEXT,
    raw_json JSONB,
    fetched_at TIMESTAMPTZ NOT NULL,
    parser_version INTEGER NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY(fixture_id,provider),
    CHECK(status IN('COMPLETE','PARTIAL','UNAVAILABLE','UNSUPPORTED','FETCH_FAILED','API_ERROR','PARSE_ERROR'))
);

CREATE TABLE IF NOT EXISTS football_period_statistics(
    fixture_id BIGINT NOT NULL REFERENCES api_football_fixtures(fixture_id) ON DELETE CASCADE,
    provider VARCHAR(32) NOT NULL,
    provider_event_id VARCHAR(64) NOT NULL,
    period VARCHAR(24) NOT NULL,
    statistic_type VARCHAR(40) NOT NULL,
    team_side VARCHAR(8) NOT NULL,
    value NUMERIC,
    value_status VARCHAR(16) NOT NULL,
    raw_key TEXT,
    ingested_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY(fixture_id,provider,period,statistic_type,team_side),
    CHECK(period IN('FIRST_HALF','SECOND_HALF','FULL_MATCH')),
    CHECK(team_side IN('HOME','AWAY')),
    CHECK(value_status IN('KNOWN','ABSENT','INVALID')),
    CHECK((value_status='KNOWN' AND value IS NOT NULL AND value>=0) OR (value_status<>'KNOWN' AND value IS NULL))
);
