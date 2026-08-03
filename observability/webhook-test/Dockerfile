FROM busybox:1.37.0-musl

COPY webhook.cgi /www/cgi-bin/alerts
COPY health.txt /www/health

RUN chmod 0555 /www/cgi-bin/alerts

EXPOSE 8080

USER 65532:65532

CMD ["httpd", "-f", "-p", "8080", "-h", "/www"]
