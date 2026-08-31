# FPV Drone Rebuilt

NeoForge 26.2 rebuild of the `FPV To Minecraft` 1.20.1 Mosquito/default 9-inch FPV drone and Mavic payload-dropping drone.

The rebuild also includes hostile drone operators: stationary villagers in field fatigues and radio headsets. Each operator deploys one armed Mosquito, keeps it in a realistic loiter pattern while acquiring line of sight, and then commands a velocity-led intercept after a continuous target lock. Killing the operator immediately cuts the control link and disarms its drone.

Operators can be placed with the Drone Operator Spawn Egg. Rare single operators also spawn at night on open terrain in plains, forests, taiga, and savanna biomes.

## Gameplay

1. Craft and place a Mosquito or Mavic drone on a solid surface.
2. Right-click it with its matching battery to install the pack.
3. Load an RPG-7 warhead into the Mosquito, or up to two 40 mm payloads into the Mavic.
4. Right-click with the matching controller to link, then press `G` to enter the camera.
5. Press `N` to arm. `W/S` control pitch, `A/D` roll, `Z/X` yaw, and `Space/Left Shift` throttle or climb/descent.
6. Press `B` to release one Mavic payload, `H` for position hold, `P` for return-to-home, and `G` to exit.

Sneak-right-click an unarmed drone with an empty hand to recover the airframe, battery, and unused payloads. Battery durability represents remaining charge.

## Simulation and safety

- Server-authoritative input, position, velocity, battery, signal, arming, release, and detonation state.
- SI-unit 20 Hz flight integration with quaternion attitude, Betaflight-style acro rates, stabilized velocity control, quadratic drag, mass-dependent thrust, LiPo voltage sag, and payload mass changes.
- Mosquito impact warhead with directional shaped-charge damage.
- Mavic two-station payload bay; released rounds inherit aircraft momentum, use realistic gravity/drag, require a 30-tick arming interval, can be deflected, and become recoverable duds on premature impact.
- Control timeout disarms the Mosquito and sends the Mavic into return-to-home.
- Hostile operators remain planted at their station, ignore creative/spectator players, require a 30-tick visual lock, and cannot have their autonomous aircraft hijacked with a player controller.
- Block damage is disabled by default and configurable in the common config.

## Build target

- Minecraft 26.2
- NeoForge 26.2.0.7-beta
- Java 25

The original 1.20.1 JAR was used only as the authorized behavioral and asset reference for this port; it is not a runtime dependency.
