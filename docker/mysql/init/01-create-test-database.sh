#!/usr/bin/env bash

set -euo pipefail

test_database="${TEST_DB_NAME:-coffee_order_system_test}"

MYSQL_PWD="${MYSQL_ROOT_PASSWORD}" mysql --protocol=socket -uroot <<SQL
CREATE DATABASE IF NOT EXISTS \`${test_database}\`
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;
GRANT ALL PRIVILEGES ON \`${test_database}\`.* TO '${MYSQL_USER}'@'%';
FLUSH PRIVILEGES;
SQL
