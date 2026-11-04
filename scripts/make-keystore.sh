#!/usr/bin/env bash
# One-time helper: creates the release keystore and prints the four values to store as
# GitHub repository secrets. Run it on your own machine, NOT in CI, and back the .jks up.
#
#   ./scripts/make-keystore.sh
#
# Then add these under  Settings > Secrets and variables > Actions > New repository secret:
#   KEYSTORE_BASE64, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD
set -euo pipefail

ALIAS="${KEY_ALIAS:-clock}"
OUT="${1:-personal-clock-release.jks}"
if [ -e "$OUT" ]; then echo "Refusing to overwrite $OUT"; exit 1; fi

read -r -s -p "Choose a keystore password (>= 6 chars): " PASS; echo
keytool -genkeypair -v -keystore "$OUT" -alias "$ALIAS" -keyalg RSA -keysize 4096 \
  -validity 36500 -storepass "$PASS" -keypass "$PASS" \
  -dname "CN=Personal Clock, O=Personal, C=TH"

echo
echo "Keystore written to: $OUT   (BACK THIS FILE UP - losing it means uninstalling the app)"
echo
echo "KEYSTORE_PASSWORD = (the password you typed)"
echo "KEY_PASSWORD      = (same password)"
echo "KEY_ALIAS         = $ALIAS"
echo "KEYSTORE_BASE64   = (copy the single line below)"
base64 -w0 "$OUT" 2>/dev/null || base64 "$OUT" | tr -d '\n'
echo
