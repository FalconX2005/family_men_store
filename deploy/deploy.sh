#!/usr/bin/env bash
# =====================================================================
#  Kompyuterdan serverga yuborish (Git Bash / Linux / macOS):
#
#  Birinchi marta:
#    SSH_KEY=~/oracle.key SERVER=1.2.3.4 ADMIN_PASSWORD='...' BOT_TOKEN=... CHAT_ID=... \
#        DOMAIN=familystore.duckdns.org bash deploy/deploy.sh
#
#  Keyingi yangilashlar (sozlamalar serverda saqlanib qoladi):
#    SSH_KEY=~/oracle.key SERVER=1.2.3.4 bash deploy/deploy.sh
# =====================================================================
set -euo pipefail
cd "$(dirname "$0")/.."

: "${SERVER:?SERVER=ip manzil kerak}"
: "${SSH_KEY:?SSH_KEY=kalit faylining manzili kerak}"
SSH_USER=${SSH_USER:-ubuntu}
SSH="ssh -i $SSH_KEY -o StrictHostKeyChecking=accept-new $SSH_USER@$SERVER"

echo "==> Build"
mvn -q -B package -DskipTests
JAR=$(ls target/family-mens-store-*.jar | grep -v original | head -1)

echo "==> Yuborish: $JAR -> $SERVER"
scp -i "$SSH_KEY" -o StrictHostKeyChecking=accept-new "$JAR" "$SSH_USER@$SERVER:/tmp/app.jar"
scp -i "$SSH_KEY" deploy/setup.sh "$SSH_USER@$SERVER:/tmp/setup.sh"

echo "==> O'rnatish"
# Maxfiy qiymatlar buyruq satrida emas, stdin orqali uzatiladi
{
  printf 'export ADMIN_PASSWORD=%q BOT_TOKEN=%q CHAT_ID=%q DOMAIN=%q EMAIL=%q\n' \
    "${ADMIN_PASSWORD:-}" "${BOT_TOKEN:-}" "${CHAT_ID:-}" "${DOMAIN:-}" "${EMAIL:-}"
  echo 'bash /tmp/setup.sh; rc=$?; rm -f /tmp/setup.sh; exit $rc'
} | $SSH "sudo bash -s"
