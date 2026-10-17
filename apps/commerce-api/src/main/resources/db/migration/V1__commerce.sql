CREATE TABLE inventory(tenant text NOT NULL, sku text NOT NULL, stock integer NOT NULL CHECK(stock>=0), price integer NOT NULL CHECK(price>0), version bigint NOT NULL DEFAULT 0, PRIMARY KEY(tenant,sku));
INSERT INTO inventory(tenant,sku,stock,price) VALUES ('demo','BOOK-1',10,2500),('other','BOOK-1',10,2500);
CREATE TABLE orders(id uuid PRIMARY KEY,tenant text NOT NULL,actor text NOT NULL,sku text NOT NULL,quantity integer NOT NULL CHECK(quantity>0),total_cents integer NOT NULL,status text NOT NULL,version bigint NOT NULL DEFAULT 0);
CREATE TABLE return_proposals(id uuid PRIMARY KEY,tenant text NOT NULL,actor text NOT NULL,order_id uuid NOT NULL UNIQUE REFERENCES orders(id),reason text NOT NULL,status text NOT NULL,version bigint NOT NULL DEFAULT 0,approved_until timestamptz,approved_by text);
CREATE TABLE idempotency(scope text PRIMARY KEY,request_hash text NOT NULL,result jsonb NOT NULL);
CREATE TABLE outbox(event_id uuid PRIMARY KEY,payload text NOT NULL,aggregate_id uuid NOT NULL,published_at timestamptz,attempts integer NOT NULL DEFAULT 0,trace_id text,parent_span_id text);
CREATE TABLE inbox(event_id uuid PRIMARY KEY,processed_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE audit_orders(event_id uuid PRIMARY KEY REFERENCES inbox(event_id),order_id uuid NOT NULL,tenant text NOT NULL);
CREATE INDEX outbox_pending ON outbox(event_id) WHERE published_at IS NULL;
