# Development log

Where shipped work, architecture decisions, and technical history get
recorded — what was built, why, and what's still unverified. Distinct from
`BRAINSTORM.md`, which is for actual brainstorming/ideation sessions, not a
running project record.

## UI/UX reset: environment picker removed, one shared top bar for both modes

Decided to stop Productivity Mode work for now and start a general app
UI/UX pass instead. First target: the in-VR "environment picker" (a grid
letting Gaming mode pick Passthrough / a black void / one of four bundled
360° photos as the backdrop). Removed entirely — the session is now
hard-locked to Passthrough. Along the way, found the picker's own "open"
icon secretly shared its swapchain/texture with Productivity Mode's
exit-door button (the code literally commented "reuses the env-button
swapchain slot") — flagged by the user as exactly the wrong way to build
menu UI.

**The replacement is one shared top-bar module, not a Gaming-only feature.**
Direct instruction: "The ONLY difference between Gmode and PMode is how
they render and connect to the pc and how many screens. Other then that
they both have the same top center menu module." PMode's existing bar
(`productivityMenuBarPose()`/`productivityMenuItemPose()`) was already the
right shape — modular item count, anchored above the screen(s) by its own
comment — so it was generalized rather than replaced: `topBarPose(ctx)` now
branches on `ctx->productivityMode` for the anchor (above Gaming's single
resizable screen, or above PMode's centre screen), `topBarItemPose()` lays
out items identically either way, and one shared hit-test function
(`updateTopBar()`, native) is called from both modes' input paths — Gaming
gained the exit/brightness icons it never had before, PMode's old bespoke
bar and its borrowed texture slot are gone. The bar's own texture is
renamed `topBarSwapchain` and now holds the whole icon strip, not one
borrowed cell.

**Two items today: Exit and a passthrough brightness slider.** Tapping
the brightness icon opens a track+thumb above it; dragging is absolute
position (not relative delta, unlike the screen move/resize handles),
right = full passthrough (default), left = full black, persisted to a new
`vr_passthrough_level` preference and restored at session start. The dim
effect is a full-surround black sphere
(`XR_TYPE_COMPOSITION_LAYER_EQUIRECT2_KHR`, `radius = 0` per spec = infinite
sphere) submitted behind everything else, alpha = `1 - level`, re-uploaded
only when the value actually changes. The equirect extension was already
detected/enabled in this codebase (`ctx->equirectSupported`) but had never
actually been used until now. Exit needed zero Java-side changes —
`Game.onVrExitRequested()` had already been written defensively to handle
non-Productivity sessions even though nothing outside PMode could reach it
before.

**Icons are user-supplied PNGs, not drawn in code.** Exit is a plain white
door/arrow glyph; brightness is a two-tone glyph (a white rounded rect
partly occluding a black bracket) depicting a bright screen against a dark
room, matching what the slider actually does. Both live at
`res/drawable-nodpi/ic_topbar_*.png`, decoded and drawn into one wide
strip bitmap in `XrRenderer.buildTopBarArt()`, uploaded as a single
texture. Exit is index 0, which the existing placement math already put on
the left — confirmed, no change needed. A soft, mostly-see-through pill
(`BlurMaskFilter`, ~31% opaque black, baked into the same texture behind
the icons) was added after the user pointed out plain white icons would
vanish against a bright wall; alpha/blur radius still needs a real
on-device check against an actual wall.

**New app icon**: the placeholder purple adaptive-icon background
(`ic_launcher_background`) was replaced with a proper adaptive icon built
from a user-supplied pinwheel image — background color matched to the
icon's own grey, foreground scaled to the standard safe zone, plus a
flattened legacy icon for older launchers. The bundled wordmark text was
flagged as unreadable at real 48px launcher size (confirmed with an
upscaled mockup) but kept as-is at the user's call.

Native (`externalNativeBuildNonRootDebug`) and the full app
(`assembleNonRootDebug`) both build clean; installed to the connected
Quest 3 and launches to PC-select with no crash. **Not yet verified**: the
actual in-VR behavior (bar placement/legibility in both modes, the slider
drag feel, whether the dim level reads right) needs a real PC-connected
session, which wasn't available while building this.

One real mid-session mistake worth recording: a large native edit (the
picker removal) was left half-done — a whole render block was deleted but
a second block still referencing the deleted identifiers wasn't caught
before reporting the task in progress. Found by grepping for the deleted
identifiers and confirming the file still compiled with
`externalNativeBuildNonRootDebug`; fixed by finishing the removal. For
native/compiled code, "I removed X" is a claim to verify with a real
build, not report from having read the diff.

## Third top-bar module: screen curvature, made live-adjustable in-VR

Added a curve slider to the top bar, same pattern as brightness: tap the
icon, drag right for a tighter (~180°) wrap, left for flat. This exposed a
real pre-existing constraint - `curvature` was already a feature (a flat
Settings-only seekbar driving a cylinder-vs-quad screen layer,
`XR_TYPE_COMPOSITION_LAYER_CYLINDER_KHR`), but it lived entirely as a
per-frame Java parameter threaded through `nativeUpdateInput`/
`nativeEndFrame`/`updatePlacement`. Made it native-owned live state instead
(`ctx->curveAmount`, mirroring `ctx->passthroughLevel`) so the in-VR slider
has something to actually mutate - removed the parameter from all three
function signatures, seeded from the existing `seekbar_vr_curvature`
preference at session start (`nativeSetCurvature`, called once, same as
`nativeSetPassthroughLevel`), written back to that *same* preference key
(not a new one) so the flat Settings screen and the in-VR slider stay one
source of truth.

**One real wrinkle, worth the note**: `updatePlacement()`'s existing
curvature-seed formula also resets screen position/pose (it's the same
branch that places the screen on first launch or when the flat Settings
distance/size sliders move). Dragging the new in-VR curve slider must
never snap the screen back to the default position, so it writes
`ctx->screenRadius` directly instead, using `ctx->lastDistance` (already
tracked every frame regardless) rather than going through that reseed
path.

**The slider itself is now shared infrastructure, not brightness-specific.**
`ctx->sliderOpen` (bool) became `ctx->openSlider` (an item index, or
`TOPBAR_NO_SLIDER`) plus `ctx->grabSliderTarget` (a snapshot taken when a
drag starts, so it keeps controlling the same value even if the other hand
touches a different icon mid-drag) - the track/thumb chrome and the
open/close/drag state machine are the same for both modules, just
retargeted by index. Adding a fourth slider-based module later is a
two-line change (a new index constant, a case in `applySliderValue()`/
`markSliderDirty()`), not a new subsystem.

Full app build clean, installed and launches with no crash. Same caveat
as everything else in this doc: the actual on-device drag feel hasn't
been checked yet.

## Fourth top-bar module: keyboard toggle - a tap, not a slider

User's own request going in: "let's go big" on adding a way to bring up
the on-screen keyboard - but with a real insight that made this small
instead of a from-scratch 3D keyboard: Quest's system IME can appear as an
overlay inside an immersive session on its own once a key-event-consuming
view has focus, and this app already has exactly that. Found `Game.java`
already implements `GameGestures.toggleKeyboard()` (used by the existing
flat/touch-mode gesture), which just calls
`InputMethodManager.toggleSoftInput()` - and `onKeyDown()`/`onKeyUp()`
already route through `KeyboardTranslator` into the same host-keyboard-
event pipeline a physical Bluetooth keyboard uses, regardless of Gaming vs
Productivity mode. So the whole feature is: a 4th tap-only bar icon (no
slider - mirrors Exit's pattern exactly) wired to a new
`IN_KEYBOARD_TOGGLE` slot → `InputListener.onVrKeyboardToggleRequested()`
→ `runOnUiThread(this::toggleKeyboard)` (hopped to the UI thread, since
`toggleKeyboard()` normally only runs from a touch-gesture callback, not
the render thread this fires from). Zero new text-input plumbing.

Icon started as a procedural placeholder, then replaced same day with
user-supplied art (`ic_topbar_keyboard.png`) - same cleanup as the curve
icon needed: source was a near-white light grey (`244,244,244`) with soft
noisy edges, recolored to solid white and denoised to match the rest of
the set.

**Real unverified assumption, flagged explicitly**: that Quest's system
keyboard actually renders as a visible, legible overlay *within* this
app's immersive OpenXR session when toggled this way, not just in flat
2D activities. This is standard behavior for well-behaved immersive
Android/Quest apps in general, and nothing here should prevent it, but it
hasn't been seen working in this specific app yet - first thing to check
alongside the rest of the top bar.

## This build is Gaming-only - Productivity Mode moved to a private repo

Decided to split development: Productivity Mode continues in a new
private repo, `r0mn-creator/moonlight-vr-pmode` (a full-history copy of
this repo, made by mirror-pushing rather than GitHub's Fork feature, which
won't fork a repo into the account that already owns it - local clone at
`/home/roman/Android/MoonlightVR-PMode`). This public repo's `master`
stays Gaming-only for now.

Scope was deliberately kept small: "you only need to remove what the user
can see... Gmode works just fine." So only the user-visible surface was
touched - the Gaming/Productivity tab bar on PC-select is hidden
(`activity_pc_view.xml`'s `modeTabBar` set to `visibility="gone"`,
`PcView.java`'s `initializeModeTabs()` call commented out) - and nothing
underneath was removed. All the PMode code (multi-process
`PModeScreenService`s, AIDL, native `productivityMode` render/input
paths) is still physically in this repo, just permanently unreachable
since nothing can set `productivityMode = true` anymore. Trivially
reversible when PMode is ready to merge back.

The shared top-bar work (Exit, brightness, curve, keyboard) shipped here
too, deliberately - none of it is Productivity-specific, it was built to
serve both modes identically from the start.

## Fifth top-bar module: 3D effect toggle, live in both directions

Same "on/off, no slider" shape as Exit and the keyboard button, but this
one turned out to have a real architectural wrinkle worth recording.

**The setting itself**: Productivity Mode used to have its own Depth
Off/On buttons in the (now-hidden) drawer panel - a per-launch toggle, not
a persisted preference, and Productivity-specific. Promoted that concept
into a real, simple, mode-agnostic Settings checkbox
(`checkbox_vr_depth_effect`, "3D Effect", on by default) that both modes
now share - separate from the existing `list_vr_depth_source` dropdown,
which stays as the advanced/debug test-pattern picker (flat/ramp/blob/
eyetest/shifttest) it always was.

**Why a live toggle isn't "just another flag the render reads"**: the
mono-vs-stereo decision was baked into the actual OpenXR swapchain's size
at session start (`videoWidth * 2` for stereo, `videoWidth` for mono, in
`initSwapchain()`) - not something a per-frame flag can change, since you
can't resize a live swapchain. Fix: always initialize as at least
`DEPTH_MODE_MODEL`-capable (never truly `OFF`) so the swapchain is always
allocated stereo-sized, regardless of the saved toggle state - this costs
nothing new in practice, since the existing default was already "model"
for virtually everyone. The live toggle then only has to flip two much
lighter things each way: `ctx->depthEffectOn` (a new native flag that
zeroes `separation` before `renderVideoFrame()`, collapsing the whole warp
to a flat pass in one place rather than gating occlusion/upsample/
disparity separately), and the Java-side MiDaS inference thread's actual
running state (saves the ~13.5ms/frame GPU cost when off).

**Second wrinkle**: `startDepthThread()`/`stopDepthThread()` were written
assuming a start-once/stop-once lifecycle per session (`stopDepthThread()`
blocks on `join()`, and `depthExit` was never reset back to `false`
anywhere). The live toggle needs both to run repeatedly within one
session, and `stopDepthThread()`'s blocking join can't run on the render
thread without stalling the whole VR view for however long it takes.
Fixed with `reconcileDepthThread()`: a small dedicated worker thread that
serializes start/stop calls (only one in flight at a time - both touch the
same depth EGL context, so they can't be allowed to race), and
`startDepthThread()` now resets `depthExit`/`depthPending`/`depthBusy`
before spinning up a fresh thread. The visual change itself
(`nativeSetDepthEffect`) is applied immediately regardless of how long the
thread reconciliation takes, so the toggle always *feels* instant even
though the GPU-cost saving lags a moment behind it.

Icon started as a placeholder pair (filled "lenses" for on, outline-only
for off), then replaced same day with user-supplied art (an isometric "3D"
block, already clean - full opacity at 255 white, no denoising needed
unlike the last few icons). Only one asset was provided, so the "off"
state is the same art at 45% alpha rather than a separately fabricated
variant.

## Ambient glow v1: the dim sphere tints itself from the screen's colour

User's idea: like bias lighting behind a real TV, the darker the room gets
(brightness slider), the more the surrounding passthrough should pick up
ambient light coloured like whatever's on screen, instead of just fading
to flat black.

**v1 scope, deliberately simple**: one averaged colour for the whole
frame, not per-edge/positional matching. `computeGlowColor()` reuses the
existing box-filter downscale technique (`DOWNSCALE_FRAGMENT_SRC`, already
used for the depth model's input) at a tiny `GLOW_TEX_SIZE` (8x8) target,
own program/FBO since the depth model's version only exists in
`DEPTH_MODE_MODEL` sessions and glow needs to work with the 3D effect off
too. Read back and averaged in C into `ctx->glowR/G/B`, which the
passthrough dim sphere's texture now uses instead of hardcoded black - the
existing alpha curve (`1 - passthroughLevel`) already means "more visible
as the room darkens" needed zero new logic, just a new colour underneath
it.

Only runs while the dim sphere is actually visible
(`passthroughLevel < 0.999`), so the default full-passthrough experience
costs nothing extra. Re-uploads the tiny dim texture every frame while
visible (video content changes every frame; the texture is 4x4, cheap
regardless).

**Follow-up same day: a dedicated on/off toggle, contextual to the
brightness slider.** User: "we need a button to toggle on and off glow...
This toggle appears and disappears with the slider that darkens the room."
Not a topbar module (it isn't part of the fixed icon row) - its own small
icon, positioned next to the brightness slider's track specifically
(`topBarGlowTogglePose()`, built off `topBarSliderPose(ctx,
TOPBAR_BRIGHTNESS_INDEX)` - never appears next to curve's slider, since
glow has no meaning there), only hit-tested while that slider is open.
Turning it off skips `computeGlowColor()`'s per-frame downscale entirely
(not just the visual result) and the dim sphere falls back to flat black
immediately rather than freezing on a stale colour. Persisted
(`vr_glow_enabled`, on by default), same restore/persist shape as the
other toggles. Icon is user-supplied art with two states - a glowing
white outline for on, a plain grey outline for off - recreated once after
the first version's blur wasn't pronounced enough to read clearly.

**Next evolution (discussed, not started)**: true room-scale glow, where
light would bounce off the user's *actual* walls using Quest's scanned
room geometry rather than a generic surrounding sphere. Researched what
that would take:
- APIs: `XR_FB_scene` + `XR_FB_spatial_entity*` for anchors/planes,
  `XR_META_spatial_entity_mesh` for the actual triangle mesh (mesh is what
  you'd need for real bounce/reflection math - the semantic plane API only
  gives crude wall/floor rectangles).
- Hard prerequisite: only works if the user already ran Quest's own
  system-level Space Setup room scan - this app cannot trigger a scan
  itself, only query whatever's already there. Must degrade gracefully
  when absent (i.e. this can only ever be an optional layer over the v1
  sphere effect, never a replacement for it).
- Needs the `com.oculus.permission.USE_SCENE` manifest permission plus a
  runtime consent prompt.
- Considered stable/shipped for third-party apps as of 2025-2026, not
  beta-flagged.
- Real lift: this app currently uses zero spatial-entity APIs (just
  passthrough + controllers), so this is closer to "new subsystem" (mesh
  query, LOD/simplification budget for arbitrary user-room complexity,
  raycast/reflection math against it) than "new render pass." Quest 3 gets
  a meaningfully better mesh than Quest 2/Pro if that ever matters.

## On-device test pass: findings, then a round of fixes

First real headset session against the new top bar. Findings (verbatim
in spirit): 3D toggle worked; keyboard icon did nothing; the glow was "an
overpowering white that fills the room" and needed a short throw instead;
sliders/icons were too small to grab reliably, wanted a round thumb and a
thinner track; curve wanted more curve at max; the darkness slider itself
was fine on a second look ("I take it back... it's the glow that's too
strong"); exit worked perfectly; the corner resize handles needed more
room off the screen edge to grab. Two polish asks came out of the same
session: a short fade instead of an instant flash on exit, and whether
Quest gives this app the same scheduling priority a "real" VR game gets.

**Sizing.** `CORNER_HOVER` 1.5->2.2, `TOPBAR_ITEM_SIZE_M` 0.10->0.20,
`SLIDER_TRACK_WIDTH_M` 0.28->0.56, `SLIDER_THUMB_SIZE_M` 0.045->0.09.
Track height went the other way, 0.03->0.022 - doubling everything
uniformly would have made the track read as a fat bar instead of a slim
line with a big grabbable thumb on it. Curve's tight end
(`CURVE_RADIUS_MIN_MULT`) tightened from 1.0x to 0.6x viewing distance;
both call sites (`updatePlacement()`'s seed and `applySliderValue()`'s
live update) now read the same two named constants so they can't drift
apart again.

**Glow vs. darkness, actually separated.** The v1 design above tinted the
same full-surround dim sphere the brightness slider drives - which is
exactly why it read as "fills the room": a colour applied to something
already covering the entire passthrough view has no way to stay short-
throw. Reverted the dim sphere to flat black, always. The glow is now its
own small quad (`updateGlowHalo()`, 64x64 alpha-only) hugging the screen's
own rectangle with a soft `GLOW_MARGIN_FRAC` (0.18) falloff - transparent
directly behind the screen (which draws over it regardless, composition
layers are submission-order, not depth-tested) and fading to nothing a
short distance past the screen's edge. Submitted between the dim sphere
and the screen itself, Gaming mode only.

**Keyboard - root cause found, one manifest line.** `toggleSoftInput()`
was already correctly wired (confirmed via `Game.java`/`activity_game.xml`
- a focusable, focused `StreamView` exists in the hierarchy either way).
The actual cause: Horizon OS gates the system-keyboard-overlay compositor
feature behind an opt-in `<uses-feature>` flag. Without it the OS silently
refuses to composite the keyboard overlay during an active immersive
session - the `InputMethodManager` call still "succeeds", nothing is ever
drawn. Confirmed via Unity/Unreal's identical error message
("Oculus overlay keyboard is disabled, add
'oculus.software.overlay_keyboard' feature request..."), and it's a
manifest flag rather than an engine API, so it applies the same way to
this native OpenXR app. Fix: added
`<uses-feature android:name="oculus.software.overlay_keyboard"
android:required="false"/>` to `AndroidManifest.xml`. (Separately, Meta
also has a much heavier `XR_META_VIRTUAL_KEYBOARD_EXTENSION_NAME` API for
rendering their own 3D floating keyboard model in-scene - not needed here,
that's a different feature from showing the plain system IME overlay.)

**Fade in/out, ~2s.** New independent whole-view fade sphere
(`fadeSwapchain`/`ctx->fadeAlpha`/`FADE_IN`/`FADE_OUT`), same tiny
alpha-only equirect recipe as the dim sphere but submitted dead last in
the layer array so painter's-algorithm order puts it in front of
literally everything - the screen included, not just the room (the dim
sphere alone can't do this: it's submitted first/backmost, so the screen
always draws over it). Session start (`XR_SESSION_STATE_READY`, right
after `xrBeginSession` succeeds) seeds it fully black and eases to clear
over `FADE_DURATION_NS` (2s). Pressing the Exit icon no longer raises
`IN_EXIT_PRESSED` immediately - it starts a `FADE_OUT` instead (captures
the current alpha as the ease-from point, so pressing exit mid fade-in
doesn't jump); once that reaches full black, `nativeEndFrame` sets
`ctx->fadeOutComplete`, and the *next* `nativeUpdateInput` call is what
actually raises `IN_EXIT_PRESSED` - checked ahead of both modes' input
handling and any focus/placement early-return, so exit can't get stuck
mid-fade if the session loses focus. This means `finish()` only ever
lands on a frame the user can no longer see anything of.

**Performance level.** Added `XR_EXT_performance_settings` (detected like
every other optional extension here, enabled conditionally, `enabledExts`
bumped from 9 to 10 slots to fit it). At the same `XR_SESSION_STATE_READY`
point the fade kicks off, also calls
`xrPerfSettingsSetPerformanceLevelEXT()` for both `CPU_EXT` and `GPU_EXT`
domains at `SUSTAINED_HIGH_EXT` (not `BOOST_EXT`, which the spec frames as
a short-burst allowance - a stream runs for the whole session). This is
additive to, not a replacement for, the existing
`com.oculus.intent.category.VR` intent filter that already marks this as
an immersive app to the scheduler; it's an explicit ask rather than
hoping the runtime's default pick is generous. Whether it produces a
measurable difference (this device may already have picked a high level
on its own, same as the shipping-config-costs-nothing finding elsewhere
in this project) is unverified.

## Live on-device round two: real bugs found by actually using it

A second headset pass, this time exercising the fixes above for real
(brightness/curve sliders, corner handles, glow) instead of just reasoning
through them. Found several real bugs the first pass's static reading
missed entirely:

**Slider thumb rendered as a hard square, not a circle - and the glow
halo didn't fade, it just stopped dead at a sharp edge.** Same root
cause in both places: `XR_COMPOSITION_LAYER_BLEND_TEXTURE_SOURCE_ALPHA_BIT`'s
blend equation (`Color_dst = Color_src + Color_dst*(1-Alpha_src)`) expects
premultiplied source color - i.e. color must already shrink toward black
as alpha shrinks toward zero. The slider track already did this
(`p[0]=p[1]=p[2]=a`); the thumb didn't (`p[0]=p[1]=p[2]=255` regardless of
`a`), and the glow halo didn't either (fixed `r,g,b` regardless of `a`).
A "transparent" texel with full-strength color still adds that color at
full strength under this blend mode, so both rendered as if alpha were
pinned at 255 everywhere except the literal quad boundary. Fixed both to
premultiply, matching the track. Also, while chasing "the glow is too
big and doesn't follow the curve": the halo was always a flat quad even
when the screen itself renders as a curved cylinder - added a matching
cylinder path (same radius/axis math as the screen's own, `centralAngle`/
`aspectRatio` scaled by the same margin factor) so it actually hugs a
curved screen instead of a flat rectangle sitting in front of one.
Shrunk `GLOW_MARGIN_FRAC` further (0.18->0.10), bumped `GLOW_HALO_TEX`
64->128, and switched the linear alpha ramp to a smoothstep ease so the
fade reads as a fade instead of a ramp over a handful of texels.

**Dragging the slider thumb didn't work, and it also snapped the corner
resize handle.** `updateTopBar()` sets `ctx->grabMode = GRAB_SLIDER` and
owns that grab's entire lifecycle itself - but `applyGrab()` (the
screen's own move/resize state machine) ran unconditionally every frame
regardless, and its very first check (`if (ctx->grabMode != GRAB_NONE)`)
doesn't know what `GRAB_SLIDER` is. It read `ctx->grabByTrigger`/
`ctx->grabHand`, both meaningless for a slider grab, decided the grab
had been released, reset `grabMode` to `GRAB_NONE`, and then - same
frame, same stray hover state - could immediately hand it to
`GRAB_RESIZE` if the ray also happened to land in the screen's corner
zone. Fixed by skipping `applyGrab()` entirely while `grabMode ==
GRAB_SLIDER`; that state is `updateTopBar()`'s alone now.

**Corner resize handle sat half on top of the screen's own corner.**
`local.x/y` placed it exactly at the corner (`±0.5*screenWidth`), not
outside it. Added `CORNER_GAP_FRAC` (same standoff convention as the
move bar's `BAR_GAP_FRAC`) so it now sits fully clear of the picture.

**Glow-toggle icon didn't get the 2x sizing pass the rest of the top bar
got.** `GLOW_TOGGLE_SIZE_M` was missed when `TOPBAR_ITEM_SIZE_M` doubled
earlier - doubled it too (0.06->0.12).

**Brightness icon was rendering as a blank white block.** The pre-made
asset was authored as a filled two-tone glyph (opaque black curve +
opaque white fill, both alpha 255) instead of this app's actual
convention for every other top-bar icon (a white glyph, alpha-shaped,
transparent everywhere else). Replaced with a plain sun glyph matching
that convention.

**Exit fade shortened 2s -> 1s** per feedback that 2 felt long.

**Keyboard: root-caused fully, then pulled from the top bar anyway.**
The `oculus.software.overlay_keyboard` manifest fix from the previous
round was real and correct - confirmed via logcat that the entire chain
fires end to end: native hit-test -> `IN_KEYBOARD_TOGGLE` -> Java
dispatch -> `toggleKeyboard()` -> `InputMethodManager.toggleSoftInput()`
-> Horizon OS's own `KeyboardInputMethodService` logging
`onShowInputRequested package: com.limelight.debug`. The OS genuinely
accepts the request. But the panel never actually appears, and Horizon
OS's own logs show why:
```
W DynamicObjectClient: FIXME: failed to enable keyboard tracking
W DynamicObjectClient: Failed to enable KeyboardTrackingFidelity.
```
literally a `FIXME` left in Meta's shipped code, right after
`ObjectTrackingEngine::startTrackingKeyboard()` registers the keyboard
successfully. Earlier in the same log: `"Incompatible features Keyboard
and SurfaceInputs are requested ON -- stopping keyboard tracking"` - this
app requests hand-tracking (Horizon calls it "SurfaceInputs" internally),
and the user was in fact on hand-tracking (bare hands, no controllers)
during every failed test. Everything points to a genuine Horizon OS
limitation: the system keyboard's positioning can't come up while
hand-tracking is the active input mode, independent of anything this app
does. Decision: pull the keyboard icon from the top bar for now
(`TOPBAR_ITEM_COUNT` 5->4, `TOPBAR_KEYBOARD_INDEX` removed, indices
renumbered) rather than ship a button that silently does nothing for
hand-tracking users - who are apparently the norm, not the exception.
The underlying `IN_KEYBOARD_TOGGLE` plumbing (Java `toggleKeyboard()`,
`onVrKeyboardToggleRequested()`, the manifest flag) is left in place,
just unreachable - re-adding the icon later is the only step needed if
Horizon OS ever fixes this, or if a controller-only code path is worth
carrying separately.

**This build is now the release build, not debug.** Per the user: this
fork isn't a debug/test app, it's the actual Virtual Moonlight people
use - `com.limelight.debug` has been uninstalled from the test device in
favour of the signed `nonRootRelease` build (`com.limelight.unofficial`,
per upstream Moonlight's own applicationId convention for third-party
release builds - see the big comment in `app/build.gradle`). All testing
from here on should target that build, not debug.

## Not yet verified / next up

- The corner-handle widening (`CORNER_HOVER`) is a symmetric hover-zone
  change, not an asymmetric outward shift - may not fully match "expand
  the handles off the screen edge" if that meant something more specific.
- The 3D-effect toggle specifically: repeated on/off cycling within one
  session (does `reconcileDepthThread()` actually behave under rapid
  double-taps, does the depth EGL context survive several start/stop
  cycles cleanly) has only been reasoned through, not run on a headset.
- Further UI/UX targets beyond the top bar: the flat 2D screens (PC-select,
  Settings) haven't had a pass yet, and are actually easier to iterate on
  without a headset worn (adb can drive/screenshot a normal Activity in a
  way it can't drive an immersive OpenXR session).
- Separately flagged, not yet root-caused: an earlier on-device build
  showed the old mode tab bar and no version number in the corner despite
  a byte-verified build - unclear if still reproducible now that
  Productivity Mode's tab has actually been removed from this app.
- Performance level (`SUSTAINED_HIGH_EXT`): still no way to confirm from
  outside a session whether it changed anything measurable.
- Whether the fixes in this entry actually look/feel right on the release
  build specifically (all verification so far happened on the now-removed
  debug build).
