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

## Not yet verified / next up

- The actual on-device feel of the top bar and slider in both modes (needs
  a real PC-connected session).
- Further UI/UX targets beyond the top bar: the flat 2D screens (PC-select,
  Settings) haven't had a pass yet, and are actually easier to iterate on
  without a headset worn (adb can drive/screenshot a normal Activity in a
  way it can't drive an immersive OpenXR session).
