# digital-signature
[![CI Build State](https://github.com/baloise/digital-signature/workflows/CI/badge.svg)](https://github.com/baloise/digital-signature/actions?query=workflow%3A%22CI%22)
[![Codacy Badge](https://api.codacy.com/project/badge/Grade/2e7111f7beb84222bdf07f8fc3c788f9)](https://app.codacy.com/app/baloise/digital-signature?utm_source=github.com&utm_medium=referral&utm_content=baloise/digital-signature&utm_campaign=Badge_Grade_Dashboard)

A digital signature macro for [Atlassian Confluence](https://www.atlassian.com/software/confluence)

## Description
Allows confluence users to write contracts in a confluence macro which can be signed directly by logged-in users.
- content and signatures can not be modified once signed
- white list users who can sign
- report when and by whom the contract was signed
- easily send email to signers of the contract
- receive notifications, when your contract was signed

## ClassCastException issue
![ClassCastException in Macro](./docs/img/classcastexception.png)

If you observe issues in the Macro resulting in a `ClassCastException` please update digital-signature to version 7.0.7 or later,
clear the plugin cache (one last time) and restart confluence.

For background information please refer to [#82](https://github.com/baloise/digital-signature/issues/82) 
and ['How to clear Confluence plugins cache'](https://confluence.atlassian.com/confkb/how-to-clear-confluence-plugins-cache-297664846.html).

## Privacy Policy
- We do not transfer or store any data outside your Atlassian product.
- We have no access to any data you stored within your Atlassian product.
- Your data is yours - no strings attached.

## Installation & Usage
Install via [Atlassian Marketplace](https://marketplace.atlassian.com/plugins/com.baloise.confluence.digital-signature).

A detailed description of the available configuration fields and usage is described in
the [Wiki...](https://github.com/baloise/digital-signature/wiki/Signature-Macro-Usage)

## Using Confluence Data Center Version
Digital-signature can be used on Confluence Data Center, however it is not yet officially tested and approved.

## Feature overview
### Insert / edit macro
![](./docs/img/contract_text.png)
![](./docs/img/edit.png)

### Sign
![](./docs/img/sign.png)

- Set signers, title notified users and layout of the contract
- One click approval. User management is done by Confluence.
- The signature remains valid only as long the title and body are the same as at the time of signature.

### Markdown
![](./docs/img/markdown.png)

### Mail notification
![](./docs/img/report_email_export.png)
![](./docs/img/send_mail.png)

## Build & Run

Local Confluence runs via Docker (`local-env/`). Build/deploy is driven by [`.justfile`](.justfile).

### Prerequisites

- JDK **21**, Maven 3.9.6+ / [`mvnd`](https://github.com/apache/maven-mvnd), [`just`](https://github.com/casey/just), Docker
- Confluence DC license available via `gopass`/`pass` at `lpd/linked-planet/atlassian/license/dc/confluence`, **or** export `CONFLUENCE_LICENSE` yourself
- Optional: `nix develop` (flake) — pulls JDK/just/mvnd/maven and sets `CONFLUENCE_VERSION` + `CONFLUENCE_LICENSE` in the shell

### Maven Toolchain

`~/.m2/toolchains.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<toolchains>
    <toolchain>
        <type>jdk</type>
        <provides>
            <version>21</version>
            <vendor>eclipse-temurin</vendor>
        </provides>
        <configuration>
            <jdkHome>/usr/lib/jvm/temurin-21-jdk-amd64</jdkHome>
        </configuration>
    </toolchain>
</toolchains>
```

### Local Confluence (Docker)

`local-env/docker-compose.yml` runs Confluence + Postgres 16. State lives in `local-env/confluence-home` and `local-env/postgres-home` (gitignored).

| Port | Service |
|------|---------|
| `8090` | Confluence HTTP |
| `8091` | Confluence (synchrony / related) |
| `5005` | JDWP (remote debug) |
| `5432` | Postgres (`confdb` / `confdb` / `confdb`) |

Env (compose / Dockerfile):

| Variable | Purpose |
|----------|---------|
| `CONFLUENCE_VERSION` | Image tag (default `10.2.15`; `just confluence-version` reads `pom.xml`) |
| `CONFLUENCE_LICENSE` | DC license key injected as `ATL_LICENSE_KEY` |
| `FRONTEND_DEV_MODE` | Optional; default `false` |

```bash
# from repo root — with license + version in the environment (nix develop does this)
just up          # build image, start Confluence + Postgres (force-recreate)
just logs        # follow Confluence logs
# wait until healthcheck is green → http://localhost:8090

just build       # package plugin (mvnd, tests skipped)
just deploy      # UPM upload → http://localhost:8090 (admin/admin)
just redeploy    # build + deploy

just down        # stop (120s grace) then compose down
just restart     # down && up
```

Interactive menu: `just` / `just --choose`.

#### Homes snapshot

```bash
just save-home   # zip confluence-home + postgres-home → local-env/local-env-homes.zip (sudo; skips logs/tmp/plugin jars)
just load-home   # restore from that zip (sudo; chmod 777 homes)
just reset-env   # wipe homes — re-run setup wizard after `just up`
```

Attach a remote debugger to `localhost:5005` (suspend=n).

## Contribute
Keep it simple: every contribution is welcome. Either if you report an issue, help on solving one, or contribute to the
solution by adding a Pull Request.

For a more detailed answer on best practices and guidelines please have a look at our [open source community](https://github.com/baloise/open-source) at Baloise.

## Credits
This macro was written by [Matthias Cullmann](@culmat).
It is maintained in the Open Source Community at Baloise.

## License
This project is licensed under the [Apache-2.0 License](https://github.com/baloise/digital-signature/blob/main/LICENSE).

## Releases
[Build Artifacts](https://github.com/baloise/digital-signature/tree/gh-pages/)
