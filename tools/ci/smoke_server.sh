#!/usr/bin/env bash
# Prueba de humo: instala un servidor Forge 1.20.1 real, copia los .jar compilados
# (reobfuscados, como los usaría un jugador), genera un mundo, ejecuta los comandos
# de tools/ci/smoke_commands.txt y falla si aparece alguna excepción o crash.
set -uo pipefail
JARS_DIR="$1"
FORGE="1.20.1-$(grep '^forge_version=' mods/alquimia/gradle.properties | cut -d= -f2)"
ROOT="$PWD"
mkdir -p smoke && cd smoke

curl -sSLf -o installer.jar "https://maven.minecraftforge.net/net/minecraftforge/forge/$FORGE/forge-$FORGE-installer.jar"
java -jar installer.jar --installServer > install.log 2>&1 || { tail -50 install.log; exit 1; }

mkdir -p mods && cp "$JARS_DIR"/*.jar mods/
ls -la mods
echo "eula=true" > eula.txt
cat > server.properties <<PROPS
online-mode=false
level-seed=20240601
spawn-protection=0
view-distance=6
simulation-distance=6
max-tick-time=-1
PROPS
echo "-Xmx3G" > user_jvm_args.txt

rm -f in.fifo && mkfifo in.fifo
exec 3<>in.fifo
./run.sh nogui < in.fifo > server.out 2>&1 &
PID=$!

wait_for() { # patrón, timeout
  local t=0
  until grep -q "$1" server.out; do
    sleep 2; t=$((t+2))
    if ! kill -0 $PID 2>/dev/null; then echo "El servidor murió"; return 1; fi
    if [ $t -ge "$2" ]; then echo "Timeout esperando: $1"; return 1; fi
  done
}

status=0
if wait_for 'Done (' 600; then
  echo "Servidor listo. Ejecutando comandos..."
  while IFS= read -r line || [ -n "$line" ]; do
    [ -z "$line" ] && continue
    case "$line" in
      \#wait*) sleep "${line#\#wait }";;
      \#expect*) ;;
      \#*) ;;
      *) echo ">> $line"; echo "$line" >&3; sleep 1;;
    esac
  done < "$ROOT/tools/ci/smoke_commands.txt"
  echo "stop" >&3
else
  status=1
fi

for i in $(seq 1 120); do kill -0 $PID 2>/dev/null || break; sleep 1; done
kill -9 $PID 2>/dev/null

echo "================ server.out (filtrado) ================"
grep -vE "^\s*at |DEBUG" server.out | tail -400

if ls crash-reports/*.txt >/dev/null 2>&1; then
  echo "!!! CRASH REPORT"; cat crash-reports/*.txt | head -200; status=1
fi
if grep -nE "Exception|Caused by|\[ERROR\]|/ERROR\]|FATAL" server.out | grep -vE "Mixin apply failed.*mixins.*optional|Couldn't load|Failed to fetch|narrator" ; then
  echo "!!! Se encontraron errores en el log"; status=1
fi
grep -q 'Done (' server.out || status=1

# Líneas que tienen que aparecer en el log ("#expect <regex>" en smoke_commands.txt)
while IFS= read -r line || [ -n "$line" ]; do
  case "$line" in
    \#expect*)
      pattern="${line#\#expect }"
      if grep -qE "$pattern" server.out; then echo "OK   esperado: $pattern"; else echo "FALTA esperado: $pattern"; status=1; fi;;
  esac
done < "$ROOT/tools/ci/smoke_commands.txt"
exit $status
