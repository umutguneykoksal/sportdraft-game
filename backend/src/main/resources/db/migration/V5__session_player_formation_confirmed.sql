ALTER TABLE session_player
    ADD COLUMN IF NOT EXISTS formation_confirmed BOOLEAN NOT NULL DEFAULT false;
