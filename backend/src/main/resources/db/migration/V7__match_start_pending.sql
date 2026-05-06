ALTER TABLE game_session
    ADD COLUMN IF NOT EXISTS match_start_deadline_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS match_start_ready_user_one_id UUID,
    ADD COLUMN IF NOT EXISTS match_start_ready_user_two_id UUID;
