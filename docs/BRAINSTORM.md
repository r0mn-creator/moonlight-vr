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
