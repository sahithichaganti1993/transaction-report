-- ---------------------------------------------------------------------------
-- Six fixture rows, deliberately shaped like the real data.
--
-- id acct datetime           type       amount_real  balance_real  loyalty
--  1  100  2025-08-01 10:00  GAME_BET        -10.00        100.00     1000
--  2  100  2025-08-01 10:05  GAME_WIN         20.00        120.00     1000
--  3  100  2025-08-15 12:00  GAME_BET         -5.00        115.00     1000
--  4  200  2025-08-20 09:30  GAME_WIN          8.00         58.00      500
--  5  200  2025-08-25 18:45  ROLLBACK          7.00         65.00      500
--  6  100  2025-09-01 09:00  GAME_BET         -3.00         30.00     1000   (outside August)
--
-- Notes on why these values:
--   - wagers are negative, matching the real data's sign convention, so the
--     summary's magnitude handling is actually exercised
--   - row 3 leaves AMOUNT_REAL null on purpose, to prove COALESCE is doing its
--     job: without it that row's amount would be null and poison the sum
--   - the *_RAW_LOYALTY values are large relative to the money columns, as in
--     the real data
--   - row 6 sits outside the August range the tests query, so date filtering
--     has something to exclude
-- ---------------------------------------------------------------------------

INSERT INTO account_tran
  (ID, ACCOUNT_ID, `DATETIME`, LOGDATETIME, TRAN_TYPE, AMOUNT_REAL, BALANCE_REAL,
   PLATFORM_TRAN_ID, GAME_TRAN_ID, GAME_ID, PLATFORM_ID, ROLLED_BACK,
   AMOUNT_RELEASED_BONUS, AMOUNT_PLAYABLE_BONUS, BALANCE_RELEASED_BONUS,
   BALANCE_PLAYABLE_BONUS, AMOUNT_UNDERFLOW, AMOUNT_RAW_LOYALTY,
   BALANCE_RAW_LOYALTY, AMOUNT_FREE_BET, GAME_INSTANCE_ID, CHANNEL)
VALUES
  (1, 100, '2025-08-01 10:00:00', '2025-08-01 10:00:00', 'GAME_BET', -10.00, 100.00,
   '500010000001', '110000001', 'slots-aurora', 59, 0,
   0.00, 0.00, 0.00, 0.00, 0.00, 0, 1000, 0.00, 3001, 'DESKTOP'),

  (2, 100, '2025-08-01 10:05:00', '2025-08-01 10:05:00', 'GAME_WIN', 20.00, 120.00,
   '500010000002', '110000002', 'slots-aurora', 59, 0,
   0.00, 0.00, 0.00, 0.00, 0.00, 0, 1000, 0.00, 3001, 'DESKTOP'),

  -- AMOUNT_REAL deliberately NULL: proves the COALESCE in the sum expression.
  (3, 100, '2025-08-15 12:00:00', '2025-08-15 12:00:00', 'GAME_BET', NULL, 115.00,
   '500010000003', '110000003', 'slots-borealis', 59, 0,
   0.00, 5.00, 0.00, 0.00, 0.00, 0, 1000, 0.00, 3002, 'MOBILE'),

  (4, 200, '2025-08-20 09:30:00', '2025-08-20 09:30:00', 'GAME_WIN', 8.00, 58.00,
   '500020000004', '220000004', 'table-cygnus', 61, 0,
   0.00, 0.00, 0.00, 0.00, 0.00, 0, 500, 0.00, 3003, 'MOBILE'),

  (5, 200, '2025-08-25 18:45:00', '2025-08-25 18:45:00', 'ROLLBACK', 7.00, 65.00,
   '500020000005', '220000005', 'table-cygnus', 61, 1,
   0.00, 0.00, 0.00, 0.00, 0.00, 0, 500, 0.00, 3003, 'MOBILE'),

  -- September: outside the August range the tests query.
  (6, 100, '2025-09-01 09:00:00', '2025-09-01 09:00:00', 'GAME_BET', -3.00, 30.00,
   '500010000006', '110000006', 'slots-aurora', 59, 0,
   0.00, 0.00, 0.00, 0.00, 0.00, 0, 1000, 0.00, 3004, 'DESKTOP');
