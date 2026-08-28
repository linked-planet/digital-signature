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
    rm -rf ./local-env/confluence-home ./local-env/postgres-home ./local-env/confdb.dump
    echo "Wiped local-env homes. Run: just up"

# Snapshot confluence-home + pg_dump into local-env/local-env-homes.zip (needs sudo for confluence-home)
save-home:
    #!/usr/bin/env bash
    set -euo pipefail
    {{compose}} stop confluence --timeout 120 2>/dev/null || true
    if ! {{compose}} exec -T postgres pg_isready -U confdb >/dev/null 2>&1; then
      {{compose}} up postgres -d --wait
    fi
    mkdir -p ./local-env
    {{compose}} exec -T postgres pg_dump -U confdb -Fc confdb > ./local-env/confdb.dump
    cd ./local-env
    # sudo: confluence-home is owned by container users
    sudo rm -f local-env-homes.zip
    sudo zip -r local-env-homes.zip confluence-home confdb.dump \
        -x "confluence-home/analytics-logs/*" \
           "confluence-home/plugins/installed-plugins/*digital-signature*.jar" \
           "confluence-home/plugins/.bundled-plugins/*" \
           "confluence-home/plugins/.osgi-plugins/*" \
           "confluence-home/plugins-osgi-cache/*" \
           "confluence-home/plugins-cache/*" \
           "confluence-home/export/*" \
           "confluence-home/log/*" \
           "confluence-home/logs/*" \
           "confluence-home/tmp/*" \
           "confluence-home/temp/*" \
           "confluence-home/secure-tunnel/*" \
           "confluence-home/index/*" \
           "confluence-home/journal/*" \
           "confluence-home/.java/*" \
           "confluence-home/plugins-temp/*" \
           "confluence-home/webresource-temp/*" \
           "confluence-home/viewfile/*" \
           "confluence-home/sandbox/*" \
           "confluence-home/.cache/*" \
           "confluence-home/docker-app.pid"

# Restore confluence-home + DB from local-env/local-env-homes.zip (needs sudo)
load-home:
    #!/usr/bin/env bash
    set -euo pipefail
    {{compose}} down 2>/dev/null || true
    cd ./local-env
    sudo rm -rf confluence-home postgres-home confdb.dump || true
    unzip -o local-env-homes.zip
    sudo chmod -R 777 confluence-home
    if [[ -f confdb.dump ]]; then
      sudo rm -rf postgres-home
      cd ..
      {{compose}} up postgres -d --wait
      set +o pipefail
      cat ./local-env/confdb.dump | {{compose}} exec -T postgres \
        pg_restore -U confdb -d confdb --clean --if-exists --no-owner --single-transaction
      rc=$?
      set -o pipefail
      if [[ $rc -ne 0 && $rc -ne 1 ]]; then
        echo "pg_restore failed (exit $rc). confluence-home was restored but the DB may be incomplete — run just reset-env and retry." >&2
        exit $rc
      fi
      echo "Restored confluence-home + confdb. Run: just up"
    elif [[ -d postgres-home ]]; then
      sudo chmod -R 777 postgres-home
      echo "Restored legacy zip (postgres-home). Run: just up"
    else
      echo "zip missing confdb.dump and postgres-home" >&2
      exit 1
    fi


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
