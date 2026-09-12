ALTER TABLE users
    ADD COLUMN face_identity_enrolled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN face_identity_enrolled_at TIMESTAMP NULL;
