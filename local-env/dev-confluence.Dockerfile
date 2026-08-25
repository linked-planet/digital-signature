# syntax=docker/dockerfile:1
ARG CONFLUENCE_VERSION
FROM atlassian/confluence:${CONFLUENCE_VERSION}

RUN test -n "$CONFLUENCE_VERSION"
ENV JVM_SUPPORT_RECOMMENDED_ARGS="-Xdebug -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005 -Dupm.plugin.upload.enabled=true -Dplugin.webconsole.enabled=true"
ENV ATL_LICENSE_KEY=${CONFLUENCE_LICENSE}
ENV CONFLUENCE_LOG_STDOUT="true"
ENV TZ=Europe/Berlin

# Ports
EXPOSE 8090
EXPOSE 8091
EXPOSE 5005
