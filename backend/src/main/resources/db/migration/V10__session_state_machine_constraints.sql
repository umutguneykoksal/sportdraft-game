ALTER TABLE game_session
    ADD CONSTRAINT chk_game_session_status
    CHECK (
        status IN (
            'WAITING',
            'COUNTDOWN',
            'FORMATION_SELECT',
            'AUCTIONING',
            'MATCH_START_PENDING',
            'FINALIZING',
            'AWAITING_RESULT',
            'FINISHED'
        )
    );

ALTER TABLE game_session
    ADD CONSTRAINT chk_match_start_deadline_requires_pending
    CHECK (
        (status = 'MATCH_START_PENDING' AND match_start_deadline_at IS NOT NULL)
        OR (status <> 'MATCH_START_PENDING')
    );
