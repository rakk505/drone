# FPV Drones to Minecraft — NeoForge 26.2

NeoForge 26.2 port of `FPVtoMinecraft-1.20.1-V1.1.4.jar`, using the original
airframe/goggles models, textures, audio, recipes, controller calibration,
Betaflight-style rates, OSD editor, signal/video effects, thermal controls,
battery and payload behavior.

The existing hostile drone-operator encounter is retained. Operators spawn
rarely at night in plains, forests, taiga, and savanna biomes, deploy one armed
FPV drone, acquire visible survival players, and command a pursuit/impact run.
Killing the operator cuts the link and drops its aircraft into an inert fall.

## Flying

1. Place an FPV Drone or FPV Thermal Drone on solid ground.
2. Right-click it with the remote and goggles to link both, then install a
   battery. Equip the linked goggles to enter the camera feed.
3. Press `N` to arm. With keyboard/mouse, `W/S` controls throttle, `A/D`
   controls yaw, and mouse movement controls roll/pitch. A calibrated GLFW
   controller uses the original channel mapping and rate curves.
4. `Page Up/Down` changes camera angle and `F7` cycles video resolution.
   Thermal controls are `End` (toggle), `U` (NUC), `G` (AGC), and `Home`
   (focus). Shift-scroll cycles linked goggles channels.

The FPV settings/calibration screens are available from the Controls screen
and NeoForge's mod configuration entry. Use the Betaflight item on a drone to
edit its build, rates, name, and OSD layout.

## Build target

- Minecraft 26.2
- NeoForge 26.2.0.75
- Java 25
- GeckoLib 5.5.4

The original 1.20.1 JAR is an authorized behavior and asset reference, not a
runtime dependency.
