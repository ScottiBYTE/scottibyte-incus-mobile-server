FROM node:22-bookworm-slim

ENV NODE_ENV=production
ENV APP_HOME=/app
ENV DATA_DIR=/app/data
ENV HOME=/app/data
ENV INCUS_CONFIG=/app/data/incus-client

WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
      ca-certificates \
      curl \
      gnupg \
      openssh-client \
      sqlite3 \
      tini \
      gosu \
      python3 \
      make \
      g++ \
    && install -d -m 0755 /etc/apt/keyrings \
    && curl -fsSL https://pkgs.zabbly.com/key.asc -o /etc/apt/keyrings/zabbly.asc \
    && sh -c 'echo "deb [signed-by=/etc/apt/keyrings/zabbly.asc] https://pkgs.zabbly.com/incus/stable bookworm main" > /etc/apt/sources.list.d/zabbly-incus-stable.list' \
    && apt-get update \
    && apt-get install -y --no-install-recommends incus-client \
    && rm -rf /var/lib/apt/lists/*

COPY package*.json ./
RUN npm ci --omit=dev

COPY . .

RUN mkdir -p /app/data /app/data/ssh /app/data/incus-client \
    && chmod +x /app/docker-entrypoint.sh \
    && chmod 700 /app/data/ssh /app/data/incus-client

EXPOSE 3088

ENTRYPOINT ["/usr/bin/tini", "--", "/app/docker-entrypoint.sh"]
CMD ["node", "server.js"]
