# TV estilo Pixel

**Un inicio para Android TV / Google TV que se ve como el launcher de un teléfono Google Pixel.**

> 🍴 **Esto es una bifurcación (fork) de [Clean Your TV](https://github.com/Gathin23/cleanyourtv), de [Gathin](https://github.com/Gathin23).** Todo el mérito del proyecto es suyo: la limpieza de la TV, el launcher, las herramientas y la documentación son trabajo de él. Aquí solo hay una variación de su launcher con otro estilo. Si te gusta, apoya y dale una estrella al [proyecto original](https://github.com/Gathin23/cleanyourtv).

*[English below](#english)*

<!-- FOTOS: aquí van las capturas de la TV (inicio, ajustes rápidos, menú de entradas). -->

## Quién lo hizo

No soy programador, solo alguien con tiempo libre que quería que su TV se viera bonita. Todo lo hice conversando con **Claude** (la IA de Anthropic): yo decía qué quería y cómo se veía en mi TV, y Claude escribía y ajustaba el código.

## Qué cambiamos respecto al original

- 🔵 **Estilo Pixel:** cada app es un ícono circular uniforme, con anillo de foco y sin banners.
- 🕐 **Reloj de dos líneas** como en la pantalla bloqueada del Pixel, con la fecha bajo el saludo y el clima bajo el reloj.
- 🎛️ **Ajustes rápidos y menús estilo Pixel**, también el menú de entradas HDMI. Sin zoom al enfocar, solo cambio de color.
- 🧱 **Dock** sin nombres, con un marco oscuro translúcido y angosto ajustado a los círculos.
- 🔤 **Fuente Plus Jakarta Sans** en todo el sistema.
- 👋 **Saludo fijo** que tú eliges (por ejemplo "Hola, Ana") en lugar de "Buenos días / Buenas tardes".
- 🎬 **Fondos de video propios que rotan solos** cada cierto tiempo, y un script para enviarlos sin complicaciones.

## Probado en

- **Xiaomi TV Box S (3.ª generación)**, Google TV, Android 14. Funciona muy bien.
- **Hisense 55Q6N**, Google TV. Ojo: en mi Hisense, después de quitar las apps de fábrica, el Wi‑Fi dejó de conectar (por cable de red sí funciona). Si tienes una Hisense, quita apps con cuidado y de pocas.

Debería servir en otros equipos Android TV / Google TV (Android 8 o más nuevo), pero no está probado. No sirve en Samsung (Tizen), LG (webOS) ni Fire TV.

## Cómo ponerlo en tu TV o TV box

### La forma fácil: que una IA lo haga por ti

Así lo hice yo, sin saber programar.

1. **En la TV:** ve a *Ajustes → Sistema → Acerca de* y presiona OK **7 veces** sobre *Compilación*. Luego entra a *Opciones de desarrollador* y activa **Depuración USB** y **Depuración de red / ADB** si aparece.
2. **Busca la IP de la TV** en *Ajustes → Red* (algo como `192.168.1.50`). La computadora debe estar en la **misma red** que la TV.
3. **En tu computadora** (Windows, Mac o Linux), instala [Claude Code](https://claude.com/claude-code) u otro asistente que pueda usar la terminal, y descarga este repositorio (botón verde **Code → Download ZIP**, y descomprímelo).
4. Abre el asistente dentro de esa carpeta y dile algo como:

   > Quiero instalar este launcher en mi TV. La IP es 192.168.1.50. Sigue PROMPT.md, primero haz un respaldo, desactiva (sin desinstalar) las apps que no uso, compila e instala el launcher, y aplica el tema `themes/pixel-tv` con mi nombre y mi ciudad.

5. **Acepta en la TV** el aviso de "¿Permitir depuración?" cuando aparezca, y responde lo que te pregunte el asistente (qué apps usas, tu ciudad, etc.).
6. **Al terminar, apaga la depuración** en la TV (USB y de red). Es importante por seguridad.

### Si prefieres hacerlo tú mismo

Con JDK 17, el Android SDK y Python 3 instalados, desde la carpeta del repositorio:

```bash
python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt
TV_HOST=192.168.1.50 launcher/build-and-install.sh
TV_HOST=192.168.1.50 tools/theme.sh themes/pixel-tv
```

Para quitar las apps de fábrica, sigue [PROMPT.md](PROMPT.md) (del proyecto original). Lee sus reglas de seguridad antes de empezar.

## Personaliza

- **Saludo y ciudad:** edita [`themes/pixel-tv/config.json`](themes/pixel-tv/config.json): `greeting_text` (tu saludo), `weather_label`, `weather_latitude` y `weather_longitude` (tu ciudad y sus coordenadas, las sacas de Google Maps). Luego vuelve a enviar el tema con `tools/theme.sh`.
- **Cada cuánto cambia el fondo:** `fondo_rotation_minutes` (por ejemplo `60`). Con `0` el fondo queda fijo.
- **Fondos de video:** no vienen incluidos. Descarga videos horizontales que puedas usar (por ejemplo de [Pexels](https://www.pexels.com/videos/)) y envíalos así:

  ```bash
  TV_HOST=192.168.1.50 tools/fondo.sh sequoia ~/Descargas/mi-video.mp4
  ```

  Hay cinco espacios: `gg-sunset`, `gg-night`, `sequoia`, `noche` y `utah`. Si ya tienes los videos listos en una carpeta con esos nombres, `tools/fondos.sh <carpeta>` los envía todos de una vez.

## Volver a como estaba

```bash
adb shell cmd package set-home-activity <paquete-del-inicio-original>
adb uninstall com.gathin.tvhome
TV_HOST=192.168.1.50 tools/restore.sh
```

`tools/restore.sh` (del proyecto original) devuelve las apps de fábrica que se hayan desactivado.

## Créditos y licencia

- **[Clean Your TV](https://github.com/Gathin23/cleanyourtv) de [Gathin](https://github.com/Gathin23)** es el proyecto original y el autor de todo lo que hace funcionar esto. Licencia MIT.
- Este fork parte de la variante [My System TV](https://github.com/gabzferrer/my-system-tv) de gabzferrer, también basada en Clean Your TV.
- Estilo Pixel hecho por **[GeoL21](https://github.com/GeoL21)** con ayuda de **Claude (Anthropic)**.
- Fuente Plus Jakarta Sans (SIL Open Font License, ver `launcher/FONT-LICENSE-PlusJakartaSans-OFL.txt`). Íconos Material Symbols de Google (Apache 2.0).
- "Pixel", "Google TV" y las marcas de las apps son de sus dueños. Este proyecto no está relacionado con Google.
- Licencia MIT, igual que el original (ver [LICENSE](LICENSE)).

---

## English

**A Google Pixel-style home screen for Android TV / Google TV.**

This is a **fork of [Clean Your TV](https://github.com/Gathin23/cleanyourtv) by [Gathin](https://github.com/Gathin23)**, and all credit goes to him: the debloat, the launcher, the tools and the docs are his work. This fork only restyles his launcher. If you like it, star the [original project](https://github.com/Gathin23/cleanyourtv).

I'm not a programmer, just someone with free time. I built this by talking with **Claude** (Anthropic's AI): I described what I wanted and how it looked on my TV, and Claude wrote and adjusted the code.

**What changed:** round, uniform Pixel-style app icons; a two-line Pixel lock-screen clock; Pixel-style quick settings, menus and HDMI input menu; a slim translucent dock; Plus Jakarta Sans font; a custom fixed greeting; your own video backgrounds that rotate on their own.

**Tested on:** Xiaomi TV Box S (3rd gen, Android 14) and Hisense 55Q6N (after removing factory apps, Wi‑Fi stopped connecting on the Hisense; Ethernet still works).

**Easiest install:** enable Developer options, USB debugging and network debugging on the TV, note its IP, download this repo on a computer on the same network, open [Claude Code](https://claude.com/claude-code) (or a similar assistant) in the folder and ask it to follow [PROMPT.md](PROMPT.md), install the launcher and apply `themes/pixel-tv`. Turn debugging off when done.

**Manual install:**

```bash
python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt
TV_HOST=<tv-ip> launcher/build-and-install.sh
TV_HOST=<tv-ip> tools/theme.sh themes/pixel-tv
```

Edit `themes/pixel-tv/config.json` for your greeting and city. Add video backgrounds with `tools/fondo.sh`. Undo with `tools/restore.sh` and `adb uninstall com.gathin.tvhome`.

**Credits:** [Clean Your TV](https://github.com/Gathin23/cleanyourtv) by Gathin (MIT), via [My System TV](https://github.com/gabzferrer/my-system-tv) by gabzferrer. Pixel style by [GeoL21](https://github.com/GeoL21) with help from Claude (Anthropic). Not affiliated with Google. MIT License.
