# syntax=docker/dockerfile:1.18@sha256:dabfc0969b935b2080555ace70ee69a5261af8a8f1b4df97b9e7fbcf6722eddf

ARG VERSION=dev

FROM gradle:9.6.1-jdk21@sha256:9795f8c9009a7de6adea522b907c9aadc38fe7c313874112c2b696c8e70b0303 AS builder

ARG VERSION

WORKDIR /home/gradle/project
COPY --chown=gradle:gradle gradle gradle
COPY --chown=gradle:gradle gradlew gradlew.bat build.gradle settings.gradle gradle.properties ./
RUN --mount=type=cache,target=/home/gradle/.gradle \
    ./gradlew dependencies --no-daemon

COPY --chown=gradle:gradle LICENSE THIRD_PARTY_NOTICES.md ./
COPY --chown=gradle:gradle src src
RUN --mount=type=cache,target=/home/gradle/.gradle \
    ./gradlew clean bootJar --no-daemon -PreleaseVersion="${VERSION}" && \
    cp build/libs/rendoc.jar /tmp/rendoc.jar

FROM debian:13-slim@sha256:3a39a0592364683e6bab97937b72cad5a8fa6dcbbee90edb3bb48c7f8e94f258 AS runtime

ARG VCS_REF="unknown"
ARG VERSION

LABEL org.opencontainers.image.title="Rendoc" \
      org.opencontainers.image.description="Self-hosted Office document to PDF conversion API" \
      org.opencontainers.image.source="https://github.com/keveon/rendoc" \
      org.opencontainers.image.revision="${VCS_REF}" \
      org.opencontainers.image.version="${VERSION}" \
      org.opencontainers.image.licenses="MIT"

ENV DEBIAN_FRONTEND=noninteractive \
    HOME=/var/lib/rendoc/home \
    RENDOC_WORK_DIR=/var/lib/rendoc/work \
    RENDOC_LIBREOFFICE_WORK_DIR=/var/lib/rendoc/libreoffice \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -Djava.io.tmpdir=/tmp -Dfile.encoding=UTF-8"

RUN apt-get update && \
    apt-get install -y --no-install-recommends \
      ca-certificates \
      curl \
      fontconfig \
      fonts-crosextra-caladea \
      fonts-crosextra-carlito \
      fonts-liberation2 \
      fonts-noto-cjk \
      libreoffice-calc-nogui \
      libreoffice-core-nogui \
      libreoffice-writer-nogui \
      openjdk-21-jre-headless \
      tini && \
    rm -rf /var/lib/apt/lists/* && \
    groupadd --system --gid 10001 rendoc && \
    useradd --system --uid 10001 --gid rendoc --home-dir /var/lib/rendoc/home \
      --shell /usr/sbin/nologin rendoc && \
    install -d -o root -g root -m 0755 /app && \
    install -d -o rendoc -g rendoc -m 0750 \
      /var/lib/rendoc/home \
      /var/lib/rendoc/work \
      /var/lib/rendoc/libreoffice \
      /usr/local/share/fonts/custom

COPY --from=builder --chown=root:root /tmp/rendoc.jar /app/rendoc.jar
COPY --chown=root:root container/entrypoint.sh /usr/local/bin/rendoc-entrypoint
COPY --chown=root:root LICENSE THIRD_PARTY_NOTICES.md /usr/share/doc/rendoc/
RUN chmod 0444 /app/rendoc.jar && \
    chmod 0444 /usr/share/doc/rendoc/LICENSE /usr/share/doc/rendoc/THIRD_PARTY_NOTICES.md && \
    chmod 0755 /usr/local/bin/rendoc-entrypoint && \
    fc-cache -f

USER 10001:10001
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD curl --fail --silent --show-error http://127.0.0.1:8080/readyz || exit 1

ENTRYPOINT ["/usr/bin/tini", "--", "/usr/local/bin/rendoc-entrypoint"]
CMD ["java", "-jar", "/app/rendoc.jar"]
