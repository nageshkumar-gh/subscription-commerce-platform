#!/usr/bin/env bash
# Builds and starts every stack one at a time, in dependency order, from the repository root.
# Sequential on purpose: parallel builds exhaust memory on small hosts and hang.
#
#   ./scripts/deploy-all.sh [env-file]          (default env file: .env.datadog)
#   nohup ./scripts/deploy-all.sh > ~/deploy.log 2>&1 &   (survives a dropped SSH session)
#
# Each stack's build output goes to ~/deploy-<stack>.log; this script prints one line per stack.
set -u
cd "$(dirname "$0")/.." || exit 1
ENV_FILE="${1:-.env.datadog}"
[ -f "$ENV_FILE" ] || { echo "Missing $ENV_FILE (copy .env.datadog.example)"; exit 1; }

docker network inspect subscription-platform >/dev/null 2>&1 || docker network create subscription-platform >/dev/null

STACKS="customer-service product-service order-service payment-service network-service fulfillment-service
billing-service orchestration-service tracking-service invoice-service web-ui admin-ui"

failed=0
for stack in $STACKS; do
  start=$(date +%s)
  if docker compose --progress=plain --env-file "$ENV_FILE" -f "$stack/compose.yaml" up -d --build > ~/"deploy-$stack.log" 2>&1; then
    echo "ok   $stack ($(( $(date +%s) - start ))s)"
  else
    echo "FAIL $stack ($(( $(date +%s) - start ))s) - see ~/deploy-$stack.log"
    failed=1
  fi
done
echo DONE
exit $failed
