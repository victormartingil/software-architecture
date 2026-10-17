-- Legacy concatenated scopes cannot be decoded reliably. Stop rather than replay an effect.
-- Exclude concurrent legacy writers while checking the precondition and changing the schema.
LOCK TABLE idempotency IN ACCESS EXCLUSIVE MODE;
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM idempotency) THEN
    RAISE EXCEPTION 'Legacy idempotency records exist: migration blocked; see ADR 002';
  END IF;
END $$;

-- No default: the legacy writer must fail, even if it resumes after this migration.
ALTER TABLE idempotency ADD COLUMN scope_version smallint NOT NULL CHECK (scope_version = 2);
