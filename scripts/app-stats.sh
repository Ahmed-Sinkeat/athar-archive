#!/bin/sh
# App usage counts from Analytics Engine (workers/search/index.ts, dataset athar_app).
#   scripts/app-stats.sh            events per day, last 14 days
#   scripts/app-stats.sh book       top 30 details for one event (book | search | download | audio)
# Needs CLOUDFLARE_ACCOUNT_ID and CLOUDFLARE_ANALYTICS_TOKEN (Account Analytics: Read) in
# ~/.config/athar/publish.env.
set -e
set -a; . "$HOME/.config/athar/publish.env"; set +a
: "${CLOUDFLARE_ANALYTICS_TOKEN:?add CLOUDFLARE_ANALYTICS_TOKEN to ~/.config/athar/publish.env}"
case "$1" in
  "") q="SELECT toDate(timestamp) AS day, blob1 AS event, SUM(_sample_interval) AS n FROM athar_app WHERE timestamp > NOW() - INTERVAL '14' DAY GROUP BY day, event ORDER BY day DESC, n DESC" ;;
  open|book|search|download|audio) q="SELECT blob2 AS detail, SUM(_sample_interval) AS n FROM athar_app WHERE blob1 = '$1' AND timestamp > NOW() - INTERVAL '7' DAY GROUP BY detail ORDER BY n DESC LIMIT 30" ;;
  *) echo "unknown event: $1" >&2; exit 2 ;;
esac
curl -sS "https://api.cloudflare.com/client/v4/accounts/$CLOUDFLARE_ACCOUNT_ID/analytics_engine/sql" \
  -H "Authorization: Bearer $CLOUDFLARE_ANALYTICS_TOKEN" --data "$q FORMAT PrettyCompact"
