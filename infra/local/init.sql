-- Synthetic local credentials. Applications own separate databases.
CREATE USER commerce WITH PASSWORD 'commerce-local';
CREATE DATABASE commerce OWNER commerce;
CREATE USER ai WITH PASSWORD 'ai-local';
CREATE DATABASE ai OWNER ai;
CREATE USER keycloak WITH PASSWORD 'keycloak-local';
CREATE DATABASE keycloak OWNER keycloak;
REVOKE CONNECT ON DATABASE commerce FROM PUBLIC;
GRANT CONNECT ON DATABASE commerce TO commerce;
REVOKE CONNECT ON DATABASE ai FROM PUBLIC;
GRANT CONNECT ON DATABASE ai TO ai;
REVOKE CONNECT ON DATABASE keycloak FROM PUBLIC;
GRANT CONNECT ON DATABASE keycloak TO keycloak;
\connect ai
CREATE EXTENSION vector;
