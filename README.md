# Exsanguinate

A small Minecraft Fabric mod (1.20.1) built as a learning project.

## The Carnelian Oath

A heavy blood-ritual blade with a few mechanics:

- **Blood strike** - the blade alone is dull (5 damage). A full-power swing
  (attack cooldown >= 90%) costs the wielder 1 heart and deals +10 damage.
  Spam-clicking costs nothing and does nothing special. If the wielder is too
  weak to pay (1 heart or less) the blade refuses: the swing connects dull,
  with a refusal thud.
- **Blood Curse** - every payment (swing or infusion) leaves an open wound
  for 5 s: no natural (food) healing until it closes, refreshed by each
  payment. Without it a full food bar heals faster than the sword drains.
  Berserk keeps its own regen.
- **Weight** - a fed hit shoves the target back, plays three low layered
  sounds, sprays blood, kicks the camera (shake + roll) and slows the wielder
  for half a second of recovery. Infused hits do all of that, bigger.
- **Infusion** - hold right-click for 2 seconds to charge the blade (3 hearts,
  paid when the ritual completes; a heartbeat quickens while you hold). The
  next full swing deals +24 damage instead of the normal bonus, then the
  charge is consumed. Completing the ritual plays a short "charged" cue.
- **Berserk mode** - every fed hit builds rage (the hit's damage before
  armor). At 55 rage in one fight - two charged hits, one charged + two
  normal, or four normal - the wielder enters a rage state: max health is
  capped at 4 hearts (with saturation to regen up to it), every full swing
  deals +4 damage, and the infusion only costs 1 heart and half the time.
  Wears off 10 s after the last combat (or when the sword is put away);
  rage resets after 10 s out of combat or on death. A berserk wielder leaves bloody footprints (alternating feet,
  fading after 5 s) that every nearby player can see, and sees a blood
  vignette on their own screen: it fades in zooming out from the center and
  fades out zooming back in. They also hear (only them) a low lub-dub
  heartbeat and an eerie looping drone, fading in and out with the vignette.
  Finally, the whole screen drains of colour while berserk: full black &
  white, except reds (blood, the blade's glow, the vignette), which keep
  their colour. It fades in over 1 s and out over 2 s, via the custom post
  shader in `client/ColorFilter`.
- **Forging** - the sword is not crafted, it is forged in a blood ritual
  (see below).

## The Blood Ritual

Everything is in its own creative tab, "Exsanguinate". Names are coloured
(Carnelian Oath dark red, Heavy Cleaver and Altar of the Lesser Zenith gold) by overriding
`getName()`. In game every item only shows its lore, if it has any (see
`LoreItem`; the lines live in `lang/en_us.json` as `<key>.lore.1`, `.lore.2`...;
the Heavy Cleaver has none, so it shows just its name). The real
rules, for me:

1. **Heavy Cleaver** (`cleaver`, crafted: `II` / `II` / ` S`, I = iron
   ingot, S = stick). Kill something with it while holding **iron ingots in
   the off hand**: each kill has a 50% chance to turn one ingot into
   **Bloodquenched Iron** (`blood_infused_iron`). Blood flies from the
   victim into the ingot with a hiss; a miss only gives off a wisp of smoke.
2. **Altar of the Lesser Zenith** (`blood_altar`, crafted: `RFR` / `DDD` / ` D `,
   R = redstone, F = rotten flesh, D = polished deepslate). Right-click it
   with an item to lay one on it (up to 4), right-click with an empty hand
   to take the last one back. The items float above the basin at their
   true size (the Carnelian Oath is huge there too).
3. Lay **2 Bloodquenched Iron + 1 stick** on it. The altar is now primed:
   it seeps red spores, drones very quietly (heard only within the same 6
blocks a kill reaches) (a lower, sicker cousin of the
   berserk drone, see `client/AltarHumSound`) and whispers now and then.
4. **Kill a creature within 6 blocks of it.** Its blood leaps from the
   corpse to the altar and the 4 second ritual begins: the offering rises,
   spins faster and closes in while a spiral of blood tightens around it
   and a heartbeat quickens; in the last second the items tremble and melt
   together. The end is dark, not flashy: black ink and a thick cloud of
smoke spill out with one deep thud, a ring of
   blood stains the floor, and players within 12 blocks get one dull camera
   thud and 3 seconds of Darkness. Nothing is heard past ~16 blocks. The
   Carnelian Oath is left floating above the altar; take it with an empty
   hand.

Rituals live in `ritual/Rituals.java` (one line each: offering + result).
Timings, sounds and particles are in `block/entity/BloodAltarBlockEntity`
(`WITNESS_RADIUS`, `DARKNESS_TICKS` = 0 to drop the darkness,
`WHISPER_CHANCE`), the floating items in `client/BloodAltarRenderer`, the
drone volumes in `client/AltarHumSound`.

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
- [x] **2. Custom drain sound on infusion**
      `blood_drain` (procedural heartbeat thump) plays when the ritual starts
      and completes; `blood_fail` for refusals.
      TODO: looping drain sound while charging.
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
- [x] **5. Crafting** -> replaced by the blood ritual (see above)
      Cleaver kills with iron in the off hand make Blood Infused Iron (50%);
      the sword is forged on the Blood Altar by a sacrifice. Custom 16x16
      textures for the cleaver, the iron and the altar.
      TODO: real 3D model for the cleaver, data-driven (JSON) rituals,
      custom sounds for the altar (vanilla stand-ins for now: soul sand
      valley + crimson forest loops at half pitch, soul sand valley
      additions, beacon hum, warden heartbeat, sculk catalyst, respawn
      anchor).

## Structure

- `src/main/java/net/yut/exsanguinate/` - the mod's Java code
  - `item/` - items (CarnelianOathItem, CleaverItem, LoreItem, ModItems),
    ColoredBlockItem (the altar's gold name) and ModItemGroups (the mod's
    own creative tab - add new items there)
  - `block/` - the Blood Altar block (ModBlocks), and in `block/entity/`
    its block entity (items + ritual timer) and ModBlockEntities
  - `ritual/` - the rituals the altar knows and the kill that starts them
  - `sound/` - sound events (ModSounds)
  - `effect/` - status effects (BloodCurse)
  - `berserk/` - berserk mode logic (BerserkMode)
  - `client/` - client-only: ScreenShake, ColorFilter, BerserkVignette,
    BerserkSounds, BloodAltarRenderer, AltarHumSound and BerserkPreview
    (a dev-only F9 key that fakes berserk, so its visuals can be tested
    without grinding rage)
  - `mixin/` - mixins that patch Minecraft classes
  - `network/` - packets (BerserkStatePayload, RitualCompletePayload)
- `src/main/resources/` - assets (models, textures, sounds, lang) and data
  (recipes, loot table, tags). Note: 1.20.1 reads `data/<mod>/recipes/` and
  `loot_tables/` (plural); the singular names are 1.21+.

## Testing (dev loop)

Do not build a jar and drop it in a real launcher for everyday changes: that
means a full 80-mod client boot plus a big save every time. Use the dev
client instead:

```
dev.bat                (or: ./gradlew runClient)
```

- **Skip the main menu.** Create the test world once: Creative, Superflat,
  name it `exsanguinate-dev` (Superflat loads fastest). From then on the dev
  client jumps straight into it (`--quickPlaySingleplayer`, set up in
  `build.gradle`). Use another save with `-PquickPlayWorld=MyWorld`.
- **Keep the client running.** A restart is only needed for code changes
  (and even then, see below).
  - `F3+T` reloads textures, models, lang, `sounds.json` and shaders. Run
    `./gradlew processResources` first if you edited files under
    `src/main/resources` (they are copied to `build/resources/main`, which
    is what the game reads). Shader edits also need the filter to reload:
    press F9 twice.
  - `/reload` reloads data packs: recipes, loot tables, tags.
  - F9 (dev only) toggles the berserk visuals, so the colour drain, vignette
    and sounds can be checked without building 55 rage.
- **Code changes.** `runClient` only recompiles changed files, so a fresh
  launch is the simple way. For method-body changes, run the client from
  IntelliJ in Debug (the bundled JetBrains Runtime supports enhanced class
  redefinition) and use *Reload Changed Classes*; changes in `mixin/` still
  need a restart. New fields/methods need a restart too unless the enhanced
  redefinition is enabled in *Settings > Build, Execution, Deployment >
  Debugger > HotSwap*.
- `./gradlew build` is only for sharing the mod or putting it in a real
  modpack; the jar lands in `build/libs/`.

## Notes

- Screen shake / filter settings are constants in
  `client/ScreenShake.java` and `client/ColorFilter.java`.
- The berserk colour filter (`client/ColorFilter`) is a custom post shader
  in `assets/exsanguinate/shaders/` and needs "Fancy" graphics. Its fade
  times are constants in `ColorFilter`; the desaturation strength is the
  `Desaturation` value in `berserk_desaturate.json` (1.0 = full black &
  white).
- Minecraft only plays `.ogg`. The procedural `blood_drain.wav` and
  `blood_fail.wav` were never audible, so `sounds.json` currently points those
  two events at vanilla sounds (warden heartbeat, stone hit). To use real
  audio: drop `.ogg` files in `assets/exsanguinate/sounds/` and point the
  events at them like `sword_swing` does.
- Also using vanilla stand-ins until custom audio exists:
  `berserk_heartbeat` (warden heartbeat), `berserk_ambience` (soul sand
  valley loop, pitched up) and `infusion_charged` (crossbow loaded click).
  The drone loops, so its custom `.ogg` should loop seamlessly; mark it
  `"stream": true` in `sounds.json` like vanilla ambience. Heartbeat speed
  and the volumes are in `client/BerserkSounds`.
- Damage numbers and feel constants live at the top of `CarnelianOathItem`
  (blood cost/bonus, knockback, recovery, `IMPACT_VOLUME`) and
  `client/ScreenShake`. Footprint spacing is in `BerserkMode`, their
  lifetime and size in `client/BloodFootprintParticle`. Vignette fade times
  and zoom are in `client/BerserkVignette`; the image is
  `assets/exsanguinate/textures/misc/blood_vignette.png` (transparent
  where the world should show through).