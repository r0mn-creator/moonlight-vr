# VR Moonlight fork — Gaming + Productivity modes (living doc)

Status: **real repo started, 2026-09-15.** Public repo:
https://github.com/r0mn-creator/moonlight-vr — cloned from the private
`moonlight-android-xr-fork` (itself built on Sean Gilleece's
[Moonlight XR](https://github.com/Gilleece/moonlight-android-xr), which is
itself a fork of official Moonlight for Android). This doc is now also
mirrored into the repo at `docs/BRAINSTORM.md`; keep both in sync, or treat
the in-repo copy as canonical going forward and just leave this one as the
pre-repo history. Don't let it go stale silently — if a decision below gets
superseded, mark it rather than deleting it, so the reasoning isn't lost.

**First real commits**: a Gaming/Productivity tab bar added to `PcView`
(`activity_pc_view.xml`, both portrait and land, plus `PcView.java`) — the
existing PC grid (`pcGridAdapter`) stays shared and untouched under both
tabs, Productivity reveals a settings drawer underneath it (monitor stepper
1-3, 30/60fps toggle). **UI only — no real session parameters are sent yet**,
per the "start easy, plug in the real code later" instruction. Local project:
`/home/roman/Android/MoonlightVR`.

**Attribution note, resolved**: the inherited README carried Sean Gilleece's
personal Ko-fi link for the stereo/depth work he built. Kept it, but
relabeled clearly as supporting the original author rather than this fork,
and linked his actual repo (verified via `gh api users/Gilleece` — the git
commit author name "gilleece" was lowercase and didn't match his real GitHub
casing, `Gilleece`). Worth remembering as a general lesson: **verify a GitHub
handle via the API before publishing a credit link built from a git commit
author name** — casing/actual-username can differ from what git log shows.

**Confirmed mechanism for the "3D effect" to preserve** (from reading the
inherited README, not just prior assumption): monocular depth model (MiDaS
small) runs on-device on the decoded frame, upsampled and guided by the
colour frame, then a depth-image-based-rendering shader synthesizes a
separate per-eye view — entirely client-side, host never knows it's VR. This
directly confirms the earlier open question ("is the 3D effect per-surface
already?") is still open — haven't yet checked whether this pipeline assumes
a single decoded stream/texture or could run per-panel for Productivity's
up-to-three screens.

## The core idea

A free, open-source alternative to paid VR desktop apps (Virtual Desktop,
Immersed), built by extending Moonlight rather than starting from scratch —
because Moonlight already is a proven, responsive remote-gaming client, and
that responsiveness is exactly what a productivity-over-VR tool also needs.
Starting point: the user's existing fork, `moonlight-android-xr-fork`
(GitHub: r0mn-creator), which already runs on Quest 3 and already has a "3D"
rendering effect on the floating screen worth preserving.

**Two modes, one app, kept as separate top-level tabs:**

- **Gaming tab** — the standard Moonlight PC-selection layout, unmodified in
  spirit. One floating screen, framerate locked to match headset native
  refresh (90Hz+ on Quest 3), **zero monitor-management UI**. This tab is
  deliberately kept exactly as simple as stock Moonlight so the app never
  loses its "point at a PC, one screen, low latency, done" essence for anyone
  who only ever uses this mode.
- **Productivity tab** — same PC list underneath (see below), but opens into
  a different settings/session profile: multi-monitor support, and a
  framerate choice.

## Tabs are a settings profile, not two apps bolted together

The two tabs must **share a single paired-PC list** (one pairing, one cert,
one online/offline check) rather than forking PC discovery/pairing per tab.
A PC used for gaming today can be the same PC used for work tomorrow — a
home PC or a laptop, one physical monitor, either used from the couch with a
controller or from a desk with keyboard/mouse. So "Gaming" vs "Productivity"
is not a property of the PC, it's a **launch profile** chosen per-session:
which settings screen you see and what session parameters get sent (monitor
count, framerate) after you pick a PC that's already paired for both.

## Framerate

- **Gaming stays fixed**, matched to headset native refresh (90Hz+ on Quest
  3). Not user-configurable — this is what keeps input feeling responsive.
- **Productivity gets a user-facing 30/60fps toggle** for the desktop video
  stream itself. Let the user trade based on their own bandwidth — 60 keeps
  window drags/scrolling feeling native; 30 frees roughly double the bitrate
  headroom for spatial quality (sharper text) instead. Neither choice is
  "wrong," so don't pick one for the user.
- **Regardless of the 30/60 toggle, the VR shell around the content — head-
  tracking and reprojection — always stays pinned at full native refresh
  (90Hz+).** The desktop video content updating at 30 or 60fps never touches
  headset comfort; only the head-tracking rate does, and that's untouched by
  this toggle. This is the same split Virtual Desktop already uses in its own
  productivity mode — real prior art, not a novel risk.

## Why productivity content is a different encoding problem than gaming

Video codecs spend most of their bits on what *changes* between frames.
Gaming content repaints constantly (camera motion, particle effects) and
needs high bitrate to stay clean. Productivity content — slow window drags,
spreadsheets, reading, programming, no fast-paced motion — is far closer to
static, so it compresses much more efficiently at the same visual quality.
That's the whole basis for offering a lower framerate as a *choice* in
Productivity mode: giving up frames-per-second there costs far less
perceptually than it would in a game, because there's rarely fast motion to
smooth in the first place.

**Worth researching, not yet decided:** H.265/AV1 **Screen Content Coding**
(SCC) — an encoder mode built specifically for sharp text/UI edges rather
than camera-style motion, closer to what a spreadsheet or code editor needs
than the video-tuned encoder settings Moonlight/Sunshine use today. Not yet
confirmed how well-supported this is in the hardware encoders Sunshine
actually drives (e.g. NVENC) — a real feasibility item, not a given.

## Multi-monitor mechanism — does NOT require modifying Sunshine

Confirmed via research: **Sunshine does not need to be forked.** It already
ships a `global_prep_cmd` hook system — arbitrary host-side scripts that run
before a stream starts and undo themselves after it ends, with nothing
running the rest of the time. Existing community projects already use this
exact mechanism for on-demand virtual displays, matched to the client's
resolution/refresh rate, torn down on disconnect:

- https://github.com/ImStillBlue/sunshine-virtual-display
- https://github.com/itsmikethetech/Virtual-Display-Driver
- https://github.com/Cynary/sunshine-virtual-monitor

**Plan**: each monitor count becomes a separate Sunshine "app" entry (Sunshine
already treats "Desktop" as just an app), each with its own prep-cmd that
stands up N virtual displays. This means:

- All new engineering work concentrates in the **Moonlight client fork**
  (tabs, settings UI, floating-window placement, the add-monitor button), not
  in Sunshine.
- Anyone with a **stock, unmodified Sunshine install** only needs the VR
  client plus a small setup script that wires up the prep-cmd app entries —
  a much easier adoption story than asking people to run a patched host.
- If a genuinely seamless in-session experience later demands something
  Sunshine's hooks can't do, that's the point where forking Sunshine gets
  reconsidered — not before.

## "Add monitor" UX flow (first-launch walkthrough, as designed so far)

1. User opens the app on Quest 3, goes to the Productivity tab, picks a PC.
2. Opens into the desktop with just one floating screen (matching however
   many virtual displays the host already has configured — starts at one).
3. Just outside the floating screen, at the top, sit controls — one of them
   is **"Add monitor."**
4. Clicking it spawns a new floating screen next to the first. Repeatable.
5. The user can go into **Windows' own Display Settings** — Identify,
   rearrange — to configure the new monitors. This is not an app feature:
   there is no in-VR button for it. It's the user right-clicking the desktop
   shown on any one of the streamed panels, exactly as they would on a
   physical monitor, because that panel *is* an ordinary Windows desktop.
   The VR shell doesn't need to know Display Settings exists — the only new
   control the app itself introduces is "Add monitor."
6. Below each floating screen are **VR grab handles** so the user can also
   reposition the screens in 3D space independently of how Windows thinks
   they're arranged — two separate arrangement systems (OS-level topology,
   VR-space placement) that don't need to agree with each other.

**Mid-session "add monitor" is allowed to drop and reconnect** rather than
truly hot-add live. This was a deliberate simplification: it turns a hard
live-protocol problem (adding a video feed to an already-running stream)
into the same "reconfigure topology → brief reconnect" pattern a real
physical monitor already causes when plugged into a running PC. A short
reconnect flicker reads as normal, not broken.

## Device scope

**Target Quest 3 first**, not multi-headset from day one — it's what the
user owns, and it's Android-based, meaning this work builds directly on the
already-existing `moonlight-android-xr-fork` rather than starting a new
codebase.

## Keep from the existing fork

The current `moonlight-android-xr-fork` already has a "3D" rendering effect
on its floating screen that should be preserved. Since Productivity mode can
have up to three independent floating screens instead of Gaming mode's one,
this effect needs to work **per-screen** — worth checking, once real code
work starts, whether it's already built per-surface or currently assumes a
single screen in the scene (fixed position, single render target), which
would need generalizing to N independent panels.

## Input method for Productivity mode

Clarified against a reference image the user shared (three floating flat
monitors + a holographic virtual keyboard/trackpad, controller ray-pointed) —
the floating screens matched the existing design; the virtual keyboard did
not, so this was checked rather than assumed:

- **Now**: real keyboard/mouse only. Either wired straight to the PC (this
  app never sees it — it's just normal PC input, no different from any other
  desktop use), or connected to the headset and forwarded to the PC over the
  network. The second case is not new work: it's the same keyboard/mouse
  passthrough input path Moonlight already uses for Gaming mode. Productivity
  mode doesn't add an input pipeline, it just needs that existing path to
  keep working during a Productivity session.
- **Later (not yet started)**: a virtual, controller/hand-pointed keyboard
  and trackpad, for when no physical keyboard is on hand. Explicit decision:
  **reuse Horizon OS's own built-in system keyboard rather than building a
  custom one** — Meta's OS already renders one automatically whenever a
  standard Android text input field gains focus, already tuned for the
  headset's own pointer/hand-tracking. The design implication: whatever
  Productivity-mode UI eventually needs text entry should go through normal
  Android focusable text fields so the system keyboard triggers naturally,
  rather than intercepting input and drawing a custom on-screen keyboard.

## Passthrough — turned out to already exist

Before building anything, found that real Quest passthrough was **already a
complete, working feature** in the inherited codebase: `XrRenderer.java`
reads a `vrPassthrough` preference and passes it down through JNI to
`xr_renderer.c`, which sets the actual OpenXR `environmentBlendMode` to
alpha-blend (real passthrough, not a fake/simulated one) when the runtime
supports it. It was just a manual checkbox in Stream Settings, with no tie to
Gaming/Productivity at all, and defaulted OFF.

**Decision**: default it ON for new installs (`DEFAULT_VR_PASSTHROUGH =
true`), but leave it as the same sticky, user-controlled SharedPreference it
already was. Explicitly **not** forcing it by tab/mode — an earlier plan to
force passthrough on for Productivity and off for Gaming was scrapped
because it would silently override a user's own choice every single launch,
which contradicts "stays that way until a user decides not to use it."

**Also threaded a `productivityMode` flag** from PcView's tab selection all
the way to the Game/GameXR launch intent (`Game.EXTRA_PRODUCTIVITY_MODE`),
through `AppView` and `ServerHelper.doStart`/`createStartIntent`, and the two
paths that bypass AppView (PcView's own resume action, `ShortcutTrampoline`
for home-screen shortcuts — both default it to `false`/Gaming since they
have no tab context). Deliberately not used for anything yet — it exists so
the multi-screen Productivity renderer (next) knows which kind of session
it's in.

## Reusing the user's own Horizon Home space — not possible

Asked whether, when passthrough is off, the app could show the user's own
personalized Horizon Home space (their decorated environment) as the
background instead of a plain room. Researched rather than assumed:
**no public OpenXR/Horizon OS API exposes a user's Home Space to third-party
apps.** Horizon Home is the system shell's own environment, rendered only by
the OS itself. The only real options for a non-passthrough background remain
what the app already ships and controls itself (currently: a 360 photo, or a
plain dark room) — not literally borrowing the user's own Home decor.

## Productivity mode always opens the Desktop, not the tapped app

Sunshine's per-app launch commands often put the target software in its own
fullscreen mode (Steam launches into Big Picture, Unity into a fullscreen
player window, etc.) — the opposite of what Productivity mode is for.
Sunshine has its own long-standing convention for this: an app literally
named **"Desktop"** with no launch command, which just streams the desktop
as-is. **Whatever tile the user taps while in Productivity mode now redirects
to that PC's "Desktop" entry instead** (`AppView.resolveLaunchApp`) — they
land on the real desktop with their monitors, and open Steam/Unity/whatever
themselves, in its normal windowed form, same as they would sitting at the
PC. Falls back to launching the tapped app unchanged if the PC has no app
named "Desktop" (a user could have renamed or removed it).

## Multi-screen rendering — scoping notes (2026-09-15)

Explored `xr-renderer/xr_renderer.c` to scope the actual "3 screens" work.
Findings and decisions, in order:

**The renderer is single-screen today.** `XrCtx` holds one flat `screenPose`,
one `screenWidth`, one video swapchain — not an array. But the existing
stereo "3D effect" already proves the needed trick: it shows each eye a
different **half** of the same decoded video texture via
`XrCompositionLayerQuad.subImage.imageRect` (a real OpenXR sub-rect sample,
no shader work). Splitting into thirds instead of halves, one flat quad per
screen, is the same mechanism — not new architecture.

**Depth/stereo is Gaming-only now.** Given the frozen-Gaming/
Productivity-is-the-focus rule, Productivity's screens default to flat/mono
(no MiDaS pass) — shipped as a real Off/On toggle in the settings drawer,
**off by default**. MiDaS is already GPU-accelerated via TFLite's GPU
delegate (measured ~13.5ms/inference vs 183-265ms CPU, per an existing
in-code benchmark comment) but it's real cost for an effect flat desktop UI
barely benefits from. When a user opts in, the plan is to run the same
per-screen (3x the inference cost) — not yet built, opt-in only.

**Equal-thirds splitting is WRONG for mixed monitor orientations —
caught before writing code.** GameStream/Sunshine's protocol has no concept
of "multiple monitors" at all: it reports one combined image size and
nothing else. Windows' extended desktop is one virtual canvas; two identical
landscape monitors happen to make a clean 2x-wide rectangle, but add one
portrait monitor and the combined canvas becomes an irregular shape (e.g.
two 1920x1080s + one 1080x1920 monitor could report as roughly 4920x1920,
with the landscape monitors only filling part of that height). There is no
wire-level way to ask the stream which pixels belong to which monitor, so
naive equal-width splitting would crop/misplace content the moment monitors
don't match — which is the user's own real 3-monitor PC (2 landscape + 1
portrait), not a hypothetical edge case.

**Resolved with direct manipulation instead of configuration data entry.**
Rather than making the user pre-enter each monitor's resolution/orientation,
the fix is a full in-VR arrangement UI:

- **Per-screen rotate control**, positioned above each individual floating
  screen — the user rotates a screen by hand until its content displays
  correctly, which is how orientation gets corrected without the app ever
  needing to know real monitor geometry in advance. **Each press steps
  exactly 90°** (0→90→180→270→0…), not a free/continuous drag — matching
  the fact that a real monitor's rotation is always one of those four
  states, never an arbitrary angle.
- **A central control cluster above those**, containing an
  **Auto-arrange toggle**:
  - **Off**: free placement — each screen moved independently via the
    existing grab handles at the bottom of each screen (already built for
    the single-screen case, needs generalizing to N screens).
  - **On**: all screens snap side-by-side onto a shared curved "wall," and
    four option buttons appear — **Curve**, **Distance**, **Height**,
    **Space between**. Each button's slider is hidden until tapped:
    - Tap a button → its slider opens, appearing above that button.
    - Tap the same button again while its slider is open → slider closes.
    - Tap a *different* option button while a slider is open → the open
      slider is replaced by the newly-tapped one (only ever one slider
      visible at a time, no separate close-then-open step needed).
    - Tap anywhere else → whichever slider is open closes.
    - **Curve** — left: wall nearly flat (screens only slightly curved
      relative to each other); right: wall curves around toward just
      before the edge of the user's peripheral vision.
    - **Distance** — left: pushes the wall further away; right: pulls it
      closer.
    - **Height** — left: lowers the wall; right: raises it.
    - **Space between** — left: screens touch edge-to-edge; right: screens
      spread apart.
  - All of this is VR-space geometry (positions/curvature of floating
    panels), not anything sent to the host — purely a client-side placement
    system layered on top of however many screens are actually being
    rendered.

**Phase 1 shipped 2026-09-15, not yet verified on-device.** Added
`ctx->productivityMode` to `XrCtx`, threaded from `PreferenceConfiguration`
through `XrRenderer.nativeInit`. When set, `nativeEndFrame` submits 3 mono
`XrCompositionLayerQuad`s (equal-width columns of the same swapchain texture,
fixed shallow-arc default positions) **instead of** Gaming's single-screen/
stereo layers — wrapped in an `if/else` so Gaming's existing code path is
byte-for-byte untouched. No beam/handles/picker/background layer in this
mode yet — visual-only, per the phasing below. `Game.java` requests 3x the
normal stream width (naive equal-thirds) and force-disables depth mode for
Productivity sessions regardless of the depth toggle, since per-screen depth
isn't built. Compiles and installs; needs an actual device test connected to
a real PC to confirm it renders correctly.

**Top menu bar with a working Exit button, shipped 2026-09-15** (before
Phase 1 was even tested on-device — there's no Android back gesture inside
an immersive OpenXR session, so this couldn't wait). Built modularly on
purpose, since Phase 2's curve/distance/height/spacing controls need to
slot into the same bar later:
- `productivityMenuBarPose()` anchors the bar above the centre screen;
  `productivityMenuItemPose(index, count)` lays out slots left-to-right —
  adding a module later is raising `PRODUCTIVITY_MENU_ITEM_COUNT` and giving
  the new index somewhere to draw/hit-test, not a restructure.
- Exit is the only module so far: a Canvas-drawn door+arrow glyph
  (`XrRenderer.buildExitButton`), uploaded through the *existing*
  `nativeUploadPicker` env-button path (same swapchain Gaming's own env
  button uses — safe to share since the two modes never render at once).
- Real hit-testing, not just visual: `updateProductivityInput()` is a small
  function isolated from the single-screen grab/hover/picker system, reusing
  the existing `screenProject` ray-plane math and trigger-edge tracking.
  Pressing Exit sets a new `IN_EXIT_PRESSED` slot, which becomes an
  `InputListener.onVrExitRequested()` callback → `finish()` on the UI thread
  — the same path the existing keyboard quit shortcut already uses, which
  already returns to PcView for any VR session via the existing
  `EXTRA_RETURN_TO_PC_VIEW` mechanism.

**Invariant: floating screens always face the user, not a fixed world
orientation.** Already true in Phase 1 by construction —
`productivityScreenPose` toes each screen inward toward the origin (where
OpenXR's local space is centered, i.e. roughly where the user is at session
start), rather than placing every screen parallel to the center one. This
has to hold through Phase 2 too: whatever the curve/distance/height/spacing
sliders do to a screen's position, they need to re-aim its orientation at
the user as part of the same update, not just translate it while keeping
whatever rotation it already had.

**Phasing, to avoid building all of this at once**:
- **Phase 1** (next, not started): get 3 flat screens rendering at all, in a
  fixed default wall arrangement (sensible constant curve/distance/height/
  spacing, no interactive controls yet). This isolates the real technical
  risk — generalizing `XrCtx`'s screen state to an array of 3 and correctly
  slicing/submitting 3 composition layer quads — from the much larger
  arrangement-UI feature below it.
- **Phase 2**: the full control cluster (per-screen rotate, auto-arrange
  toggle, the four sliders, generalized free-placement grab handles) on top
  of a renderer already proven to work.
- **Also needed, not yet scoped in detail**: requesting a wide stream
  resolution from the host for Productivity sessions (currently
  `PreferenceConfiguration.width/height` is a single global value, same
  pattern as the `productivityMode`/`productivityDepth` flags already
  threaded through the launch intent).

## Naming

Renaming both the client and (if ever needed) the host fork is fine and
planned, not yet decided. Both Moonlight and Sunshine are GPL-licensed, so a
rename needs clear "based on / forked from" credit, and — since it's GPL —
keeping all changes open source too, which costs nothing since "free and
open-source alternative" was the goal from the start.

## Not yet decided / not yet discussed

- Final name for the client (and host tooling, if any).
- Exact SCC/codec feasibility on the encoders Sunshine actually drives.
- Whether the existing fork's 3D screen effect is per-surface already.
- The actual UI redesign for the tab system (in progress next).
- Whether a genuinely live mid-session hot-add is ever worth revisiting past
  the disconnect/reconnect approach.
