FROM node:20-alpine AS build
WORKDIR /app

# Optional: bake an absolute API URL into the build (e.g. when NOT using the nginx
# reverse proxy below, such as pointing this frontend at a remote backend host).
# Left empty by default so the app calls same-origin relative URLs and relies on
# the /api/ reverse proxy configured in nginx.conf.
ARG VITE_API_URL=""
ENV VITE_API_URL=$VITE_API_URL

COPY package*.json ./
RUN npm install
COPY . .
RUN npm run build

FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
