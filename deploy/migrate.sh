#!/usr/bin/env bash
# Existing Docker installations: back up first, then add missing schema fields.
set -euo pipefail
umask 077
cd "$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
if docker compose version >/dev/null 2>&1; then DC=(docker compose); else DC=(docker-compose); fi

db="$("${DC[@]}" exec -T mysql sh -c 'printf %s "$MYSQL_DATABASE"')"
[[ "$db" =~ ^[a-zA-Z0-9_]+$ ]] || { echo 'Invalid database name'; exit 1; }
mysql_query() {
  "${DC[@]}" exec -T mysql sh -c 'export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec mysql -uroot --default-character-set=utf8mb4 --batch --skip-column-names "$1"' sh "$db"
}
query() { printf '%s\n' "$1" | mysql_query; }
tables="$(query "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('article','article_category','chat_session','chat_message','emotion_record');")"
[[ "$tables" == 5 ]] || { echo 'MindMan tables are missing; migration stopped'; exit 1; }

mkdir -p deploy/backups
backup="deploy/backups/pre-update-$(date +%Y%m%d-%H%M%S).sql"
"${DC[@]}" exec -T mysql sh -c 'export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec mysqldump -uroot --single-transaction --no-tablespaces --default-character-set=utf8mb4 "$1"' sh "$db" > "$backup"
[[ -s "$backup" ]] || { echo 'Database backup is empty; migration stopped'; exit 1; }
echo "Database backup: $backup"

add_column() {
  local table="$1" column="$2" definition="$3" exists
  exists="$(query "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='$table' AND column_name='$column';")"
  if [[ "$exists" == 0 ]]; then query "ALTER TABLE $table ADD COLUMN $column $definition;" >/dev/null; fi
}
add_index() {
  local table="$1" name="$2" columns="$3" exists
  exists="$(query "SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='$table' AND index_name='$name';")"
  if [[ "$exists" == 0 ]]; then query "ALTER TABLE $table ADD INDEX $name ($columns);" >/dev/null; fi
}
add_column article source_type "VARCHAR(20) NOT NULL DEFAULT 'local'"
add_column article source_url 'VARCHAR(1000) DEFAULT NULL'
add_column article source_name 'VARCHAR(128) DEFAULT NULL'
add_column article emotion_tags 'VARCHAR(512) DEFAULT NULL'
add_index article idx_article_category_status_publish 'category_id,status,deleted,publish_time,id'
add_index article idx_article_status_publish 'status,deleted,publish_time,id'
add_index article idx_article_title 'title'
add_index article idx_article_source_url 'source_url(512)'
add_index article_category idx_category_status_sort 'status,sort_order,id'
add_index chat_session idx_session_user_updated 'user_id,updated_at'
add_index chat_message idx_message_session_created 'session_id,created_at,id'
add_index chat_message idx_message_user_role_created 'user_id,role,created_at'

for sql in code/backend/src/main/resources/db/migration/*.sql; do
  echo "Apply: $sql"
  mysql_query < "$sql" >/dev/null
done
echo 'MindMan schema migration complete'
