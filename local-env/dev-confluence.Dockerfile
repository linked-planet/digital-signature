# syntax=docker/dockerfile:1
ARG CONFLUENCE_VERSION
FROM atlassian/confluence:${CONFLUENCE_VERSION}

ENV FRONTEND_DEV_MODE=${FRONTEND_DEV_MODE}
ENV JVM_SUPPORT_RECOMMENDED_ARGS="-Xdebug \
 -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005 \
 -Dupm.plugin.upload.enabled=true \
 -Dplugin.webconsole.enabled=true \
 -Datlassian.upm.signature.check.disabled=true \
 -Dcom.atlassian.plugins.authentication.basic.auth.filter.force.allow=true \
 -Dfrontend.devMode=${FRONTEND_DEV_MODE}"

ENV CONFLUENCE_LOG_STDOUT="true"
ENV TZ=Europe/Berlin

# Ports
EXPOSE 8090
EXPOSE 8091
EXPOSE 5005
