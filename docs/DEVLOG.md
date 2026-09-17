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

Icon is a placeholder pair (filled "lenses" for on, outline-only for off)
since no custom art was provided for this one yet.

## Not yet verified / next up

- The actual on-device feel of the top bar and slider in both modes (needs
  a real PC-connected session).
- The 3D-effect toggle specifically: repeated on/off cycling within one
  session (does `reconcileDepthThread()` actually behave under rapid
  double-taps, does the depth EGL context survive several start/stop
  cycles cleanly) has only been reasoned through, not run on a headset.
- Further UI/UX targets beyond the top bar: the flat 2D screens (PC-select,
  Settings) haven't had a pass yet, and are actually easier to iterate on
  without a headset worn (adb can drive/screenshot a normal Activity in a
  way it can't drive an immersive OpenXR session).
