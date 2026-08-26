license_secret_path := "lpd/linked-planet/atlassian/license/dc/confluence"
deploy_url := "http://localhost:8090"
deploy_user := "admin"
deploy_pass := "admin"

default:
  @just --choose

# ═══════════════════════════════════════════════════════════════════════════════
#                                BUILD & DEPLOY
# ═══════════════════════════════════════════════════════════════════════════════

# Clean Build Artifacts
clean:
  mvnd clean

# Build Plugin
build *args="":
    mvnd package \
        -am \
        -DskipTests \
        -Dstyle.color=always \
        {{args}}


# Deploy plugin
# NOTE: upm-plugin is flaky with maven daemon
deploy:
    mvn upm:uploadPluginFile \
        -Ddeploy.url={{deploy_url}} \
        -Ddeploy.username={{deploy_user}} \
        -Ddeploy.password={{deploy_pass}} \
        -Dfrontend.devMode=true \
        -Dstyle.color=always

# Build and deploy in single step
redeploy *args="":
  @just build {{args}}
  @just deploy


# ═══════════════════════════════════════════════════════════════════════════════
#                              DOCKER SERVICES
# ═══════════════════════════════════════════════════════════════════════════════

compose := "docker compose -f ./local-env/docker-compose.yml -p local-env"

# Start Docker Services
up:
    {{compose}} up \
      --always-recreate-deps \
      --force-recreate \
      -d \
      --build

# Stop Docker Services (graceful stop first — Confluence needs time to flush)
down:
    {{compose}} stop --timeout 120
    {{compose}} down --remove-orphans

# Restart all Docker Services
restart: down && up

# Wipe local Confluence+Postgres state (re-run setup wizard afterwards)
reset-env: down
    #!/usr/bin/env bash
    set -euo pipefail
    rm -rf ./local-env/confluence-home ./local-env/postgres-home
    echo "Wiped local-env homes. Run: just up"

# Snapshot confluence-home + postgres-home into local-env/local-env-homes.zip (needs sudo)
save-home:
    #!/usr/bin/env bash
    set -euo pipefail
    cd ./local-env
    # sudo: home dirs are owned by container users
    sudo rm -f local-env-homes.zip
    sudo zip -r local-env-homes.zip confluence-home postgres-home \
        -x "confluence-home/analytics-logs/*" \
           "confluence-home/plugins/installed-plugins/*digital-signature*.jar" \
           "confluence-home/plugins/.bundled-plugins/*" \
           "confluence-home/plugins/.osgi-plugins/*" \
           "confluence-home/export/*" \
           "confluence-home/log/*" \
           "confluence-home/tmp/*"

# Restore confluence-home + postgres-home from local-env/local-env-homes.zip (needs sudo)
load-home:
    #!/usr/bin/env bash
    set -euo pipefail
    cd ./local-env
    sudo rm -rf confluence-home postgres-home || true
    unzip local-env-homes.zip
    sudo chmod -R 777 confluence-home postgres-home


# ═══════════════════════════════════════════════════════════════════════════════
#                                 UTILITIES
# ═══════════════════════════════════════════════════════════════════════════════

# Print the Confluence version from pom.xml
confluence-version:
    @awk -F'[<>]' '/<confluence.version>/{ print $3 }' pom.xml | tr -d ' '

# Print the Confluence license from gopass or pass
confluence-license:
    #!/usr/bin/env bash
    set -euo pipefail
    if command -v gopass >/dev/null 2>&1 && gopass show {{license_secret_path}} >/dev/null 2>&1; then
        gopass show {{license_secret_path}} | tr -d '\n'
    elif command -v pass >/dev/null 2>&1; then
        pass show {{license_secret_path}} | tr -d '\n'
    else
        echo "Couldn't retrieve secret: gopass or pass not installed" >&2
        exit 1
    fi

logs:
    {{compose}} logs -f confluence
