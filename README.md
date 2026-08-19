# Exsanguinate

A small Minecraft Fabric mod (1.20.1) built as a learning project.

## The Carnelian Oath

A heavy blood-ritual blade with a few mechanics:

- **Life drain** - every hit costs the wielder 1 heart. If the wielder is too
  weak to pay, the syphon "misses": the swing connects but takes no blood and
  the sword gives a dull refusal thud.
- **Infusion** - hold right-click for 2 seconds to charge the blade (3 hearts).
  The next hit deals massive bonus damage, then the charge is consumed.
  Completing the ritual flashes the screen black & white for a second.
- **Berserk mode** - at 4 hearts or less, while in combat, the wielder enters
  a rage state: max health is capped at 4 hearts (with saturation to regen up
  to it), every hit deals bonus damage, and the infusion only costs 1 heart
  and half the time. Wears off 10 s after the last combat.
- **Crafting** - the sword is made from 2 Blood Infused Iron + 1 stick.
  Blood Infused Iron is crafted from 1 iron ingot + 1 rotten flesh.

## Building / Running

```bash
./gradlew runClient     # play the mod
./gradlew build         # compile + package
```

## Ideas / Todo

- [x] **1. Syphon fails when the wielder can't pay** (barebones)
      When the player would die from the life drain, the hit still connects
      but takes no blood and plays a distinct "refusal" sound. The infusion
      is refused up front if the player can't pay, and fails with the same
      sound if health drops mid-charge.
      TODO: decide if the whole hit should whiff instead of just the syphon.
- [x] **2. Custom drain sound + black & white flash on infusion** (barebones)
      `blood_drain` (procedural heartbeat thump) plays when the ritual starts
      and completes; `blood_fail` for refusals. On completion the client
      applies the vanilla `desaturate` post shader for 1 second.
      TODO: fade back to color smoothly (custom shader instead of the
      vanilla desaturate), looping drain sound while charging.
- [x] **3. Berserk / rage mode at low health** (barebones)
      At <= 4 hearts and recently in combat: max health capped at 4 hearts,
      saturation to regen to 4, +4 damage per hit, infusion costs 1 heart and
      takes 1 s. Combat is tracked via damage dealt/taken timestamps; the
      mode ends 10 s after the last combat event.
      TODO: real "fade in" visual (red vignette, tinted screen), heartbeat
      sound + screen pulse while active, sound cue when it triggers.
- [x] **4. "Infused" description with example damage** (barebones)
      Tooltip shows "Infused! Next hit deals 16.0 damage" while charged and a
      hint line otherwise.
      TODO: show berserk state in the tooltip too, color-code the example.
- [x] **5. Crafting** (barebones)
      Blood Infused Iron (iron ingot + rotten flesh) -> the sword
      (B/B/S pattern). Uses the vanilla iron-ingot sprite as a placeholder.
      TODO: custom texture/3D model for Blood Infused Iron, cooler recipe
      (e.g. a "blood vial" item dropped by mobs, anvil ritual, redstone +
      ghast tear + iron), advancement for crafting the sword.

## Structure

- `src/main/java/net/yut/exsanguinate/` - the mod's Java code
  - `item/` - items (CarnelianOathItem, ModItems)
  - `sound/` - sound events (ModSounds)
  - `effect/` - status effects (BloodCurse)
  - `berserk/` - berserk mode logic (BerserkMode)
  - `client/` - client-only: ScreenShake, ColorFilter
  - `mixin/` - mixins that patch Minecraft classes
  - `network/` - packets (InfusionCompletePayload)
- `src/main/resources/` - assets (models, sounds, lang) and data (recipes)
- `src/main/generated/` - datagen output (run via `./gradlew runDatagen`)

## Notes

- Screen shake / filter settings are constants in
  `client/ScreenShake.java` and `client/ColorFilter.java`.
- The black & white filter needs "Fancy" graphics (vanilla post shaders).
- Sounds were generated procedurally as placeholders - replace the `.wav`
  files in `assets/exsanguinate/sounds/` with real audio any time.