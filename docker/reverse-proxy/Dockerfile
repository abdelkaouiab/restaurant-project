FROM nginx:1.30.4-alpine3.24

COPY nginx.conf /etc/nginx/nginx.conf
COPY proxy_params /etc/nginx/proxy_params

USER 101:101

EXPOSE 8080 8443

ENTRYPOINT ["nginx"]
CMD ["-g", "daemon off;"]
