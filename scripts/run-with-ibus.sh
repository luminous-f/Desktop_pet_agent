#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

unset LC_ALL
export LANG="${LANG:-zh_CN.UTF-8}"
export GTK_IM_MODULE=ibus
export QT_IM_MODULE=ibus
export XMODIFIERS=@im=ibus
export SDL_IM_MODULE=ibus
export GLFW_IM_MODULE=ibus

start_pet() {
  ibus-daemon -drx >/dev/null 2>&1 || true
  exec mvn javafx:run
}

if [ -z "${DBUS_SESSION_BUS_ADDRESS:-}" ]; then
  exec dbus-run-session -- bash -lc '
    unset LC_ALL
    export LANG="${LANG:-zh_CN.UTF-8}"
    export GTK_IM_MODULE=ibus
    export QT_IM_MODULE=ibus
    export XMODIFIERS=@im=ibus
    export SDL_IM_MODULE=ibus
    export GLFW_IM_MODULE=ibus
    ibus-daemon -drx >/dev/null 2>&1 || true
    exec mvn javafx:run
  '
fi

start_pet
