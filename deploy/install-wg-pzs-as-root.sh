#!/bin/bash
# Na noble, jako root:
#   sudo bash /home/cursor-deploy/pzs-budzetowanie.org/install-wg-pzs-as-root.sh
set -euo pipefail

STAGE=/home/cursor-deploy/pzs-budzetowanie.org

if [[ "$(id -u)" -ne 0 ]]; then
  echo "Uruchom jako root." >&2
  exit 1
fi

for f in "$STAGE/wg-pzs.conf" "$STAGE/pzs.budzetowanie.org.conf" "$STAGE/pzs.budzetowanie.org-le-ssl.conf"; do
  if [[ ! -f "$f" ]]; then
    echo "Brak pliku $f" >&2
    exit 1
  fi
done

if ip link show wg0 >/dev/null 2>&1; then
  echo "wg0 bez zmian: $(ip -4 -br addr show wg0)"
fi

install -o root -g root -m 600 "$STAGE/wg-pzs.conf" /etc/wireguard/wg-pzs.conf
systemctl enable --now wg-quick@wg-pzs

if command -v ufw >/dev/null && ufw status | grep -q "Status: active"; then
  ufw allow 51821/udp comment "pzs-wireguard"
fi

install -o root -g root -m 644 "$STAGE/pzs.budzetowanie.org.conf" /etc/apache2/sites-available/pzs.budzetowanie.org.conf
install -o root -g root -m 644 "$STAGE/pzs.budzetowanie.org-le-ssl.conf" /etc/apache2/sites-available/pzs.budzetowanie.org-le-ssl.conf
apache2ctl configtest
systemctl reload apache2

echo "--- wg-pzs ---"
wg show wg-pzs
echo "--- sluchanie ---"
ss -lun | grep 51821 || true
echo "Tunel wlaczony. Panel tylko z 10.10.30.10, 10.10.30.11, 10.10.30.12 i 127.0.0.1."
echo "Konfiguracja Katarzyny: $STAGE/pzs-katarzynad.conf"
echo "Konfiguracja Joanny: $STAGE/pzs-joannal.conf"
