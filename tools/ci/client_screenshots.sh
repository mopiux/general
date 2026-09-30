#!/usr/bin/env bash
# Abre el cliente de desarrollo en una pantalla virtual (Xvfb + Mesa por software) con la
# prueba automática activada (-Pautotest): crea un mundo, arma una escena y toma capturas.
set -uo pipefail
MOD="$1"
export LIBGL_ALWAYS_SOFTWARE=1
export MESA_GL_VERSION_OVERRIDE=4.5
export MESA_GLSL_VERSION_OVERRIDE=450
chmod +x gradlew
mkdir -p shots "mods/$MOD/run"
cat > "mods/$MOD/run/options.txt" <<OPTS
lang:${AUTOTEST_LANG:-es_ar}
guiScale:3
onboardAccessibility:false
pauseOnLostFocus:false
tutorialStep:none
skipMultiplayerWarning:true
joinedFirstServer:true
renderDistance:8
OPTS
timeout 1800 xvfb-run -a -s "-screen 0 1280x720x24" ./gradlew -p "mods/$MOD" runClient -Pautotest --no-daemon > client.log 2>&1
code=$?
echo "Código de salida: $code"
echo "================ log filtrado ================"
grep -E "AutoTest|autotest|Missing texture|missing texture|Unable to load model|Unable to resolve|Exception|\[ERROR\]|/ERROR\]" client.log \
  | grep -vE "^\s+at |Realms|telemetry|narrator|OpenAL|ALSA|audio" | tail -200
cp "mods/$MOD/run/screenshots/"*.png shots/ 2>/dev/null
ls -la shots
n=$(ls shots/*.png 2>/dev/null | wc -l)
echo "Capturas: $n"
[ "$n" -ge 6 ]
