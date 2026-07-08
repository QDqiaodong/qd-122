ARG DOCKER_REGISTRY

FROM ${DOCKER_REGISTRY}/library/node:20 AS builder
WORKDIR /app

COPY package.json package-lock.json .npmrc ./
RUN npm ci --no-audit --no-fund

COPY . .
RUN npm run build

FROM ${DOCKER_REGISTRY}/library/nginx:1.27 AS production
WORKDIR /usr/share/nginx/html

COPY --from=builder /app/dist .
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
