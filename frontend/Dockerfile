# Dockerfile frontend - CHI DUNG cho moi truong demo (TV6, tuan 8+).
# Luc code hang ngay thi chay `npm run dev`, KHONG can file nay.

# --- Giai doan 1: build ra file tinh ---
FROM node:20-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm install
COPY . .
RUN npm run build

# --- Giai doan 2: phuc vu bang nginx ---
FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
