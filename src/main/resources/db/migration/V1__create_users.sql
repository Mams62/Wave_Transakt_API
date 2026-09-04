CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE users (
                       id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),

                       first_name VARCHAR(100) NOT NULL,
                       last_name VARCHAR(100) NOT NULL,

                       email VARCHAR(150) UNIQUE NOT NULL,
                       phone VARCHAR(20) UNIQUE NOT NULL,

                       password VARCHAR(255) NOT NULL,

                       bvn VARCHAR(20),
                       nin VARCHAR(20),

                       account_status VARCHAR(30) NOT NULL DEFAULT 'PENDING',

                       email_verified BOOLEAN DEFAULT FALSE,
                       phone_verified BOOLEAN DEFAULT FALSE,

                       created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                       updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);