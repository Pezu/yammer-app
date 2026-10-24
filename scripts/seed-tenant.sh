#!/usr/bin/env bash
# Load a demo client onto a running local stack, through the API — no SQL inserts into the
# running DB. Gives QA the TD-1..TD-4 fixture shapes from docs/epics/codebase-audit/test-plan.md
# §6.3: one client, one active location, a SERVICE point, a bar, three tables (one discounted),
# a four-item menu at the exact TD-4 prices, and the TD-2 set of users.
#
#   scripts/seed-tenant.sh 123 ["Client name"]
#   scripts/seed-tenant.sh develop
#
# The SUPER password is read from V2__Seed.sql; it is never printed or written anywhere else.
set -euo pipefail

if [[ $# -lt 1 ]]; then
    echo "usage: $0 <issue-number|develop> [\"Client name\"]" >&2
    exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(dirname "$SCRIPT_DIR")"
# shellcheck source=lib/ports.sh
source "$SCRIPT_DIR/lib/ports.sh"
compute_ports "$1"

API_URL="http://localhost:$API_PORT"
CLIENT_NAME="${2:-Demo Tenant $1}"
SLUG="$(echo "$CLIENT_NAME" | tr '[:upper:]' '[:lower:]' | tr -cs 'a-z0-9' '-' | sed 's/^-//;s/-$//')"
DEMO_PASSWORD="Demo_$(date +%s)!"

command -v jq >/dev/null || { echo "jq is required" >&2; exit 1; }

if ! curl -sf "$API_URL/" >/dev/null; then
    echo "api not reachable at $API_URL — run scripts/env-up.sh $1 first" >&2
    exit 1
fi

SEED_SQL="$REPO_ROOT/api/src/main/resources/db/migration/V2__Seed.sql"
SUPER_PASSWORD="$(grep -oE "md5\('[^']*'\)" "$SEED_SQL" | head -1 | sed -E "s/md5\('(.*)'\)/\1/")"

api() {
    local method="$1" path="$2" body="${3:-}"
    if [[ -n "$body" ]]; then
        curl -sf -X "$method" "$API_URL$path" \
            -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d "$body"
    else
        curl -sf -X "$method" "$API_URL$path" -H "Authorization: Bearer $TOKEN"
    fi
}

TOKEN="$(curl -sf -X POST "$API_URL/auth/login" -H 'Content-Type: application/json' \
    -d "{\"username\":\"office@yammer.ro\",\"password\":\"$SUPER_PASSWORD\"}" | jq -r .token)"

echo "Creating client '$CLIENT_NAME'..."
CLIENT_ID="$(api POST /clients "{\"name\":\"$CLIENT_NAME\"}" | jq -r .id)"
LOCATION_ID="$(api POST /locations "{\"name\":\"Main\",\"clientId\":\"$CLIENT_ID\",\"active\":true}" | jq -r .id)"

VAT_TYPES="$(api GET /vat-types)"
VAT_ID="$(echo "$VAT_TYPES" | jq -r '.[] | select(.value==11) | .id' | head -1)"

PAYMENT_TYPES="$(api GET /payment-types)"
PAYMENT_IDS="$(echo "$PAYMENT_TYPES" | jq -c '[.[] | select(.type=="CASH" or .type=="CARD" or .type=="PROTOCOL" or .type=="PO") | .id]')"

ORDER_POINT_TYPES="$(api GET /order-point-types)"
service_type_id() { echo "$ORDER_POINT_TYPES" | jq -r --arg t "$1" '.[] | select(.type==$t) | .id'; }
SERVICE_TYPE_ID="$(service_type_id SERVICE)"
BAR_TYPE_ID="$(service_type_id BAR)"
TABLE_TYPE_ID="$(service_type_id TABLE)"

echo "Creating products (TD-4 money catalogue)..."
declare -A PRODUCT_IDS
for row in "Aqua Panna:11.00" "Prosecco:22.00" "Pizza Margherita:42.00" "Espresso:9.99"; do
    name="${row%%:*}"
    PRODUCT_IDS["$name"]="$(api POST /products \
        "{\"locationId\":\"$LOCATION_ID\",\"name\":\"$name\",\"vatTypeId\":\"$VAT_ID\"}" | jq -r .id)"
done

echo "Creating menu..."
MENU_ID="$(api POST /menu/menus "{\"locationId\":\"$LOCATION_ID\",\"name\":\"Main menu\"}" | jq -r .id)"

TREE="[]"
for row in "Aqua Panna:11.00" "Prosecco:22.00" "Pizza Margherita:42.00" "Espresso:9.99"; do
    name="${row%%:*}"
    price="${row##*:}"
    node="$(jq -n --arg name "$name" --arg pid "${PRODUCT_IDS[$name]}" --arg vat "$VAT_ID" --arg price "$price" \
        '{id:null, name:$name, orderable:true, productId:$pid, price:($price|tonumber), vatTypeId:$vat, imageObject:null, description:null, children:[]}')"
    TREE="$(echo "$TREE" | jq --argjson n "$node" '. + [$n]')"
done
api PUT "/menu/menus/$MENU_ID/tree" "$TREE" >/dev/null

echo "Creating order points (S1, B1, three tables)..."
batch() { # typeId count menuId serviceOrderPointId
    jq -n --arg loc "$LOCATION_ID" --arg type "$1" --argjson count "$2" --arg menu "$3" --arg svc "$4" \
        '{locationId:$loc, typeId:$type, count:$count,
          paymentTypeIds:'"$PAYMENT_IDS"',
          allowMultipleUsers:false, keepOpen:false,
          menuId:(if $menu=="" then null else $menu end),
          serviceOrderPointId:(if $svc=="" then null else $svc end)}'
}

SERVICE_POINT="$(api POST /order-points/batch "$(batch "$SERVICE_TYPE_ID" 1 "" "")" | jq -c '.[0]')"
SERVICE_POINT_ID="$(echo "$SERVICE_POINT" | jq -r .id)"

api POST /order-points/batch "$(batch "$BAR_TYPE_ID" 1 "$MENU_ID" "")" >/dev/null
TABLES="$(api POST /order-points/batch "$(batch "$TABLE_TYPE_ID" 3 "$MENU_ID" "$SERVICE_POINT_ID")")"

# The third table carries the TD-3 10% discount.
DISCOUNTED_TABLE="$(echo "$TABLES" | jq -c '.[2]')"
DISCOUNTED_TABLE_ID="$(echo "$DISCOUNTED_TABLE" | jq -r .id)"
UPDATE_BODY="$(echo "$DISCOUNTED_TABLE" | jq '{
    name, nickname, selfOrderMode:null, discountPercent:10.00,
    selfPayTypeId, allowMultipleUsers, keepOpen, paymentTypeIds,
    menuId, serviceOrderPointId, printerId, cashRegisterId}')"
