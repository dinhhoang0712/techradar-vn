-- Denormalized snapshot: Company data lives in Neo4j, not Postgres, so no FK is possible.
ALTER TABLE post ADD COLUMN IF NOT EXISTS tagged_company_id TEXT;
ALTER TABLE post ADD COLUMN IF NOT EXISTS tagged_company_name TEXT;
ALTER TABLE post ADD COLUMN IF NOT EXISTS tagged_company_location TEXT;
