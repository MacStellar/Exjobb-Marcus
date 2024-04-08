--liquibase formatted sql

--changeset marcusschon:interaction.1
--precondition-sql-check expectedResult:0 SELECT COUNT(to_regclass('public.session'));
CREATE TABLE session
(
    id      VARCHAR(60) DEFAULT gen_random_uuid() PRIMARY KEY,
    status  VARCHAR   NOT NULL,
    created TIMESTAMP NOT NULL
);

--changeset marcusschon:interaction.2
--precondition-sql-check expectedResult:0 SELECT COUNT(to_regclass('public.userSession'));
CREATE TABLE user_session
(
    id                VARCHAR(60) DEFAULT gen_random_uuid() PRIMARY KEY,
    session_id        VARCHAR   NOT NULL REFERENCES session (id) ON DELETE CASCADE,
    user_id           VARCHAR   NOT NULL,
    user_presentation VARCHAR   NOT NULL,
    created           TIMESTAMP NOT NULL
);

--changeset marcusschon:interaction.3
--precondition-sql-check expectedResult:0 SELECT COUNT(to_regclass('public.userTokens'));
CREATE TABLE user_token
(
    id            VARCHAR(60) DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id       VARCHAR   NOT NULL UNIQUE,
    refresh_token VARCHAR   NOT NULL,
    created       TIMESTAMP NOT NULL
);

