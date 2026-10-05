#!/bin/bash
# Run on noble as root:
#   sudo bash /home/cursor-deploy/pzs-budzetowanie.org/install-pzs-as-root.sh
set -euo pipefail

STAGE=/home/cursor-deploy/pzs-budzetowanie.org
SITE=/var/www/pzs.budzetowanie.org
PROPS="$STAGE/application.properties"

if [[ "$(id -u)" -ne 0 ]]; then
  echo "Run as root." >&2
  exit 1
fi

for f in "$STAGE/budget-0.0.1-SNAPSHOT.jar" "$PROPS" "$STAGE/pzs-budget.service" \
         "$STAGE/pzs.budzetowanie.org.conf" "$STAGE/pzs.budzetowanie.org-le-ssl.conf"; do
  if [[ ! -f "$f" ]]; then
    echo "Missing $f" >&2
    exit 1
  fi
done

DB_PASS="$(grep '^spring.datasource.password=' "$PROPS" | cut -d= -f2-)"
if [[ -z "$DB_PASS" ]]; then
  echo "No datasource password in $PROPS" >&2
  exit 1
fi

mariadb <<SQL
CREATE DATABASE IF NOT EXISTS budget_pzs CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'budget_pzs'@'localhost' IDENTIFIED BY '${DB_PASS}';
ALTER USER 'budget_pzs'@'localhost' IDENTIFIED BY '${DB_PASS}';
GRANT ALL PRIVILEGES ON budget_pzs.* TO 'budget_pzs'@'localhost';
FLUSH PRIVILEGES;
SQL

install -d -o tomaszt -g tomaszt -m 755 "$SITE" "$SITE/backups" \
  "$SITE/html/.well-known/acme-challenge"

install -o tomaszt -g tomaszt -m 640 "$PROPS" "$SITE/application.properties"
install -o tomaszt -g tomaszt -m 644 "$STAGE/budget-0.0.1-SNAPSHOT.jar" "$SITE/budget-0.0.1-SNAPSHOT.jar"
touch "$SITE/app.log"
chown tomaszt:tomaszt "$SITE/app.log"
chmod 644 "$SITE/app.log"

install -o root -g root -m 644 "$STAGE/pzs-budget.service" /etc/systemd/system/pzs-budget.service
systemctl daemon-reload
systemctl enable --now pzs-budget.service

install -o root -g root -m 644 "$STAGE/pzs.budzetowanie.org.conf" /etc/apache2/sites-available/pzs.budzetowanie.org.conf
a2ensite pzs.budzetowanie.org.conf
apache2ctl configtest
systemctl reload apache2

if [[ ! -f /etc/letsencrypt/live/pzs.budzetowanie.org/fullchain.pem ]]; then
  certbot certonly --webroot -w "$SITE/html" -d pzs.budzetowanie.org \
    --non-interactive --keep-until-expiring --key-type ecdsa
fi

install -o root -g root -m 644 "$STAGE/pzs.budzetowanie.org-le-ssl.conf" \
  /etc/apache2/sites-available/pzs.budzetowanie.org-le-ssl.conf
a2ensite pzs.budzetowanie.org-le-ssl.conf
apache2ctl configtest
systemctl reload apache2

systemctl --no-pager --full status pzs-budget.service
echo "Installed. https://pzs.budzetowanie.org/"
