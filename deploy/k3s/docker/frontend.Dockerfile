FROM node:22-alpine AS build

WORKDIR /workspace
COPY log-platform-ui/package.json log-platform-ui/package-lock.json log-platform-ui/
RUN cd log-platform-ui && npm ci
COPY log-platform-ui log-platform-ui
RUN cd log-platform-ui && npm run build:prod

FROM nginx:1.27-alpine
COPY deploy/k3s/docker/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/log-platform-ui/dist /usr/share/nginx/html

EXPOSE 80
