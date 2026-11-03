#!/usr/bin/env bash
# =====================================================================
#  Family store for men — Ubuntu serverga (Oracle Cloud Free Tier) o'rnatish
#
#  Serverda ishga tushiriladi (ilova fayli /tmp/app.jar da turgan bo'lishi kerak):
#    sudo BOT_TOKEN=... CHAT_ID=... ADMIN_PASSWORD=... DOMAIN=familystore.duckdns.org \
#         EMAIL=you@mail.com bash setup.sh
#
#  DOMAIN bo'sh bo'lsa — sayt http://SERVER_IP orqali ochiladi (HTTPS siz).
#  Skriptni qayta ishga tushirish xavfsiz: mavjud baza va rasmlar saqlanadi.
# =====================================================================
set -euo pipefail

APP_DIR=/opt/family-store
APP_USER=familystore
JAR_SRC=${JAR_SRC:-/tmp/app.jar}
DOMAIN=${DOMAIN:-}
EMAIL=${EMAIL:-}

log() { printf '\n\033[1;33m==> %s\033[0m\n' "$*"; }

[ "$(id -u)" -eq 0 ] || { echo "sudo bilan ishga tushiring"; exit 1; }
[ -f "$JAR_SRC" ] || { echo "Ilova fayli topilmadi: $JAR_SRC"; exit 1; }

log "1/7 Dasturlarni o'rnatish (Java 21, Nginx)"
export DEBIAN_FRONTEND=noninteractive
apt-get update -y -q
apt-get install -y -q openjdk-21-jre-headless nginx iptables-persistent >/dev/null

log "2/7 Firewall: 80 va 443 portlarini ochish"
for port in 80 443; do
  iptables -C INPUT -p tcp --dport "$port" -m state --state NEW -j ACCEPT 2>/dev/null \
    || iptables -I INPUT 1 -p tcp --dport "$port" -m state --state NEW -j ACCEPT
done
netfilter-persistent save >/dev/null

log "3/7 Foydalanuvchi va papkalar"
id "$APP_USER" >/dev/null 2>&1 || useradd --system --home "$APP_DIR" --shell /usr/sbin/nologin "$APP_USER"
mkdir -p "$APP_DIR"/{config,data,uploads,backups}
install -m 0644 "$JAR_SRC" "$APP_DIR/app.jar"

log "4/7 Maxfiy sozlamalar (config/application.properties)"
CFG="$APP_DIR/config/application.properties"
if [ ! -f "$CFG" ]; then
  : "${ADMIN_PASSWORD:?ADMIN_PASSWORD berilishi shart (kamida 10 belgi)}"
  [ "${#ADMIN_PASSWORD}" -ge 10 ] || { echo "ADMIN_PASSWORD kamida 10 belgi bo'lsin"; exit 1; }
  {
    echo "# Maxfiy sozlamalar — faqat shu serverda. GitHub'ga qo'ymang!"
    echo "store.admin-password=${ADMIN_PASSWORD}"
    echo "store.telegram-bot-token=${BOT_TOKEN:-}"
    echo "store.telegram-chat-id=${CHAT_ID:-}"
    echo "server.address=127.0.0.1"
    echo "# Nginx orqasida haqiqiy mijoz IP sini olish (login/spam himoyasi uchun shart)"
    echo "server.forward-headers-strategy=native"
    if [ -n "$DOMAIN" ]; then echo "server.servlet.session.cookie.secure=true"; fi
  } > "$CFG"
else
  echo "Mavjud sozlamalar saqlandi: $CFG"
fi
chown -R "$APP_USER:$APP_USER" "$APP_DIR"
chmod 600 "$CFG"

log "5/7 systemd servisi"
MEM_MB=$(awk '/MemTotal/ {print int($2/1024)}' /proc/meminfo)
XMX=$(( MEM_MB / 2 )); [ "$XMX" -gt 2048 ] && XMX=2048; [ "$XMX" -lt 256 ] && XMX=256
cat > /etc/systemd/system/family-store.service <<EOF
[Unit]
Description=Family store for men (Spring Boot)
After=network-online.target
Wants=network-online.target

[Service]
User=$APP_USER
WorkingDirectory=$APP_DIR
ExecStart=/usr/bin/java -Xmx${XMX}m -XX:+UseSerialGC -jar $APP_DIR/app.jar
SuccessExitStatus=143
Restart=always
RestartSec=5
# Qo'shimcha himoya
NoNewPrivileges=true
PrivateTmp=true
ProtectSystem=full
ProtectHome=true
ReadWritePaths=$APP_DIR

[Install]
WantedBy=multi-user.target
EOF
systemctl daemon-reload
systemctl enable family-store >/dev/null
systemctl restart family-store

log "6/7 Nginx"
SERVER_NAME=${DOMAIN:-_}
cat > /etc/nginx/sites-available/family-store <<EOF
server {
    listen 80 default_server;
    listen [::]:80 default_server;
    server_name $SERVER_NAME;

    client_max_body_size 20M;
    server_tokens off;

    gzip on;
    gzip_types text/css application/javascript image/svg+xml;

    location /uploads/ {
        proxy_pass http://127.0.0.1:8080;
        expires 30d;
        add_header Cache-Control "public";
    }

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
        proxy_read_timeout 120s;
    }
}
EOF
ln -sf /etc/nginx/sites-available/family-store /etc/nginx/sites-enabled/family-store
rm -f /etc/nginx/sites-enabled/default
nginx -t -q && systemctl reload nginx

if [ -n "$DOMAIN" ]; then
  log "HTTPS sertifikat (Let's Encrypt) — $DOMAIN"
  apt-get install -y -q certbot python3-certbot-nginx >/dev/null
  if [ -n "$EMAIL" ]; then CERT_MAIL=(-m "$EMAIL"); else CERT_MAIL=(--register-unsafely-without-email); fi
  certbot --nginx -n --agree-tos --redirect -d "$DOMAIN" "${CERT_MAIL[@]}"
fi

log "7/7 Kundalik zaxira nusxa (03:00, oxirgi 14 kun saqlanadi)"
cat > /etc/cron.daily/family-store-backup <<EOF
#!/bin/sh
tar czf $APP_DIR/backups/backup-\$(date +%F).tgz -C $APP_DIR data uploads config 2>/dev/null
find $APP_DIR/backups -name 'backup-*.tgz' -mtime +14 -delete
EOF
chmod +x /etc/cron.daily/family-store-backup

log "Ilova ishga tushishini kutish"
for i in $(seq 1 60); do
  curl -s -o /dev/null http://127.0.0.1:8080/ && break
  sleep 2
done
if curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:8080/ | grep -q 200; then
  IP=$(curl -s -4 https://ifconfig.me || echo SERVER_IP)
  echo
  echo "✅ Tayyor!  Sayt: ${DOMAIN:+https://$DOMAIN}${DOMAIN:-http://$IP}"
  echo "   Loglar:  sudo journalctl -u family-store -f"
else
  echo "⚠ Ilova javob bermayapti. Loglarni ko'ring: sudo journalctl -u family-store -n 100"
  exit 1
fi
