# Virtual Moonlight — Gaming + Productivity modes (living doc)

**Named 2026-09-16: the app is called Virtual Moonlight.** ("Moonlight VR"
was the working name throughout this doc's earlier sections — left as-is
below rather than rewritten, since the reasoning still applies, just under
the old name.) If a companion modified-Apollo host ever gets built, it's
named **Virtual Sunshine** to match. Changed via the `app_label`/
`app_label_root` resValues in `app/build.gradle` only — applicationId,
package names, and component names are untouched, so this was a zero-risk
display-name-only change.

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

**Confirmed for multi-screen PMode too (2026-09-16): the app list never
appears at all.** Once the user picks a PC in Productivity mode, there is no
intermediate "choose an app" step the way Gaming mode has one — Steam and
whatever else is in that PC's Apollo app list is skipped entirely, every
time. Picking a PC goes straight into the multi-screen Desktop session: the
client opens N concurrent launch requests (one per real display, capped by
`pmode_displays`) all against that same single "Desktop" app entry, each
tagged with a different `pmodeDisplay` value (see the Virtual Sunshine
section below) so the host knows which physical monitor to hand each
connection. From the user's perspective there's exactly one action —
"choose the PC" — and the full desktop across all configured screens is
just what Productivity mode *is*, not a launch choice among several.

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

## Real multi-monitor capture — bigger than a config toggle, decision paused

Asked whether matching Virtual Desktop's "show all my PC displays" needs
server-side modification. Checked rather than assumed: **Apollo's own
maintainers have stated true single-instance multi-display support is a
known, unimplemented gap** — "planned but the code written by Sunshine was
a total mess and hasn't gotten enough time to be cleared out." So this
isn't a config toggle away in Apollo as it exists today.

**The current working (no-code-change) path is heavier than earlier
turns assumed**: not "add 2 more app entries" in one Apollo config, but
**running 3 separate Apollo instances** on the host (`sunshine.conf`,
`sunshine_2.conf`, `sunshine_3.conf` — each its own process, port, log, and
state file), each instance's Display Device Id pointed at one of the 3
physical monitors. Our client would open 3 concurrent connections, one per
instance/port, placing each stream on its own VR screen. Real host setup
work, zero Apollo code changes.

**The alternative — one Apollo instance auto-detecting and streaming all
displays, matching Virtual Desktop's actual zero-config UX — doesn't exist
anywhere in this ecosystem yet.** Building it means patching Apollo's own
capture layer, a materially larger undertaking than this client fork, in a
codebase we don't own.

