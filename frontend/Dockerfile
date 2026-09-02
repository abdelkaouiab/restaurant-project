# syntax=docker/dockerfile:1.7

FROM node:24.19.0-alpine3.24 AS build

WORKDIR /workspace

COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci

COPY . .
RUN npm run build

FROM nginx:1.30.4-alpine3.24 AS runtime

COPY docker/nginx.conf /etc/nginx/nginx.conf
COPY --from=build --chown=101:101 /workspace/dist /usr/share/nginx/html

USER 101:101

EXPOSE 8080

ENTRYPOINT ["nginx"]
CMD ["-g", "daemon off;"]
