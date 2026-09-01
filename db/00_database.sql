-- ---------------------------------------------------------------------------
-- 00_database.sql - creates the database the application connects to.
--
-- The supplied script (01_account_tran.sql) contains only CREATE TABLE and the
-- data, so the database itself has to exist first. Run this before it.
-- ---------------------------------------------------------------------------

CREATE DATABASE IF NOT EXISTS gamedb
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE gamedb;

-- Makes a re-run of 01_account_tran.sql idempotent during local setup.
DROP TABLE IF EXISTS account_tran;