**Decision paused deliberately** (2026-09-16) — this is a big enough scope
fork to sit with rather than decide immediately. Revisit before doing any
more work that assumes one path or the other (e.g. the client-side "open N
concurrent connections" work only makes sense for the 3-instances path).

## Virtual Sunshine — the actual plan (researched, not guessed)

Read Apollo's real source (shallow-cloned to `/home/roman/Android/
ApolloResearch`, github.com/ClassicOldSong/Apollo) to find out exactly what
"real multi-monitor support" would require, rather than continuing to
theorize. Findings:

**The blocker is one global singleton, not an isolated flag.**
`proc::proc` (`process.h`/`process.cpp`) is a single global object that
simultaneously represents "the running app," "the selected display," AND
implicitly "the encoder" — referenced by name across `process.cpp`,
`video.cpp`, and `rtsp.cpp`:
- `video.cpp:1061`: `static encoder_t *chosen_encoder` — one encoder for
  the whole process.
- `video.cpp:1187,1389`: display selection writes straight onto the
  singleton (`proc::proc.display_name = display_names[display_p]`) — a
  global value, not something parameterized per session/request.
- `rtsp.cpp:401-483`: the launch handshake uses a **single-slot event**,
  not a queue — a second launch request while one is pending is silently
  dropped ("we currently only support a single pending RTSP session," per
  the code's own comment).
- `process.cpp`: `proc_t` has one `_app_id`; nothing suggests two `proc_t`
  instances coexisting today. Launching app B while app A streams goes
  through the same singleton that owns display selection — "one active
  app" and "one active display" are the *same* bottleneck, not two
  separate ones.
- No TODO/FIXME scaffolding found anywhere in `src/*.cpp,*.h` for
  multi-display — this is a clean, never-started gap, matching the
  maintainer's own "total mess" assessment rather than a half-built
  feature waiting to be finished.
- The network layer itself (`rtsp_server_t::bind()`) has no such limit —
  it accepts connections continuously. The bottleneck is entirely at the
  process/session layer above it, not sockets.

**Verdict: large, not medium.** Real single-instance multi-monitor support
means turning `proc_t` and the capture/encode loop into genuinely
per-session instances (or a small registry of them), plus replacing the
single-slot RTSP launch event with a real queue/map keyed by session ID —
a structural rewrite touching the three largest files in the codebase
(video.cpp 3065 lines, stream.cpp 2226, process.cpp), in a large C++
codebase we don't own, that its own maintainers already flagged as messy
in this exact area. There's also a real hardware ceiling this doesn't even
touch yet: concurrent hardware encoder sessions (NVENC etc.) are
GPU/driver-limited, so even a perfect software rewrite still needs the
host GPU to support 3 simultaneous hardware encode sessions.

### The actual plan: two phases, ship the tractable one first

**Phase 1 — Virtual Sunshine as orchestration, not a C++ patch.** Use the
*existing*, working single-session path three times over (process
isolation) instead of rearchitecting Apollo's core. Concretely, a setup/
launcher tool that:
- Detects the host's connected monitors and generates 3 Apollo config
  files (`sunshine.conf`, `sunshine_2.conf`, `sunshine_3.conf`), each with
  `output_name` pointed at one physical display and a distinct port.
- Starts/stops all 3 instances together as one unit — the user runs
  "Virtual Sunshine," not three separate services they have to remember.
- **Unifies pairing**: since our own tool generates all 3 configs, it can
  provision the same trusted-client certificate into all 3 instances'
  paired-clients state after a single pairing handshake in Virtual
  Moonlight, instead of the user pairing 3 times. Needs confirming
  Sunshine's pairing state format is simple cert-trust (likely, given it's
  file-based config) rather than something more coupled to a specific
  instance.
- Establishes a naming convention (e.g. "Desktop — Monitor 1/2/3") each
  instance's "Desktop" app entry uses, so the client can find the right
  stream on each port without per-PC manual configuration.
- **No changes to Apollo's C++ source at all.** This is tooling/config
  generation wrapped around existing, already-working multi-instance
  capability — the only "modification" is packaging and automating a setup
  a user could already technically do by hand today.

Client-side (Virtual Moonlight) work this phase actually requires: opening
3 concurrent NvHTTP/RTSP connections (one per port) instead of 1 — real
work in the Java networking layer, since `NvHTTP`/`MoonBridge` currently
assume one connection per stream — and mapping each of the 3 streams to
the correct VR screen by the naming convention above.

**Phase 2 — patch Apollo's capture/session core for true single-instance
concurrency.** Only worth it if Phase 1's friction (3 ports, 3 processes,
though not 3 manual pairings) turns out to be a real ongoing problem in
practice. This is the large rewrite described above, in someone else's
codebase, gated on GPU encoder session limits that exist regardless of how
good the software rewrite is. Not started, not currently planned as a
first step.

**Recommendation, given the research: start with Phase 1.** It delivers
the actual user-facing goal (3 real monitors in VR, one setup, one pair)
without touching a large unfamiliar C++ codebase its own maintainers
already called messy in this exact area.

### Correction (2026-09-16): this is a real patch after all, just narrowly scoped

The orchestration-only Phase 1 above was superseded the same day, once the
actual motivation was clarified further — worth keeping the reasoning
above rather than deleting it, since the singleton finding still directly
shapes what follows.

**The real constraint: users like the user himself may already have a
fully configured Sunshine/Apollo** (paired clients, app lists, custom
settings) and shouldn't have to delete and reinstall to try this. That
rules out "replace their host entirely" as the only option, and reframes
Virtual Sunshine as **two packaging personalities of the same underlying
patch to Apollo**, not two different codebases:

- **VS Full** — a complete replacement build (both Gaming and Productivity)
  for fresh installs or users willing to fully switch.
- **VS Productivity** — an in-place update to an *existing* Apollo/Sunshine
  install ("almost like a software update for Apollo or Sunshine"): adds a
  new Productivity tab to Apollo's existing config web UI
  (`confighttp.cpp`), and the new capture/session capability underneath
  it — without touching the user's existing config, pairings, or app list.
  Build and test this one first.

**Superseded (2026-09-16, see "Packaging simplified" below): these are not
two separate packaging efforts.** They're one build, applied two different
ways. Kept here because the underlying constraint (don't make users
reinstall/reconfigure) is still exactly right — only the "two personalities
= two build pipelines" framing was wrong.

**Explicitly reusing, not rewriting: the encoder and input-handling code.**
The user was specific about this. The patch's actual job is narrower than
the Phase 2 sketch above suggested — it doesn't need to generalize
`proc::proc` to support arbitrary N concurrent apps. It needs exactly one
new thing: **a second, parallel capture/session path specifically for
Productivity**, running alongside whatever the existing gaming path is
doing, reusing the same encoder wrapper and the same input/control
protocol code as-is. That's a materially smaller, more honest patch than
generalizing the whole session model — "one more specific kind of
session" rather than "rebuild session management to be generic."

### Implementation steps (2026-09-16), for VS Productivity specifically

Grounded in what the research actually found in Apollo's source
(`process.h/.cpp`, `video.cpp`, `rtsp.cpp`, `confighttp.cpp`,
`display_device.cpp`). One encouraging detail the research surfaced that
makes this smaller than it first looked: `nvenc_encode_session_t`/
`avcodec_encode_session_t` (video.cpp:315,392) are already **per-capture-
loop objects, not singletons** — the singularity comes from there only
ever being *one capture loop* today, not from the encoder session class
itself being single-instance. A second capture loop can create a second
encoder session using the exact same class, no encoder changes needed —
matching "reuse the encoder as-is" directly.

**Status update (2026-09-16): steps 1, 3, 4, 5 done; 6/7 turned out to need
less new code than planned; 2 and 10 still not started.** Real code is
pushed to `master` in the `virtual-sunshine` repo (commits `b664ad7c`,
`29ab3f0a`). Detail per step below; the biggest surprise was step 6/7 — see
those for why.

1. **New config surface.** Add `productivity_enabled` and a
   `productivity_displays` list (output names, reusing whatever
   `display_device.cpp` already enumerates) to `config.h`/`config.cpp` —
   the same pattern as the existing single `output_name`, just a list
   instead of one value. This is additive; existing config keys and
   values are untouched.
   **Done, named `pmode_enabled`/`pmode_displays` instead** (shorter, "PMode"
   is the shorthand we settled on in conversation) — same `video_t` struct,
   same `bool_f`/`list_string_f` parsing pattern as `output_name`.

2. **New confighttp tab.** A Productivity panel in the existing config
   web UI (`confighttp.cpp`) — likely just checkboxes over the detected
   display list (reusing `display_device.cpp`'s enumeration) writing to
   the new config keys from step 1, following whatever pattern the
   existing settings forms already use to read/write config.

3. **A new, separate session/state struct — not `proc::proc`.** A small
   `productivity_session_t` (or similar) that owns its own list of
   {display index, capture context, encoder session} — deliberately not
   touching `proc::proc.display_name` or `chosen_encoder`'s selection
   logic at all. This is the "new parallel path" instead of "generalize
   the singleton."
   **Done, simpler than planned: no new struct needed.** A `pmode_display`
   string field on `rtsp_stream::launch_session_t` (set from a new
   `pmodeDisplay` query param on `/launch`) flows straight into
   `stream::session_t`, and `video.cpp` keeps a small
   `unordered_map<display_name, capture_thread_async_ctx_t>` registry
   instead of a whole parallel session type. Less code, same isolation
   property — nothing here reads or writes `proc::proc`.

4. **Bypass `proc::proc` for capture display selection on this path.**
   The new productivity capture loop(s) read display targets from the
   step 1 config list directly and open their own `display_device`/DXGI
   duplication instances — never touching the global `display_p`/
   `display_name` the existing gaming path uses. The two paths can run
   concurrently without fighting over the same variable because they
   never share it.
   **Done.** `video::captureThread()` gained one new parameter, a pinned
   display name, used only on the PMode path — verified by re-reading
   every line that previously touched `proc::proc.display_name` or the
   Gaming-only display-switch mailbox event and gating each one behind
   "is this a PMode thread." Gaming's own call site is untouched byte-for-
   byte aside from now passing that parameter explicitly as empty (see the
   `std::thread` gotcha two paragraphs below).

5. **One encoder session per productivity display, reusing the existing
   class.** Each productivity capture loop instantiates its own
   `nvenc_encode_session_t` (or whichever backend is active), exactly the
   class the existing single-display path already uses — no encoder
   code changes. Real, unavoidable ceiling to test for regardless of any
   of this: concurrent hardware encoder session limits on the host GPU/
   driver. Needs a graceful failure path (clear error, not a crash) if a
   given GPU can't open N simultaneous hardware sessions.
   **Done for the "reuse the class" part** — `video::capture_pmode()`
   mirrors the existing `capture_async()` almost line for line, calling
   `make_encode_device`/`encode_run` exactly as Gaming does, just against
   its own per-display `capture_thread_async_ctx_t`. **Not yet done:** the
   graceful-failure path for a GPU that can't open N simultaneous hardware
   sessions — today a failed encoder open on the 2nd/3rd display just
   silently ends that one screen's session rather than surfacing a clear
   error. Untested on real hardware either way.

6. **Expose it through the existing app-launch mechanism, not a new
   protocol.** Add a reserved app entry (e.g. "Desktop — Productivity")
   to the NvHTTP `/applist` response, shown only when
   `productivity_enabled` is set. When a client launches *that specific*
   app, `nvhttp.cpp`'s launch handler routes to the new productivity path
   from step 3 instead of the normal `proc::proc` single-app launch.
   Virtual Moonlight already launches by a "Desktop" naming convention
   today (`AppView.resolveLaunchApp`) — this needs only a small update to
   look for the productivity-specific name when in Productivity mode, no
   new client-side protocol work.
   **Turned out to need no new app entry at all.** Re-read `nvhttp.cpp`'s
   actual `launch()` handler (lines ~1224-1294): if a second launch call
   targets the *same* `appid`/`appuuid` as the one already running, it
   takes the existing "resuming the same app" branch — no
   `proc::proc.execute()` call, no "an app is already running" rejection,
   just a `display_device::configure_display`+`probe_encoders()` call
   gated on `no_active_sessions` (so it only really runs for the first of
   the N launches). Since all N of Virtual Moonlight's PMode connections
   target the plain existing **"Desktop"** app (see the section above —
   no separate Productivity entry needed, the app list is skipped
   entirely), this branch already does the right thing for free. The only
   new code was reading the `pmodeDisplay` query param (step 1's job) so
   each of those N launches carries which physical display it's for.

7. **Allow N concurrent sessions for productivity launches specifically,
   without touching the existing single-slot gaming behavior.**
   `rtsp.cpp`'s `_session_slots` is already a `std::set<shared_ptr<
   session_t>>` — the collection type already supports multiple
   concurrent sessions in principle. Add a separate, small slot-tracking
   path for productivity-tagged launches (distinct from the existing
   single-slot `launch_event` gaming path) so up to 3 concurrent
   productivity sessions can be pending/active at once, while the
   existing gaming single-session behavior is completely unmodified.
   Client-side, this means Virtual Moonlight opens 3 separate launch
   requests (one per screen) against the same app entry — 3 ordinary
   Moonlight sessions running concurrently, not one session carrying 3
   video channels. No RTSP/video-channel protocol changes needed, just
   permission for more than one to exist at once for this specific case.
   **Also turned out to already work, same finding as step 6.**
   `_session_slots` being a `set` was already enough — nothing in the
   researched code path actually enforces "one session" at the RTSP/
   session-tracking layer for sessions of the *same* app; the "only one
   app running" restriction lives entirely in `nvhttp.cpp`'s launch
   handler (step 6), which the "resuming the same app" branch already
   sidesteps. No slot-tracking code needed. **Not yet verified**: this
   reasoning is grounded in reading the source, not in an actual 3-
   connections-at-once test — that's the real test once client-side work
   (opening N connections) exists.

8. **Input stays exactly as-is.** Per-session input/control handling
   (`input.cpp`) is scoped to `session_t` already for the single-session
   case today; extending to N concurrent productivity sessions should
   carry this along for free since it was never tied to the `proc::proc`
   singleton the way display/encoder selection was — worth confirming
   this assumption once real code work starts, not yet verified.

9. **Regression check, not just a new-feature check.** Since this is a
   patch to an install the user already relies on for gaming, the actual
   acceptance test is two-sided: existing gaming apps still launch and
   stream exactly as before (the untouched `proc::proc` path), *and* the
   new Productivity entry correctly drives 1, then 2, then 3 concurrent
   monitor streams without disturbing it.

10. **Packaging, once the above works**: VS Productivity ships as an
    in-place update to an existing Apollo/Sunshine install (replace the
    binary, preserve config/certs/pairings/app list — steps 1-2 are
    additive config keys, so an existing config file loads fine with them
    simply absent/defaulted). VS Full bundles the same patch into a
    complete fresh-install build. Same underlying code either way.

**Started 2026-09-16.** Public repo:
https://github.com/r0mn-creator/virtual-sunshine, local clone
`/home/roman/VirtualSunshine`. Cloned from Apollo directly (not a GitHub
"Fork" — same approach as Virtual Moonlight's own base), README credits
Apollo/ClassicOldSong and Sunshine/LizardByte properly, GPL-3.0 carried
over unchanged.

**Before any patch code: validating the build pipeline first.** Apollo's
own CI workflows aren't in their current public repo (confirmed via `gh
api` — 404 on `.github/workflows`, despite their own `docs/building.md`
documenting a "fork → activate workflows → trigger CI" remote-build flow).
Found why: `git log --all --full-history -- .github/workflows` shows the
whole directory was deleted at commit `da5a4e3e2` (2025-07-14) — recovered
the last working `ci-windows.yml` from its parent commit and adapted it
into `.github/workflows/windows-release.yml`, trimmed down (no docs/
coverage/test steps, no multi-OS `workflow_call` orchestration, just
build → package with CPack (NSIS installer + ZIP) → publish as a GitHub
pre-release). First run is building an **unmodified** Apollo through this
pipeline as a checkpoint — no Productivity capability yet, just proving
the pipeline itself produces a working Windows binary before adding any
patch complexity on top of an unverified foundation.

**Real constraint this whole sub-project runs under**: development happens
on Linux; Apollo's Windows capture code (DXGI) can only build/run on
Windows. Portable C++ (config, session logic, confighttp UI — the actual
patch surface per the plan above) can be compile-checked here in principle,
but end-to-end verification needs either this CI pipeline or the user's
own Windows PC. Same shipped-compiled-but-unverified posture as the Quest
haptics/audio work.

**Baseline build confirmed working, 2026-09-16.** First CI attempt found a
real bug in Apollo's own `master` (a stale `cfg.profile` reference in
`video.cpp` that no longer matches `video::config_t` — not something we
caused). Fixed by rebasing onto their latest tagged release, `v0.4.8`,
instead of bleeding-edge master. Second attempt found a second real issue:
the pinned Boost 1.89.0 release-asset SHA256 in `cmake/dependencies/
Boost_Sunshine.cmake` no longer matched what GitHub actually serves at
that URL — verified independently (downloaded and hashed it myself,
separate from the CI runner) that upstream's asset genuinely changed
content since v0.4.8 was tagged, not a network/security issue. Updated the
pin to the real current hash. Third attempt succeeded:
https://github.com/r0mn-creator/virtual-sunshine/releases/tag/build-5 —
`VirtualSunshine-installer.exe` and `VirtualSunshine-portable.zip`, both
unmodified Apollo (v0.4.8 + the two build fixes above), no Productivity
capability yet. This is the confirmed-working foundation the actual patch
(steps 1-10 above) now gets built on top of.

**Update (2026-09-16): steps 1, 3, 4, 5 are real, committed code** (see
status notes inline above); 6 and 7 needed no new code at all once the
actual `nvhttp.cpp` launch handler was read closely. **Still not started:**
step 2 (the confighttp Productivity tab — deliberately skipped for now,
scoped out when the user asked for "just the pieces that talk to PMode"),
step 9 (regression check — needs a real Windows box, not yet run), and
step 10 (packaging — see "Packaging simplified" below, which changes what
step 10 even means). Step 8 (input) still unverified, same caveat as
written above.

### A real bug found along the way: `std::thread` and default arguments

Worth recording since it'll bite again if a similar pattern gets reused.
`captureThread()` initially had the new pinned-display parameter as a
default argument (`= {}`) so the existing Gaming call site wouldn't need to
change. It compiled locally-reasoned-about fine but **failed real CI**:
`std::thread`'s constructor invokes its target indirectly through a stored
function-pointer type that includes *all* parameters the function has —
default arguments are a call-site convenience that doesn't survive that
indirection, so the Gaming call site (which relied on the default) hit a
`static assertion failed: std::thread arguments must be invocable after
conversion to rvalues`. Fixed by passing the argument explicitly at both
`std::thread{...}` construction sites and removing the now-misleading
default entirely. **Lesson**: never give a `std::thread` target function a
default argument and expect to skip passing it — pass everything
explicitly, always.

## Packaging simplified (2026-09-16): one build, two application methods

Re-examined the "VS Full vs VS Productivity" packaging split above after
the user asked a very direct question: *if updates are just drag-and-drop
new files into the folder and relaunch, can the same mechanism deliver
PMode to an existing install?* Checked the actual code rather than assuming:

- `platf::appdata()` (`src/platform/windows/misc.cpp:129`) returns
  `<the exe's own directory>/config` — config, `sunshine.conf`, `apps.json`,
  credentials, and `display_device.state` all live in a `config/` subfolder
  *next to the binary*, never inside anything a plain file overwrite would
  touch.
- The NSIS installer (`cmake/packaging/windows_nsis.cmake`) already runs an
  uninstall-before-install step on upgrade, but its own "delete
  $INSTDIR (config, cover images, settings)" prompt defaults to **No**
  (`/SD IDNO`) even when run silently/automatically — someone at Apollo
  already engineered this to be safe.

**Conclusion: this isn't two packaging pipelines, it's one build applied
two ways.**
- **Fresh install** (no existing Apollo/Sunshine/Virtual Sunshine on the
  machine): run the NSIS installer. It provisions the SudoVDA driver,
  Windows service, firewall rules, and gamepad driver — one-time setup a
  raw file copy can't do.
- **Update / add PMode to an existing install**: extract the portable ZIP's
  files directly over the existing install folder, leave `config/` alone,
  relaunch. Works whether the existing install is a previous Virtual
  Sunshine build *or* a plain vanilla Apollo install someone already had
  for gaming — since PMode is compiled into the same `sunshine.exe`/DLLs,
  dropping our files in is simultaneously "update Virtual Sunshine" and
  "add PMode to Apollo." No driver/service reprovisioning needed in this
  case because a working existing install already has all of that set up.

This retroactively answers what step 10 of the implementation plan above
actually needs to be: no separate "VS Productivity build," just clear
instructions (and eventually a README section) explaining that the
portable ZIP is the update/upgrade path and the installer is the
fresh-install path, both built from the exact same source.

**One decision this forced**: our `CMakeLists.txt` still declared
`project(Apollo ...)`, meaning our own installer silently identified itself
*as Apollo* — same install directory, same registry entry — so installing
it would have silently upgraded-in-place over a user's real Apollo install
without asking. Asked the user to choose between keeping that (free
silent-upgrade behavior, but risky/surprising) or rebranding now (safer,
but loses that automatic behavior for anyone still on plain Apollo — they'd
need the drag-and-drop path above instead, or a future explicit migration
step, to get PMode). **Chose to rebrand now.** Renamed the CMake project to
`VirtualSunshine` (commit `29ab3f0a`) — own install directory
(`C:\Program Files\VirtualSunshine\`), own registry entry, updated
publisher metadata and the one leftover "Apollo" string in the installer's
component descriptions. The actual binary filename (`sunshine.exe`) and
internal folder layout were deliberately left unchanged, since the
drag-and-drop update mechanism above depends on that layout matching an
existing Apollo install, not on what the top-level folder or registry
entry is called.

## Client can't actually use PMode yet — moonlight-common-c is single-connection by design

Asked whether Virtual Moonlight needs updates to use the Virtual Sunshine
PMode work. Checked the current client code: `Game.java:249` still does
`prefConfig.width *= 3;` — today's "Phase 1" multi-screen rendering opens
**one** NvHTTP/RTSP connection, asks for a 3x-wide stream, and just crops
the single resulting texture into 3 columns
(`productivityScreenPose`/etc. in `xr_renderer.c`). That's the literal
mechanism behind the "center screen has content, two sides are black" bug
from earlier — a single Apollo capture session only ever contains one real
monitor, no matter how wide the client asks for. PMode's actual design
(N separate connections, each tagged `pmodeDisplay=<name>`, each with its
own capture/encode pipeline server-side) needs a genuinely different client
architecture, not a patch to the existing one.

**Bigger finding while scoping that work: `moonlight-common-c` (the
client's core streaming library, `app/src/main/jni/moonlight-core/
moonlight-common-c`) only supports one connection per process, by explicit
design, not by accident.**
- `Limelight.h:532` (`LiStopConnection`) and the doc comment on
  `LiInterruptConnection` state outright: *"it is not safe to start
  another connection before the first `LiStartConnection()` call
  returns."* A documented contract, not a gap.
- `Connection.c`, `VideoStream.c`, and `AudioStream.c` each hold their
  sockets, threads, decryption contexts, and decode queues as `static`
  file-scope globals — every subsystem the library has.
- This is much bigger than Apollo's `proc::proc` singleton: that was our
  own fork's problem, in code we already own. This is baked into a
  third-party C library nearly every Moonlight/Artemis client depends on.

### Decision (2026-09-16): multi-process client, not a moonlight-common-c rewrite

Presented three options: (a) run N Android processes, each with its own
isolated moonlight-common-c instance, and composite N cross-process video
surfaces into one VR scene; (b) keep one connection, add host-side
display-switching, and treat "3 monitors" as "one live screen you can
switch, not 3 simultaneous ones" — a real scope reduction; (c) rewrite
moonlight-common-c itself to thread an explicit connection context through
every subsystem — biggest and riskiest, in code we don't own. **Chose (a),
multi-process.** The key property that makes it work: process-level
globals are automatically per-process on Android/Linux, so N processes
running the *same unmodified* moonlight-common-c gives N fully independent
"instances" for free — zero changes needed to the third-party library. All
the real engineering is in Android app architecture (Services, AIDL,
cross-process `Surface` handoff), not inside fragile C networking code.

**Why this is actually tractable, confirmed by reading the current
renderer**: `xr_renderer.c` already uses OpenGL ES with an external OES
texture (`GLES2/gl2ext.h`, `ctx->oesTexture`), and the existing single-
connection path already does exactly the pattern the multi-process design
needs, just once instead of N times — `XrRenderer.java:225-251`:
`nativeGetTexId()` creates a GL texture → `new SurfaceTexture(texId)` →
`new Surface(surfaceTexture)` → that `Surface` is handed to
`MediaCodecDecoderRenderer`'s decoder. A `Surface` is `Parcelable` and can
cross a Binder/AIDL boundary to a different process — that's the standard
Android mechanism (used by `SurfaceView`, `VirtualDisplay`,
`MediaProjection`) that makes "decoder in process B, GL texture consumed
in process A" a well-trodden path, not a novel one.

### Concrete plan

1. **N background Services, one process each.** Declare lightweight bound
   `Service`s in the manifest with `android:process=":pmode_screen1"` etc.
   (no UI — the XR activity stays the single visible process). Each hosts
   exactly one moonlight-common-c connection, driven by the *existing*,
   unmodified `NvConnection`/`MoonBridge` Java wiring — running inside an
   isolated process is what isolates its globals, not a code change.

2. **AIDL interface per screen service.** `connect(Surface videoSurface,
   String host, String pmodeDisplay, StreamConfig config, ICallback cb)`,
   `disconnect()`, plus the mouse/keyboard subset of `MoonBridge` actually
   needed for desktop use (no gamepad passthrough needed here). The
   callback interface reports connection stage/termination/stats back to
   the main process for the existing debug overlay.

3. **Main process creates N `SurfaceTexture`+OES-texture pairs** (today's
   single-texture setup, made into an array), and for each configured
   display, binds the matching `:pmode_screenN` service and hands over its
   `Surface` plus connection params via AIDL `connect()`.

4. **Renderer**: replace column-cropping one swapchain with sampling N
   independent OES textures, one per screen quad — each updated via its
   own `SurfaceTexture.onFrameAvailable`/`updateTexImage()`, routed through
   JNI per screen index instead of the single current call.

5. **Input routing**: the existing ray/pointer hit-test already knows
   which quad is being pointed at (`updateProductivityInput`); route mouse/
   keyboard events to *that* screen's bound service via its own AIDL call
   instead of the single global `MoonBridge.send*` today. Whichever screen
   was last clicked "owns" keyboard focus, same as a real multi-monitor
   desktop.

6. **Audio: exactly one screen is the audio owner** (e.g. index 0, or
   whichever was last focused) to avoid decoding and mixing N overlapping
   desktop-audio streams. The other N-1 connections request audio off or
   simply never wire their decoded audio to an `AudioTrack`.

7. **Lifecycle**: bind all N services together on entering Productivity
   mode, `disconnect()` + `unbindService()` all of them together on Exit
   (same button, extended) — each service's `onDestroy()` should also
   defensively call `LiStopConnection()` for its own process.

8. **Discovery dependency on the server side (not yet built)**: the client
   needs to learn the host's `pmode_displays` list (count + names) to know
   how many services to spin up and what to tag each `pmodeDisplay` with.
   Nothing currently exposes this — needs either a new NvHTTP endpoint or
   folding it into an existing response (`/serverinfo`). Cross-cutting with
   the still-not-started confighttp step (step 2) on the Virtual Sunshine
   side. Hardcoded `PRODUCTIVITY_SCREEN_COUNT` stays as a fallback/default
   until this exists.

9. **Regression check**: Gaming mode keeps using the existing in-process
   single connection completely unchanged — only Productivity mode routes
   through the new N-service architecture. Confirm Gaming still launches/
   streams exactly as before once this lands.

**Steps 1-2 shipped, 2026-09-16 (commit `9e1c2b61`), real compile-verified
code:**
- `PModeScreenService1/2/3` (`android:process=":pmode_screenN"`) + a small
  AIDL contract (`IPModeScreenService`/`IPModeScreenCallback`) — all real
  logic lives in `PModeScreenServiceBase`, the 3 subclasses exist purely to
  give each screen its own manifest component/process.
- The Service reuses the *existing* `NvConnection`/`AndroidAudioRenderer`
  wiring as-is (per the plan's own "reuse, don't rewrite" spirit).
  `MediaCodecDecoderRenderer` needed one real change: it always tried to
  own an `XrRenderer` (a full OpenXR session — impossible in a background
  Service), so it gained a `setRenderTarget(Surface)` overload for decoding
  straight into an externally-supplied `Surface` with no XR session
  involved, plus null-safety on the two places it touched `activity`
  directly. Verified via a real `./gradlew :app:compileRootDebugJavaWithJavac`
  that Gaming's existing `SurfaceHolder` path still compiles unchanged.
- `pmodeDisplay` now flows end to end: a new field on `StreamConfiguration`
  → appended to `NvHTTP`'s `/launch` query string → matches the
  `pmodeDisplay` param Virtual Sunshine already reads server-side
  (`b664ad7c`). Empty/null is a no-op, so Gaming's launch request is
  byte-identical to before.
- One real bug caught by actually compiling instead of just reading the
  source: named a helper method `notify()` inside an anonymous
  `NvConnectionListener` (itself an `Object` subclass) — collided with
  `Object.notify()`, a classic Java gotcha. Renamed to `notifyCallback()`.

**Step 3 shipped, 2026-09-16 (commit `c9c82afe`), verified on real hardware
this time.** `xr_renderer.c` now has `productivityOesTexture[3]` (one per
screen) instead of one shared decoded frame column-cropped into 3 quads.
`renderVideoFrame()` branches early for `productivityMode`: same swapchain,
same `imageRect` per-quad layout the composition-layer code already used
(untouched), but each screen's OWN texture gets blitted into its own
viewport column instead of one texture's columns being re-sampled. One real
bug caught while writing this: the function starts a GPU timer query
unconditionally near the top, and PMode's early return skips the matching
end call — an unmatched `glBeginQuery` would have broken every later timing
query. Fixed by gating the query start on `!productivityMode` too, since
PMode doesn't need per-frame GPU timing yet anyway.

`XrRenderer.java` creates the N `SurfaceTexture`/`Surface` pairs (only in
productivity mode), each with its own pending-frame counter and listener,
feeding `nativeUpdateProductivityTexture()` once per screen per frame. New
`getProductivityInputSurface(index)` for `Game.java` to hand to the bound
services — not called yet, since nothing binds the services or drives
`connect()` yet. That's the rest of step 4.

**This is the first change this session actually verified beyond a
compiler** — with a real Quest 3 connected (`2G0YC5ZFCM01G3`):
`./gradlew :app:externalNativeBuildRootDebug` (native code, all 4 ABIs),
`:app:assembleRootDebug` (full APK), installed and launched on-device with
no crash, native library loaded, reached `PcView` normally, and the 3
`PModeScreenService` entries confirmed present in the actual packaged
manifest. Doesn't exercise PMode streaming itself (nothing calls
`connect()` yet) - that needs `Game.java`'s mode-branching connection
lifecycle next, plus a live Virtual Sunshine host to actually test against.
A real unknown alongside it: Quest 3's Snapdragon XR2 Gen2 concurrent
hardware video decoder session limit hasn't been checked. 2-3 simultaneous
`MediaCodec` decode sessions is very likely fine on this hardware, but
unverified.

## Native Quest 3 feel — haptics and spatial audio shipped

Asked what "feels like a native Quest 3 app, built by a pro VR dev" actually
means concretely. Prioritized list: haptics, fixed foveated rendering
(offsets the exact GPU cost 3 screens just added — not yet done), spatial
audio, a real first-launch flow (not yet done). Passthrough, hand tracking,
and guardian handling were already covered or free from the OS.

**Haptics**: a new `XR_ACTION_TYPE_VIBRATION_OUTPUT` action
(`ctx->hapticAction`), bound on every controller profile — not gated behind
the `full`/`simple` binding split like most inputs, since any profile with a
trigger has a haptic motor. `fireHaptic(ctx, hand)` is a small reusable
helper, wired to the exit button for now; Phase 2's other buttons get it for
free by calling the same function.

**Spatial audio**: there's only one audio stream for the whole desktop
(mixed before it ever reaches the client), so this positions the *whole*
mix at one point — the centre screen — rather than attempting per-window
audio the stream doesn't support. `updateProductivitySpatialAudio` computes
pan (head-relative azimuth to the centre screen) and gain (distance
falloff, referenced to the default screen distance) each frame, riding
along on the existing `nativeUpdateInput` channel (two new slots,
`IN_AUDIO_PAN`/`IN_AUDIO_GAIN`) rather than adding a new JNI call.
`AndroidAudioRenderer` applies it as a balance control directly on the PCM
buffer before `AudioTrack.write()`.

**Correction, same day: both extended to Gaming mode too.** Initially built
Productivity-only by default, matching the frozen-Gaming rule everywhere
else this session — but haptics and spatial audio are basic VR platform
features, not Productivity-specific extras, and the user called that out
explicitly (the one case this session where Gaming was deliberately
touched, per its own "unless I specifically tell you" exception). Haptics
now also fire on grabbing a screen handle and on any newly-pressed mouse
button in Gaming's existing single-screen interaction system.
`computeSpatialAudio` was generalized to take an explicit screen pose and
reference distance rather than hardcoding Productivity's centre screen, so
Gaming calls it with its own movable `screenPose`. The Java-side identity-
transform optimization (pan=0/gain=1 skips processing) needed no changes —
it was already value-specific, not mode-specific.

Shipped 2026-09-16. Compiles clean (Java + native); **not built into an APK
or installed** — no device available to test against at the time, so this
needs a real on-device pass before being called done.

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
