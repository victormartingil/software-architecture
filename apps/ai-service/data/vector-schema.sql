CREATE EXTENSION IF NOT EXISTS vector;
CREATE TABLE IF NOT EXISTS ai_documents (
 id text PRIMARY KEY,
 tenant text NOT NULL,
 text text NOT NULL,
 dimensions integer NOT NULL CHECK (dimensions BETWEEN 1 AND 2000),
 embedding vector NOT NULL,
 CHECK (vector_dims(embedding) = dimensions)
);
CREATE INDEX IF NOT EXISTS ai_documents_tenant ON ai_documents(tenant, dimensions);
-- Small synthetic corpus: exact scan is intentional. Add a dimension-specific ANN index
-- only after measuring recall and verifying authorization does not lose candidates.