api PUT "/order-points/$DISCOUNTED_TABLE_ID" "$UPDATE_BODY" >/dev/null

echo "Creating users (TD-2)..."
make_user() { # username-prefix role
    local username="$1@$SLUG.local"
    api POST /users "{\"username\":\"$username\",\"name\":\"$1\",\"password\":\"$DEMO_PASSWORD\",\"roles\":[\"$2\"],\"clientId\":\"$CLIENT_ID\",\"locationId\":\"$LOCATION_ID\"}" >/dev/null
    echo "$username"
}
ADMIN_USER="$(make_user admin ADMIN)"
WAITER1_USER="$(make_user waiter1 WAITER)"
WAITER2_USER="$(make_user waiter2 WAITER)"
SERVICE_USER="$(make_user service SERVICE)"
WATCHER_USER="$(make_user watcher WATCHER)"

cat <<EOF

Seeded '$CLIENT_NAME' on $API_URL
  client:   $CLIENT_ID
  location: $LOCATION_ID (active)
  points:   S1=$SERVICE_POINT_ID, B1, 2 plain tables, 1 table with 10% discount ($DISCOUNTED_TABLE_ID)
  menu:     $MENU_ID (Aqua Panna 11.00, Prosecco 22.00, Pizza Margherita 42.00, Espresso 9.99)

  Demo users (password for all: $DEMO_PASSWORD)
    $ADMIN_USER
    $WAITER1_USER
    $WAITER2_USER
    $SERVICE_USER
    $WATCHER_USER
EOF
