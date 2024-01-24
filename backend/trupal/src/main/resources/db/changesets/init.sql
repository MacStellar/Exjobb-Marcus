--liquibase formatted sql

--changeset marcusschon:interaction.1
--precondition-sql-check expectedResult:0 SELECT COUNT(to_regclass('public.interactiontest'));
CREATE TABLE interactiontest (
                             id bigserial NOT NULL PRIMARY KEY,
                             created_at TIMESTAMP NOT NULL,
                             updated_at TIMESTAMP NOT NULL,
                             interaction jsonb NOT NULL
);


--changeset marcusschon:interaction.2
--precondition-sql-check expectedResult:0 SELECT COUNT(to_regclass('public.messages'));
CREATE TABLE messages (
    id      VARCHAR(60)  DEFAULT uuid_generate_v4() PRIMARY KEY,
    text    VARCHAR      NOT NULL
);