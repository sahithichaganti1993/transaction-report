-- ---------------------------------------------------------------------------
-- H2 fixture schema, mirroring the shape of the supplied account_tran table.
--
-- Only the columns the application maps or reads are present. Column names,
-- nullability and types follow the real schema so the tests exercise the same
-- conditions: AMOUNT_REAL / BALANCE_REAL nullable, the bonus columns NOT NULL,
-- the *_RAW_LOYALTY columns BIGINT rather than DECIMAL, and DATETIME quoted
-- because it is a reserved word.
-- ---------------------------------------------------------------------------

DROP TABLE IF EXISTS account_tran;

CREATE TABLE account_tran (
  ID                      BIGINT       NOT NULL AUTO_INCREMENT,
  ACCOUNT_ID              INT          NOT NULL,
  `DATETIME`              TIMESTAMP    NOT NULL,
  LOGDATETIME             TIMESTAMP    NOT NULL,
  TRAN_TYPE               VARCHAR(10)  NOT NULL,
  AMOUNT_REAL             DECIMAL(10,2)         DEFAULT NULL,
  BALANCE_REAL            DECIMAL(10,2)         DEFAULT NULL,
  PLATFORM_TRAN_ID        VARCHAR(100)          DEFAULT NULL,
  GAME_TRAN_ID            VARCHAR(100)          DEFAULT NULL,
  GAME_ID                 VARCHAR(100)          DEFAULT NULL,
  PLATFORM_ID             INT                   DEFAULT NULL,
  ROLLED_BACK             INT                   DEFAULT 0,
  AMOUNT_RELEASED_BONUS   DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  AMOUNT_PLAYABLE_BONUS   DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  BALANCE_RELEASED_BONUS  DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  BALANCE_PLAYABLE_BONUS  DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  AMOUNT_UNDERFLOW        DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  AMOUNT_RAW_LOYALTY      BIGINT        NOT NULL DEFAULT 0,
  BALANCE_RAW_LOYALTY     BIGINT        NOT NULL DEFAULT 0,
  AMOUNT_FREE_BET         DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  GAME_INSTANCE_ID        BIGINT                DEFAULT NULL,
  CHANNEL                 VARCHAR(25)           DEFAULT NULL,
  PRIMARY KEY (ID)
);
