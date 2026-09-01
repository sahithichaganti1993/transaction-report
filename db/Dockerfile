# MySQL 8 with the schema, indexes and data baked in.
#
# The scripts used to be bind-mounted into /docker-entrypoint-initdb.d. That
# works only if the host directory is actually shared with the Docker VM - and
# when it isn't (Docker Desktop file-sharing not covering the clone path, a
# Colima/Lima profile without that mount, SELinux on Linux) the mount silently
# resolves to an EMPTY directory: MySQL starts up fine, runs nothing, and the
# app then crash-loops against an empty schema.
#
# Baking them into the image removes that whole class of failure - the data is
# part of the image, so `docker compose up --build` behaves the same everywhere.
FROM mysql:8.0

# Filename order matters: database, then the supplied table+data, then indexes.
COPY 00_database.sql 01_account_tran.sql 02_indexes.sql /docker-entrypoint-initdb.d/
