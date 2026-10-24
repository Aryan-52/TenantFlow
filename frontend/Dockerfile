FROM node:22-alpine AS build
WORKDIR /app

# Optional: bake an absolute API URL into the build (e.g. when NOT using the nginx
# reverse proxy below, such as pointing this frontend at a remote backend host).
# Left empty by default so the app calls same-origin relative URLs and relies on
# the /api/ reverse proxy configured in nginx.conf.
ARG VITE_API_URL=""
ENV VITE_API_URL=$VITE_API_URL

COPY package.json package-lock.json ./
# Reproducible install from the lockfile.
RUN npm ci
COPY . .
RUN npm run build

# Unprivileged nginx: runs as uid 101 and listens on 8080 (non-root cannot bind :80).
FROM nginxinc/nginx-unprivileged:1.31-alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=3s --start-period=10s --retries=5 \
  CMD wget -q -O /dev/null http://127.0.0.1:8080/healthz || exit 1
