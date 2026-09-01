-- ---------------------------------------------------------------------------
-- 02_indexes.sql - indexes added on top of the ones shipped in the supplied
-- script, to support this report's access patterns.
--
-- The supplied table already has:
--   PRIMARY KEY        (ID)
--   IDX_ACCOUNT_TRAN_1 (ACCOUNT_ID, DATETIME)   -> account filter + range: covered
--   IDX_ACCOUNT_TRAN_2 (GAME_TRAN_ID, PLATFORM_ID)   -> game_tran_id filter: covered
--   IDX_ACCOUNT_TRAN_3 (PLATFORM_TRAN_ID, PLATFORM_ID) -> platform_tran_id filter: covered
--
-- What it does NOT have is anything that helps the two most common shapes of
-- this report: a bare date range, and a date range narrowed by GAME_ID or
-- TRAN_TYPE. Every query the app issues is bounded by DATETIME, so DATETIME is
-- either the leading column or the trailing column of a (filter, DATETIME)
-- pair - that lets InnoDB satisfy "filter + range + ORDER BY DATETIME" from a
-- single index instead of falling back to a full scan plus a filesort.
-- ---------------------------------------------------------------------------

USE gamedb;

-- Date-range-only reports, and the default ORDER BY DATETIME.
CREATE INDEX ix_account_tran_datetime          ON account_tran (`DATETIME`);

-- Game filter narrowed by a date range.
CREATE INDEX ix_account_tran_game_datetime     ON account_tran (GAME_ID, `DATETIME`);

-- Transaction-type filter narrowed by a date range; also serves the
-- bet/win totals in the summary section.
CREATE INDEX ix_account_tran_trantype_datetime ON account_tran (TRAN_TYPE, `DATETIME`);
