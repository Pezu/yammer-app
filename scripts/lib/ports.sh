# Port/project-name arithmetic shared by env-up.sh, env-down.sh and seed-tenant.sh.
# CLAUDE.md §7: worktree base ports are db 5600 / gcs 4600 / api 8200 / web 4400, offset by
# (issue number mod 50) * 10; the develop integration stack uses the fixed offset 500.
#
# Sets DB_PORT, GCS_PORT, API_PORT, WEB_PORT and PROJECT_NAME for the given id ("develop" or
# an issue number). Source this file, then call compute_ports <id>.
compute_ports() {
    local id="$1"
    local offset
    if [[ "$id" == "develop" ]]; then
        PROJECT_NAME="yammer-develop"
        offset=500
    elif [[ "$id" =~ ^[0-9]+$ ]]; then
        PROJECT_NAME="yammer-$id"
        offset=$(( (id % 50) * 10 ))
    else
        echo "usage: $0 <issue-number|develop>" >&2
        exit 1
    fi
    DB_PORT=$((5600 + offset))
    GCS_PORT=$((4600 + offset))
    API_PORT=$((8200 + offset))
    WEB_PORT=$((4400 + offset))
}
