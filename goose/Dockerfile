# syntax=docker/dockerfile:1

FROM debian:bookworm-slim

RUN apt-get update \
    && apt-get install --yes --no-install-recommends \
       bzip2 \
       ca-certificates \
       curl \
       libssl3 \
    && rm -rf /var/lib/apt/lists/*

# Official Goose installer, with the first-run wizard disabled for image builds.
RUN curl -fsSL \
      https://github.com/aaif-goose/goose/releases/download/stable/download_cli.sh \
    | CONFIGURE=false bash

ENV PATH="/root/.local/bin:${PATH}"
WORKDIR /workspace

COPY goose-entrypoint.sh /usr/local/bin/goose-entrypoint
ENTRYPOINT ["/usr/local/bin/goose-entrypoint"]
CMD ["--help"]
