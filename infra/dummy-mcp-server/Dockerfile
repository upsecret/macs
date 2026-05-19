FROM node:20-alpine
WORKDIR /app
COPY server.js ./server.js
EXPOSE 8765
HEALTHCHECK --interval=10s --timeout=3s --retries=5 \
  CMD wget --spider -q http://localhost:8765/health || exit 1
CMD ["node", "server.js"]
