# ghost client

Este es el cliente PvP que uso en [Ghost Launcher](https://github.com/gh0stymc/ghost-launcher), mi launcher.
Quería algo tipo Lunar pero libre y con mi propia cara, y en vez de empezar de cero me apoyé en un proyecto
excelente que ya existía — [AxolotlClient](https://codeberg.org/AxolotlClient/AxolotlClient-mod) — y lo fui
adaptando a lo que me gusta: aspecto propio, español, cosméticos y algún extra. Todo el mérito de la base es
suyo (ver el aviso de abajo).

Para Minecraft (Fabric): HUD configurable (FPS, CPS, ping, keystrokes, armadura, pociones, coordenadas…), zoom,
freelook, motion blur, hit color, utilidades de Hypixel, sonidos al matar/morir y más.

> **Aviso:** ghost client es una **versión modificada** de
> [AxolotlClient](https://codeberg.org/AxolotlClient/AxolotlClient-mod), de moehreag y colaboradores,
> publicada con la licencia **LGPL-3.0-or-later**. No es el proyecto original ni está respaldado por sus
> autores. Los cambios están en la rama `ghost` de este repositorio; el historial de git conserva el
> trabajo original y la fecha de cada cambio. El README original está en
> [`doc/README-AxolotlClient.md`](doc/README-AxolotlClient.md).
>
> No es un producto oficial de Minecraft ni está aprobado por Mojang o Microsoft.

## Qué se ha cambiado respecto al original

- **Nombre e identidad**: "ghost client" en la ventana, la pantalla de título, los menús, las teclas,
  las notificaciones, Discord y lo que ven los servidores. Iconos con el fantasma.
- **Aspecto**: menús con el estilo y los colores de Ghost Launcher, HUD con esquinas redondeadas,
  menú de mods estilo tarjetas (Right Shift) con buscador y descripciones, editor del HUD propio.
- **Mover el HUD**: icono ✥ en cada tarjeta de HUD que abre el editor con ese elemento marcado.
- **Discord**: solo el nombre del cliente, la IP del servidor (opcional) y el tiempo en ese servidor.
- **Pantalla de carga**: animación 3D propia, o una imagen/GIF/vídeo del usuario, en lugar del logo de
  Mojang (`util/GhostLoadingScreen.java`, `mixin/SplashOverlayMixin.java`).
- **Menú principal**: tu skin en 3D haciendo gestos (`util/GhostSkinPreview.java`).
- **Sound Effects** (módulo nuevo): sonidos al matar, morir y en rachas, con sonidos propios en WAV/OGG
  (`modules/sounds/SoundEffects.java`).
- **Textos en español** para las partes principales.
- **Funciones online desactivadas y ocultas** (amigos, chat, insignias, noticias): dependen de los
  servidores del proyecto original. Se controla con `GhostClient.ONLINE_FEATURES`.
- **Créditos**: la pantalla de créditos mantiene a los autores originales y el aviso de licencia.

El identificador interno del mod sigue siendo `axolotlclient` (cambiarlo obligaría a renombrar todas las
texturas y textos); el jugador no lo ve.

## Compilar

Hace falta JDK 25.

```bash
./gradlew -Paxolotlclient.modules.all=false \
  -Paxolotlclient.modules.latest=true -Paxolotlclient.modules.26.2=true \
  -Paxolotlclient.modules.26.1=true -Paxolotlclient.modules.1.21.11=true \
  :latest:build :26.2:build :26.1:build :1.21.11:build -x test
```

Los `.jar` salen en `versions/<versión>/build/libs/GhostClient-*.jar`.

| Módulo   | Minecraft |
|----------|-----------|
| latest   | 26.3      |
| 26.2     | 26.2      |
| 26.1     | 26.1.x    |
| 1.21.11  | 1.21.11   |

Herramientas usadas para el cambio de marca, los textos, los sonidos y las pruebas: [`ghost-tools/`](ghost-tools).

## Licencia

**LGPL-3.0-or-later**, igual que el original: ver [`LICENSE`](LICENSE) (LGPL) y
[`LICENSE-GPL`](LICENSE-GPL) (GPL v3, en la que se basa la LGPL).
Copyright © 2021–2026 moehreag y colaboradores de AxolotlClient; modificaciones © 2026 gh0stymc.
