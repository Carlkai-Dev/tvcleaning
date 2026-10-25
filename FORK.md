# Fork de GeoL21

Base: [My System TV](https://github.com/gabzferrer/my-system-tv) de gabzferrer (MIT), a su vez basado en Clean Your TV.

Cambios de este fork:

| Qué | Cómo |
|---|---|
| Saludo fijo | Clave de tema `greeting_text` (p. ej. `"Hola"`) en lugar de "Buenos días / Buenas tardes". |
| Fondos que rotan solos | Clave de tema `fondo_rotation_minutes` (p. ej. `60`): al volver al Inicio pasa al siguiente video. `0` deja el fondo elegido a mano en el Centro de control. |
| Fondos sin `adb` | `tools/fondos.sh <carpeta>` envía `gg-sunset.mp4`, `gg-night.mp4`, `sequoia.mp4`, `noche.mp4` y `utah.mp4` (y sus `.jpg`) con `tools/tv.py`, a través del ThemeProvider, para que Android 14 deje leerlos. |
| Nombres de los fondos | Cascada, Cueva verde, En la oscuridad, Capa roja y Entre llamas. |

El tema personal (nombre, ciudad) y los videos viven en el PC, no en este repositorio.
| Estilo Pixel | Mosaicos de sistema (Inicio, Ajustes, Play Store…) con iconos Material Symbols Rounded (Apache 2.0) sobre colores tonales, y los iconos pequeños con los mismos símbolos. |
| Google Sans Flex | Reemplaza a Outfit en `res/font/outfit.ttf` (el nombre del archivo se mantiene para no tocar el código). Licencia SIL OFL en `launcher/FONT-LICENSE-GoogleSansFlex-OFL.txt`. |
| Estilo Pixel (`pixel_style`) | Clave de tema: cada app es un círculo con su propio ícono (sin banners ni banda de vidrio), anillo de foco, reloj de dos líneas con los minutos en azul claro y ajustes rápidos en panel claro y opaco como los del Pixel. |
