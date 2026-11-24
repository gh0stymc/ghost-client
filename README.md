# ghost client

**English** · [Español](README.es.md)

This is the PvP client I use in [Ghost Launcher](https://github.com/gh0stymc/ghost-launcher), my launcher.
I wanted something Lunar-like but free and with my own face, and instead of starting from scratch I built on an
excellent project that already existed — [AxolotlClient](https://codeberg.org/AxolotlClient/AxolotlClient-mod) —
and adapted it to what I like: its own look, Spanish, cosmetics and a few extras. All credit for the base goes
to them (see the notice below).

For Minecraft (Fabric): configurable HUD (FPS, CPS, ping, keystrokes, armor, potions, coordinates…), zoom,
freelook, motion blur, hit color, Hypixel utilities, kill/death sounds and more.

> **Notice:** ghost client is a **modified version** of
> [AxolotlClient](https://codeberg.org/AxolotlClient/AxolotlClient-mod), by moehreag and contributors,
> released under the **LGPL-3.0-or-later** license. It is not the original project and is not endorsed by its
> authors. The changes live on the `ghost` branch of this repository; the git history preserves the original
> work and the date of each change. The original README is at
> [`doc/README-AxolotlClient.md`](doc/README-AxolotlClient.md).
>
> Not an official Minecraft product; not approved by or associated with Mojang or Microsoft.

## What's changed from the original

- **Name and identity**: "ghost client" in the window, title screen, menus, keybinds, notifications, Discord
  and what servers see. Ghost icons.
- **Look**: menus styled with Ghost Launcher's colors, rounded-corner HUD, card-style mods menu (Right Shift)
  with search and descriptions, its own HUD editor.
- **Move the HUD**: a ✥ icon on each HUD card that opens the editor with that element selected.
- **Discord**: just the client name, the server IP (optional) and the time on that server.
- **Loading screen**: its own 3D animation, or the user's image/GIF/video, instead of the Mojang logo
  (`util/GhostLoadingScreen.java`, `mixin/SplashOverlayMixin.java`).
- **Main menu**: your skin in 3D doing gestures (`util/GhostSkinPreview.java`).
- **Sound Effects** (new module): sounds on kills, deaths and streaks, with your own WAV/OGG sounds
  (`modules/sounds/SoundEffects.java`).
- **Spanish text** for the main parts.
- **Online features disabled and hidden** (friends, chat, badges, news): they rely on the original project's
  servers. Controlled with `GhostClient.ONLINE_FEATURES`.
- **Credits**: the credits screen keeps the original authors and the license notice.

The mod's internal id is still `axolotlclient` (changing it would mean renaming every texture and text); players
never see it.

## Building

Requires JDK 25.

```bash
./gradlew -Paxolotlclient.modules.all=false \
  -Paxolotlclient.modules.latest=true -Paxolotlclient.modules.26.2=true \
  -Paxolotlclient.modules.26.1=true -Paxolotlclient.modules.1.21.11=true \
  :latest:build :26.2:build :26.1:build :1.21.11:build -x test
```

The `.jar` files come out in `versions/<version>/build/libs/GhostClient-*.jar`.

| Module   | Minecraft |
|----------|-----------|
| latest   | 26.3      |
| 26.2     | 26.2      |
| 26.1     | 26.1.x    |
| 1.21.11  | 1.21.11   |

Tools used for the rebrand, texts, sounds and testing: [`ghost-tools/`](ghost-tools).

## License

**LGPL-3.0-or-later**, same as the original: see [`LICENSE`](LICENSE) (LGPL) and
[`LICENSE-GPL`](LICENSE-GPL) (GPL v3, which the LGPL builds on).
Copyright © 2021–2026 moehreag and AxolotlClient contributors; modifications © 2026 gh0stymc.
