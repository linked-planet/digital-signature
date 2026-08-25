license_secret_path := "lpd/linked-planet/atlassian/license/dc/confluence"

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
