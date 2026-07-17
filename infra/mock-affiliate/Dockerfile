FROM node:22-alpine
WORKDIR /app
COPY server.mjs ./server.mjs
USER node
EXPOSE 8090
HEALTHCHECK --interval=5s --timeout=3s --retries=10 CMD wget --quiet --tries=1 --spider http://127.0.0.1:8090/health || exit 1
ENTRYPOINT ["node", "/app/server.mjs"]
