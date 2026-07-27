#!/bin/sh
set -eu

mkdir -p "$HOME/.cache/fontconfig"
fc-cache -f >/dev/null

exec "$@"
