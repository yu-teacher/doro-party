FROM nginx:1.27-alpine

# Vite base(/party/)와 같은 경로 아래에 파일을 둔다. 게이트웨이가 /party/ 를 그대로 전달한다.
COPY dist /usr/share/nginx/html/party
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
HEALTHCHECK --interval=10s --timeout=3s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://127.0.0.1/party/ || exit 1
CMD ["nginx", "-g", "daemon off;"]
