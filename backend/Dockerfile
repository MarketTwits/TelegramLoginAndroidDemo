FROM node:25-alpine AS dependencies
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --omit=dev && npm cache clean --force

FROM node:25-alpine AS runtime
ARG BUILD_REVISION=development
ENV NODE_ENV=production
ENV APP_REVISION=$BUILD_REVISION
WORKDIR /app
RUN apk add --no-cache tini
RUN mkdir -p /app/data && chown node:node /app/data && chmod 700 /app/data
COPY --from=dependencies --chown=node:node /app/node_modules ./node_modules
COPY --chown=node:node package.json package-lock.json ./
COPY --chown=node:node src ./src
COPY --chown=node:node public ./public
USER node
EXPOSE 8080
ENTRYPOINT ["/sbin/tini", "--"]
CMD ["node", "--disable-warning=ExperimentalWarning", "src/server.js"]
