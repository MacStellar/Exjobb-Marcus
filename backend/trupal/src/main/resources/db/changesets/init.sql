--liquibase formatted sql

--changeset marcusschon:interaction.1
--precondition-sql-check expectedResult:0 SELECT COUNT(to_regclass('public.interactiontest'));
CREATE TABLE interactiontest
(
    id          bigserial NOT NULL PRIMARY KEY,
    created_at  TIMESTAMP NOT NULL,
    updated_at  TIMESTAMP NOT NULL,
    interaction jsonb     NOT NULL
);


--changeset marcusschon:interaction.2
--precondition-sql-check expectedResult:0 SELECT COUNT(to_regclass('public.messages'));
CREATE TABLE messages
(
    id   VARCHAR(60) DEFAULT uuid_generate_v4() PRIMARY KEY,
    text VARCHAR NOT NULL
);

--changeset marcusschon:interaction.3
--precondition-sql-check expectedResult:0 SELECT COUNT(to_regclass('public.session'));
CREATE TABLE session
(
    id      VARCHAR(60) DEFAULT uuid_generate_v4() PRIMARY KEY,
    created TIMESTAMP NOT NULL
);

-- -- Add a constraint to limit the number of rows (users) per session_p2p value (om jag vill ha detta):
-- ALTER TABLE combinedusersinfo
--     ADD CONSTRAINT max_rows_per_session CHECK (
--         (SELECT COUNT(*)
--          FROM combinedusersinfo c
--          WHERE c.session_p2p = combinedusersinfo.session_p2p) <= 5
--         );

--changeset marcusschon:interaction.4
--precondition-sql-check expectedResult:0 SELECT COUNT(to_regclass('public.userSession'));
CREATE TABLE user_session
(
    id         VARCHAR(60) DEFAULT uuid_generate_v4() PRIMARY KEY,
    session_id VARCHAR   NOT NULL REFERENCES session (id) ON DELETE CASCADE,
    cookie_id  VARCHAR   NOT NULL,
    user_id    VARCHAR   NOT NULL,
    user_info  VARCHAR   NOT NULL,
    created    TIMESTAMP NOT NULL
);

--changeset marcusschon:interaction.5
--precondition-sql-check expectedResult:0 SELECT COUNT(to_regclass('public.userTokens'));
CREATE TABLE user_token
(
    id            VARCHAR(60) DEFAULT uuid_generate_v4() PRIMARY KEY,
    cookie        VARCHAR   NOT NULL UNIQUE,
    user_id       VARCHAR   NOT NULL,
    refresh_token VARCHAR   NOT NULL,
    created       TIMESTAMP NOT NULL
);