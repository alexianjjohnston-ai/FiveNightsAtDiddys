# Five Nights at Diddy's

A five-night horror-comedy remix of the 2014 Java fan game `fivenightsatfreddysjava-1.0`. It keeps that game's rules (office, doors, lights, power, camera network, enemy routes and attack timing) and reskins it as **Diddy's Platinum Studios**, a fictional after-hours recording venue haunted by photo-cutout parody versions of P. Diddy, Jay-Z, Biggie and Kanye. The characters are fictional parodies in an invented scenario. Nothing in the game refers to real events or statements.

The original project in `~/Downloads/fivenightsatfreddysjava-1.0` was not modified. This folder is a separate copy.

## Running it

You need **Java 11 or newer**. This Mac has Java 11.0.31.

| What | Command |
|---|---|
| Play the packaged game | `java -jar dist/FiveNightsAtDiddys.jar` |
| Build the JAR | `./build.sh` (creates `dist/FiveNightsAtDiddys.jar`, ~16 MB, code and assets included) |
| Run from source (reads `assets/` directly, good while swapping art) | `./run.sh` |
| Run the rule checks | `./test.sh` |

The JAR is a normal Java application, not a standalone native app: it needs a Java runtime installed. It works offline and needs no accounts or network access.

**In the browser:** every push to `main` runs `.github/workflows/pages.yml`, which runs the checks, builds the JAR and publishes it with `web/index.html` to GitHub Pages. The page runs the JAR through [CheerpJ](https://cheerpj.com) (free for personal, non-commercial use; its small licence notice stays). The game fills the browser window, shows its own loading screen until the menu is drawn, saves progress in the browser, and has a fullscreen button that appears when the mouse moves. To keep it loading quickly, the menu's assets load first and everything else loads in the background. Sounds are mono 22 kHz WAV and the rooms are JPEG; run `java tools/OptimizeAssets.java assets` after adding art. CheerpJ can't decode JPEGs with an embedded colour profile, so that tool also re-saves JPEGs as plain sRGB.

**Platform support:** built and tested on **macOS only** (this Mac). The code is plain Java/Swing with no native parts, so it should run on Windows and Linux with Java 11+, but that hasn't been tested.

## Controls

| Input | Action |
|---|---|
| Move the mouse to the screen edges | Look left/right around the office |
| Hover the bar at the bottom, or **Space** | Raise or lower the camera monitor |
| Click **DOOR**, or **A** / **D** | Close or open the left / right door (toggle) |
| Click **LIGHT**, or **Q** / **E** | Turn the left / right hall light on or off (toggle) |
| Click a box on the camera map | Switch camera (monitor up only) |
| **Esc** | Pause / resume (Esc on a menu goes back) |
| **M** | Mute / unmute |

The left door/light panel starts off-screen, as in the original. Look left to reach it; the keyboard shortcuts work from any view.

## Characters

| Original role | Replacement | Preserved behaviour | Assets |
|---|---|---|---|
| **Freddy** | **P. Diddy** | Starts on stage and moves only after both others have left. Moves in a fixed pattern every 17 s: stage → VIP Lounge → Restrooms → East Hall → East Hall Corner, then shuffles between the last two. Attacks only if, on his move, he's in the corner, the **monitor is up** and the **right door is open**. The jumpscare waits until you lower the monitor. He also appears in the power outage. | `diddy.jpg` (main), `diddy_full.jpg` (distant sightings, power outage), `diddy_side.jpg` (East Hall Corner), `diddy_snarl.jpg` (jumpscare) |
| **Bonnie** | **Jay-Z** | Fastest, every 10 s. Random advance along the west route: stage → Lounge → Green Room → West Hall → Gear Closet → West Corner → **left door**. Never retreats: once at the door he waits there. Attacks if, on his move, the **monitor is up** and the **left door is open**. Seen with the **left light**. | `jayz.png` (main), `jayz_full.jpg` (distant sightings), `jayz_laugh.jpg` (Gear Closet, jumpscare) |
| **Chica** | **Biggie** | Every 13 s, same random rule as Jay-Z on the east route: stage → Lounge → Restrooms → East Hall → East Corner → **right door**. Same attack rule with the right door. Seen with the **right light**. | `biggie.jpg` (mirrored or tilted for variety; no freely licensed Biggie photos exist on Wikimedia Commons) |
| **Foxy** | **Kanye** | Every 19 s he advances one stage behind the **Vinyl Vault** curtain, unless you're watching that camera at that moment (watching freezes him). At stage 3 the Vault is empty. On his next move he attacks the **left door**: closed means he bangs on it and resets, open means an instant jumpscare. Watching the West Hall at stage 3 shows him sprinting at the camera; the monitor drops and you have 0.5 s for the door to be shut. | `kanye.jpg` (main), `kanye_profile.jpg` (peeking through the curtain), `kanye_full.jpg` (out of the Vault), `kanye_shades.jpg` (sprint, jumpscare) |

## Rules reference: original vs remix

Times are in 10 ms ticks, as in the original loop.

| System | Original behaviour (from the 2014 source) | Remix | Intentional differences |
|---|---|---|---|
| Night length | 27000 ticks (4.5 min), no clock shown | Same; clock shows 12 AM–6 AM, 45 s per hour, from the single game timer | Clock and night number added to the HUD |
| Nights | One night; returns to the menu after a win | Five nights with saved progress | Nights 1–4 are new (see below); Night 5 is the original exactly |
| Enemy timing | Bonnie 1000, Chica 1300, Freddy 1700, Foxy 1900 | Same | None |
| Difficulty | Per-enemy `Difficulty` field that skips a move when `1+floor(rand·D)==D`, but it was never set (D=0, never skips) | Same roll, D per night | Extension; see the night table |
| Attack rules | Bonnie/Chica/Freddy: decided on their move with the monitor up and that door open; the jumpscare plays when you lower the monitor. Foxy: attacks at stage 3 if the left door is open | Same | None |
| Blocking | Enemies don't step into a room *number* another enemy holds, even across different routes | Same (kept as an original quirk) | None |
| Power drain | Table 0.5/1/2/4/5/6 % per device count, every 100 ticks, but the device count was never wired in (always 0.5 %), so power ran out at 200 s, before 6 AM at 270 s | Same table, device count wired in, every **250** ticks | **Fix.** The original night couldn't be won. Idle: ends at 46 %. One device on all night: runs out just before 6 AM. |
| Devices | Lights, closed doors and the monitor each count as 1 | Same | None |
| Power display | 10 % steps (100, 90, … 10, 0) | Same | None |
| Power out | At 0 %: dark office with Freddy, then static, then Game Over | Same timing (0.8 s dark office with Diddy, static until 2 s, then Game Over); devices switch off once | Placeholder power-down sound added |
| Monitor | Hover the bottom bar; 200 ms lockout; can't raise it once an attack is decided | Same, plus Space | **Fix:** the original re-flipped on every mouse movement inside the bar; now it flips once per entry |
| Doors/lights | Toggles; door protects instantly (no animation); light shows the doorway | Same | **Fix:** hitboxes now move with the buttons (the original's were fixed on screen while the buttons panned) and don't respond behind the monitor |
| Cameras | 10 cameras, original map and hitboxes, image sweeps 200 px | Same layout, labels and hitboxes; selected camera highlighted; light static/scanlines | Room names reskinned; ids unchanged |
| Foxy sprint | West Hall A run, 124 ticks, monitor forced down, door check 50 ticks later | Same | **Fix:** the original ran the sprint counter whenever West Hall A was watched, at any stage (a phantom kill), and could only play it once per night |
| Jumpscares | Bonnie 44, Chica 64, Freddy 112, Foxy 76 ticks, then 200 ticks of static, then Game Over, then the menu | Same lengths; photo-cutout lunge with shake; scream cut at the end like the original | Game Over offers **Retry / Main Menu** instead of a forced menu return |
| 6 AM vs attack | The clock is checked before AI in each tick; a pending attack (monitor still up) is beaten by 6 AM | Same order kept. Once a jumpscare has *started*, the clock stops and the attack wins | Rule documented and tested |
| Lounge rendering | Several combinations drew nothing (black), and Bonnie-close showed Chica's image | Every occupant drawn | Display fix only |
| Intro | 5 s newspaper | 5 s "help wanted" flyer (Night 1) or "12:00 AM / Nth Night" card; click to skip after 1 s | Skip added |

**Nights:**

| Night | D | Chance each move opportunity is skipped | Applies to |
|---|---|---|---|
| 1 | 2 | 50 % | all four (Diddy and Kanye's roll is new; theirs had no roll in the original) |
| 2 | 3 | 33 % | all four |
| 3 | 4 | 25 % | all four |
| 4 | 6 | 17 % | all four |
| 5 | 0 | never: identical to the original game | Bonnie/Chica roll as in the original |

These night values are my extension, not recovered original values. They live in `src/diddys/Config.java` (`NIGHT_SKIP_D`), along with every other timing and power number.

## Usability additions

- Pause menu (Resume / Restart Night / Main Menu). The game also pauses automatically when the window loses focus or is minimised, and mouse-driven panning is cleared.
- Settings: master volume (10 % steps), mute, **reduced flashing** (no strobing or flicker, gentler shake), **sound captions** (on-screen text for knocks, sprints, doors and power failure).
- Resizable window with letterboxing. Art is never stretched, and all clicks are mapped back to the 1280×720 layout.
- New Game asks before erasing an existing campaign. Continue opens the night select (disabled until a campaign exists).

## Saves

Progress (unlocked night, campaign complete) and settings are stored at
`~/Library/Application Support/FiveNightsAtDiddys/save.properties` (macOS), `%APPDATA%\FiveNightsAtDiddys\` (Windows), or `~/.local/share/FiveNightsAtDiddys/` (Linux). That's separate from anything the original game used, and never inside the game folder. A damaged save is renamed to `save.properties.bad` and the game starts fresh. Delete the file to reset everything.

## Replacing art and sounds

Every file the game uses, and every character placement, is listed in **`assets/manifest.properties`**. No Java changes are needed.

**Swap a character photo:** replace the file in `assets/characters/` (keep the name, or change `char.<id>.file`). Then:
- `outline`: polygon (x,y points in the photo's own pixels) around the person. The background outside it becomes transparent, with `feather` px of soft edge. For an already-transparent PNG, leave `outline` empty.
- `key = RRGGBB:lo:hi`: optional removal of a flat studio backdrop (used for Jay-Z).
- `eyes`: points for the glowing-eye effect. `face = x,y w,h`: the crop used for jumpscares and close-ups.
- `grade = saturation red green blue contrast`: colour match to the rooms.

**Switch which photo a spot uses:** each placement names its photo first (`place.lounge.diddy = diddy_full ...`). `jumpscare.<id>` picks the jumpscare photo and `menu.twitch` the menu flash photo.

**Signs, posters and plaques** (`sign.<name>` lines) are drawn in code over the original pizzeria signage: `room x y w h rotation style [b=brightness] [faces=ids] | text | text`. Styles are `poster`, `paper`, `board`, `banner`, `plaque_gold` and `plaque_platinum`. **Text lines** (`text.intro.N`, `text.win.N`, `text.lose.N`, `text.paycheck.memo`) set the night-card session, the 6 AM line and the Game Over lines.

**Move or resize a character in a room:** edit its `place.<room>.<state>` line:
`x=` / `y=` bottom-centre anchor in room pixels (rooms are 1600×720), `h=` height, `b=` brightness, `a=` opacity, `crop=` part of the photo, `rot=` tilt, `fade=` bottom dissolve, `shade=` top-to-bottom darkening, `glow=1` eye glints, `flip=1` mirror, `z=` layer order, `occ=` rectangles of the room redrawn in front (doorframes, curtains).

Use `./run.sh` to see changes immediately; run `./build.sh` to update the JAR. A missing character photo shows a labelled "PHOTO MISSING" silhouette. A missing room or interface image stops the game with a message naming the file. A missing sound is skipped.

**Asset inventory:**

| Purpose | Files | Source |
|---|---|---|
| Office (normal, left light, right light, dark) | `rooms/office*.png` | Original game |
| 10 camera rooms | `rooms/stage, lounge, green_room, vault_closed, vault_open, restrooms, gear_closet, west_hall, west_corner, east_hall, east_corner .png` | Original game (empty-room versions, no animatronics) |
| Doors, door/light buttons, monitor bar, map, camera labels, static, usage bars | `ui/*.png` | Original game |
| Characters | `characters/*.jpg/png`: 12 photos | Your three photos, plus nine Wikimedia Commons photos (credited in [CREDITS.md](CREDITS.md)) |
| Studio signage (see below) and the help-wanted flyer / paycheck ending | drawn in code from the manifest | New |
| Ambience, menu music, light buzz, door, knock, sprint, scream, camera blip, static | `audio/*.wav` | Original game |
| **Power-down cue, 6 AM chime** | `audio/power_down.wav`, `audio/chime_6am.wav` | **Placeholders** generated by `tools/MakeAssets.java`. Replace with real sounds |

## Studio dressing

The original pizzeria signage is covered by studio signage drawn from the artists' real music careers, all editable in the manifest:
- **Office:** the "Diddy's Platinum Studios" poster with all four acts and their labels (Bad Boy, Def Jam, Roc-A-Fella, G.O.O.D. Music), replacing "CELEBRATE!". Platinum and gold record plaques (*No Way Out*, *Reasonable Doubt*, *Juicy*) replace the children's drawings.
- **Cameras:**
  - *Watch the Throne* tour poster (W. Hall Corner, replacing the "Let's Party" poster)
  - Studio policy notice (E. Hall Corner, replacing "Rules for Safety")
  - Bad Boy Records banner (VIP Lounge)
  - "Session in progress" and "Runaway" boards (Vinyl Vault)
  - "Talent only" (Green Room)
  - "Out of order" (Restrooms)
- **Nights 2–5:** the intro card names the night's session after a song (Hypnotize, Run This Town, Can't Nobody Hold Me Down, Watch the Throne).

## Still not final

- **Room art is still the original pizzeria renders.** The signage is replaced, but the pizza wall décor, party hats, checkered floors and the Green Room's animatronic heads remain. A full music-venue look needs new room renders.
- **Two placeholder sounds** (power-down and 6 AM chime) need replacing.
- Biggie still has only one photo (mirrored and tilted for variety), because no freely licensed photos of him exist. Jay-Z's extra photos show short hair, while the main photo has locs.
- **Distribution:** the room/UI art and sounds belong to the original FNAF creator, and your three original photos are press photos of unknown licence. The Commons photos need the credits in CREDITS.md. Treat this as a personal, non-commercial project and keep any repository private.

## Test report

| Level | What was done | Result |
|---|---|---|
| Compilation | `javac --release 11 -Xlint:all` on all sources | Clean (two harmless serialVersionUID warnings) |
| Automated rule checks (`./test.sh`) | 102 distinct checks. **Night 5 is compared tick-by-tick with a literal copy of the original enemy code** (60 seeds, same random numbers, scripted player); a deliberately broken rule was confirmed to fail it. Also covered: clock and 6 AM, the power table and drain totals, single outage, legal moves over 200 random nights (all five nights), every door/countermeasure, Diddy's retreat, Kanye freeze/knock/sprint (including the two fixes), pending attack vs 6 AM, jumpscare freezing the night, monitor lockout, full restart reset, pause/focus loss, office hitboxes at every pan position, save round-trip and damaged saves, and the full five-night campaign/continue/new-game flow | All pass |
| Visual inspection | 56 headless renders, including every new pose and sign. Fixes made: sign positions, pose framing, occluders now keep signs in front, and the plaque text balance. Earlier: 49 headless renders of every screen, every camera with each occupant, the sprint, all four jumpscares, the outage, defeat, victory and ending. Reviewed and fixed (cutout edges, eye glow, poster position, doorframe overlap) | Reviewed |
| Runtime, real window | `java -jar` launch with no errors or warnings. A dev-only smoke test (`java -Ddiddys.dev=true -Ddiddys.smoke=DIR -cp build/classes:assets diddys.Main`) drove the live window with real Swing mouse/key events at 1600×844, 900×732 and 1280×732: New Game, intro skip, office pan, door/light clicks, A/D/Q/E, monitor hover, map clicks after resizing, Space, Esc pause/resume, minimise → auto-pause with no time jump, mute, a Jay-Z attack → jumpscare → static → Game Over → Retry. It captured what the window painted at each size (letterboxing correct) | 25/25 pass. Clock measured at 102 ticks/s (target 100) |
| Balance probe | A simple bot that only uses visible information (`BalanceBot`), 400 nights each | Win rate 79 / 59 / 48 / 44 / 32 % for Nights 1–5, usually finishing with 2–9 % power |
| **Not done** | **No human has played a full night yet.** I couldn't see or hear the live window (macOS blocked screen capture), so pacing, scare effectiveness, audio levels and mix are **unverified by ear and eye**. Not tested on Windows or Linux | Please play a night or two and report anything off |

## Development mode

`./run.sh --dev` (or `-Ddiddys.dev=true`) enables keys that are off in normal play. On the menu, **1–5** starts that night. In a night:
- **F1**: enemy-state overlay
- **F2**: +30 s
- **F3**: −10 % power
- **F4**: Kanye to stage 3
- **F5** / **F6**: Jay-Z / Biggie to their door
- **F7**: Diddy to the East Hall Corner
- **F8**: jump to 5:59 AM

`java -Djava.awt.headless=true -cp build/classes:assets diddys.Main --shots DIR` renders the review scenes to PNGs.

## Code map

`src/diddys/`:
- `Config`: every gameplay number.
- `Sim`: one night's rules, a port of the original `Main.run()` and enemy classes.
- `Cam`: the camera network.
- `Game`: screens, input and audio cues.
- `Renderer`: drawing.
- `Assets`: manifest, cutouts.
- `Audio`: preloaded clips, volume, mute.
- `Save`: progress and settings.
- `Main`: window, fixed-step loop on `System.nanoTime`, letterboxing.
- `Shots` / `Smoke`: dev tools.

Tests live in `test/diddys/`.
