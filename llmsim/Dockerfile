# syntax=docker/dockerfile:1

ARG LLMSIM_VERSION=0.10.1
FROM ghcr.io/pramalin/llmsim-build:${LLMSIM_VERSION} AS build

COPY WorkspaceSummaryFlow.scala \
  /build/src/main/scala/com/alaiengineering/filesmcp/llmsim/WorkspaceSummaryFlow.scala
RUN sbt assembly

FROM eclipse-temurin:21-jre-jammy

RUN apt-get update \
    && apt-get install --yes --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY --from=build /build/target/scala-3.3.3/llmsim.jar /app/llmsim.jar

ENV LLMSIM_SCRIPT=com.alaiengineering.filesmcp.llmsim.WorkspaceSummaryFlow \
    LLMSIM_PORT=8089 \
    LLMSIM_JOURNAL_MAX_ENTRIES=1000

EXPOSE 8089
ENTRYPOINT ["java", "-jar", "/app/llmsim.jar"]
