ALTER TABLE users
    ADD COLUMN auth_version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE verification_codes
    ADD COLUMN auth_version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE face_login_challenges
    ADD COLUMN auth_version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE users
    ADD CONSTRAINT chk_users_auth_version_nonnegative CHECK (auth_version >= 0);

ALTER TABLE verification_codes
    ADD CONSTRAINT chk_verification_codes_auth_version_nonnegative CHECK (auth_version >= 0);

ALTER TABLE face_login_challenges
    ADD CONSTRAINT chk_face_login_challenges_auth_version_nonnegative CHECK (auth_version >= 0);
