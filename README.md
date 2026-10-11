# Neon Tetris

A modern, neon-styled Tetris for Android, built with **Kotlin + Jetpack Compose**: 10×20 board, SRS rotation with wall kicks,
7-bag randomizer, swipe + button controls, 100 levels, combos, five paid power-ups (Google Play Billing), DataStore persistence,
particles, haptics and a full set of animations.

## Verification status (please read)

| Part | Status |
|---|---|
| Game engine, rules, scoring, levels, power-ups (`game/`, `domain/`) | **53 JUnit tests green**, run with a standalone Kotlin compiler. Includes randomized fuzzing (invariants checked after every action) and a 2,500-piece bot game whose 887 line clears are verified against an independent reference model. Mutation checks confirmed the tests fail when the engine is deliberately broken. |
| Compose UI, ViewModels, DataStore, Play Billing | **Not compiled with the Android SDK** (the build sandbox had no SDK or Maven access). All of it was type-checked against hand-written API stubs, so logic, nullability, `when` exhaustiveness and cross-file references are consistent. Library signatures follow the official Billing 8.x docs. A first Gradle build on your machine may still surface a small API mismatch; they should be quick fixes. |
| Run on device | **Not done.** Please run it and look at the feel (animation timing, control sizes) on a real phone. |

## Quick start

1. Open the folder in **Android Studio** (Ladybug or newer, JDK 17) and let Gradle sync, or run `./gradlew assembleDebug`.
2. Run on a device/emulator with **API 26+**. Unit tests: `./gradlew test`.
3. Debug builds use **simulated purchases** (`SIMULATE_PURCHASES = true` in `app/build.gradle.kts`) so you can try the whole shop
   (Buy → Owned → persisted → Restore) without Play Console. Release builds always use real Google Play Billing.

## Controls

| Action | Touch buttons | Swipe on the board |
|---|---|---|
| Move | ◀ ▶ (hold to repeat) | drag sideways, one column per cell dragged |
| Rotate | ⟲ ⟳ | tap (or flick up) |
| Soft drop | ▼ (hold) | drag down |
| Hard drop | ⤓ | flick down |
| Power-up | tap its button; Lightning / Hammer / Color Bomb then ask you to tap a target | |

## Rules and numbers (all tunable in `domain/GameRules.kt`)

* **Score:** every cleared cell is worth 5 points, times a multi-line multiplier (1 / 1.5 / 2 / 3), times a combo multiplier
  (+0.5 per consecutive clear, capped at ×5), times 2 during Double Score. Single = 50, double = 150, triple = 300, four lines = 600.
  Cells removed by power-ups are worth 3 points each.
* **Levels:** `requiredScore = level × 100`, applied to the *total* score: level 1 is completed at 100 points, level 2 at 200 … level 100
  at 10,000. (If you meant "L×100 points *within* each level", change `LevelSystem` only.) The HUD shows level and progress.
* **Speed:** milliseconds per row: L1 800 · L5 611 · L10 440 · L20 238 · L30 141 · L50 71 · L100 50. Lock delay 500 ms (15 resets).
* **Combo:** consecutive piece placements that clear lines; shows `COMBO ×2`, `×3` … with an animated popup.

## Power-ups and billing model

| Power-up | Effect |
|---|---|
| Bomb | explodes the bottom 5 rows |
| Lightning | clears one column (tap it) |
| Hammer | clears a 3×3 area (tap the centre) |
| Color Bomb | clears every block of one color (tap a block) |
| Double Score | ×2 points for 30 s |

Power-ups are locked until bought. Each is a **one-time (non-consumable) product**: buying it marks it **Owned** forever (restorable,
persisted in DataStore) and grants 3 charges; each use costs 1 charge, and every 5 levels reached during play each owned power-up
recharges by 1 (max 9). See `PowerUpRules`. Tapping a locked power-up in-game pauses and opens the shop.

**Configure Play Console**

1. Monetize → Products → One-time products: create five products whose IDs match `app/src/main/res/values/product_ids.xml`
   (defaults `neon_powerup_bomb`, `_lightning`, `_hammer`, `_color_bomb`, `_double_score`), add a *Buy* purchase option with a price, activate.
   **Product IDs live only in that file**; change them there (or override per build type/flavor).
2. Upload a build to an internal testing track, add license testers, install from Play. Billing returns products only for an app
   whose package name and signing key are known to Play. Change `applicationId`/`namespace` (`com.neon.tetris`) first.
3. To exercise real billing from a debug build, set `SIMULATE_PURCHASES` to `"false"` in the `debug` block.

Purchases are acknowledged (required within 3 days), re-applied on every app start/resume (`restorePurchases`), and a manual
**Restore Purchases** button is in the shop. Entitlements are granted on the client; for a real-money production game also verify
purchase tokens on a server. The project uses Billing Library **8.3.0**; Billing 9.x exists and has a migration guide.

## Architecture

```
ui/        Compose screens + ViewModels (Home, Game, Game Over, Shop, Settings), board renderer, effects
game/      TetrisEngine: pure Kotlin state machine (no Android), publishes StateFlow<GameState> + SharedFlow<GameEvent>
domain/    rules (scoring, levels, power-up constants) and models
data/      GameRepository on Preferences DataStore (best score, highest level, ownership, charges, settings)
billing/   BillingManager interface, PlayBillingManager, SimulatedBillingManager (debug), PowerUpCatalog (product ids)
util/      Haptics
```

The engine is driven by `update(dtMs)` from the ViewModel's game loop and is fully deterministic (inject a seeded `Random`).
Animations that gate gameplay (line clear: Flash → Glow → Shrink → Particles → Remove → blocks fall) live in the engine's
timeline; purely visual effects (piece gliding, particles, shake, popups) live in `ui/game/BoardFx`.

**Adding a power-up:** add a `PowerUpType`, implement its apply function in `TetrisEngine`, give it an icon/color/strings in `ui/`,
and add its product id to `product_ids.xml` + `PowerUpCatalog`.

## Known limitations

* No sound effects and no hold piece. Portrait only.
* A running game survives navigation and backgrounding (it auto-pauses) but not process death.
* The UI/billing layer has not been compiled against the real Android SDK in this environment (see above).
