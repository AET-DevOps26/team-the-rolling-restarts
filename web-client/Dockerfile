FROM node:22-alpine@sha256:16e22a550f3863206a3f701448c45f7912c6896a62de43add43bb9c86130c3e2 AS base
WORKDIR /app

FROM base AS deps
COPY package.json package-lock.json ./
RUN npm ci

FROM deps AS development
COPY . .
EXPOSE 3000
CMD ["npm", "run", "dev", "--", "--hostname", "0.0.0.0", "--port", "3000"]

FROM deps AS builder
COPY . .
RUN npm run build

FROM base AS production-deps
COPY package.json package-lock.json ./
RUN npm ci --omit=dev

FROM node:22-alpine@sha256:16e22a550f3863206a3f701448c45f7912c6896a62de43add43bb9c86130c3e2 AS production
WORKDIR /app
ENV NODE_ENV=production
# --chown=node:node: node:22-alpine defaults to root (Dockle CIS-DI-0001); `node` is the base
# image's own built-in non-root user. Ownership matters here (not just USER below) since Next's
# production server can write to .next/cache at runtime (ISR revalidation) — a bare USER switch
# with root-owned files would fail on first write.
COPY --from=builder --chown=node:node /app/package.json /app/package-lock.json /app/next.config.ts ./
COPY --from=builder --chown=node:node /app/.next ./.next
COPY --from=builder --chown=node:node /app/public ./public
COPY --from=production-deps --chown=node:node /app/node_modules ./node_modules
USER node
EXPOSE 3000
CMD ["npm", "run", "start", "--", "--hostname", "0.0.0.0", "--port", "3000"]
