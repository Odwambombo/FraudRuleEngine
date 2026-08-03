#!/bin/sh

payload="$(dd bs=1 count="${CONTENT_LENGTH:-0}" 2>/dev/null)"

printf 'Status: 204 No Content\r\n'
printf 'Content-Type: text/plain\r\n'
printf '\r\n'

printf 'Grafana alert webhook received: %s\n' "$payload" >&2
