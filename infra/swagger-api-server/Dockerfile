FROM node:20-alpine
WORKDIR /app
COPY server.js ./server.js
EXPOSE 8090
HEALTHCHECK --interval=10s --timeout=3s --retries=5 \
  CMD wget --spider -q http://localhost:8090/health || exit 1
CMD ["node", "server.js"]
