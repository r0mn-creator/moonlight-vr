# Getting Virtual Moonlight onto the Meta Horizon Store

Research notes from 2026-09-17, for future reference if we ever decide to
pursue an actual Meta Store listing (as opposed to sideloading + a
Universal Menu shortcut, which is the current, much lighter-weight path —
see `BRAINSTORM.md`).

**Bottom line up front**: this is a real submission and review process,
not a form to fill out. Budget 4-6+ weeks, expect at least one rejection
round, and read the "The one project-specific risk that could actually
block this" section below before investing time in it — it's the single
biggest open question.

## "App Lab" doesn't exist as a separate thing anymore

App Lab (the old lower-friction distribution tier) was merged into the
main Meta Horizon Store in August 2024. There's now one unified submission
path — you don't submit to "App Lab" specifically, you submit to the
Store, and Meta's own review process determines visibility/curation
outcomes. Older guides that talk about "App Lab" as a separate thing are
describing a system that no longer exists in that form.

## Account setup (before you can submit anything)

1. Create a Meta Horizon developer account: https://developers.meta.com/horizon/sign-up/
2. Verify the account — a payment method and/or 2FA (authenticator app or
   phone number).
3. **Organization verification** — required for every developer org that
   wants to publish or update an app. Two paths:
   - **Admin Verification**: a government-issued ID, typically done in
     minutes. This is the one almost every independent developer uses.
   - **Business Verification**: business name/address/phone (+ optional
     website/tax ID) if submitting as a registered business rather than
     an individual.
4. **Tax forms** — a W-8 or W-9 is requested even for free apps. If
   charging money, additional bank/payment setup is required.

## The actual submission process

1. **Create the app** in the developer dashboard.
2. **Sign the release build** with your own Android signing certificate
   (this can't be the debug/dev key you've been using this whole
   project — needs the real release keystore, which we already set up
   for the beta releases: `virtualmoonlight-release.jks`).
3. **Upload the signed build** (APK, max 1GB; OBB expansion files allowed
   up to 4GB if needed).
4. Fill in store metadata: description, screenshots, a logo with a
   **transparent background** (verify this explicitly — a flagged common
   mistake), age rating, pricing (free is fine, no submission fee is
   mentioned anywhere in Meta's docs).
5. Optionally set up release channels (for internal/beta testing before
   a public release).
6. Submit for review from the dashboard's Submission tab once it shows no
   errors/warnings.

**Once submitted, the build is locked** — you can't push fixes mid-review.
Only submit when genuinely confident it'll pass, since resubmission means
going through the queue again.

## Technical requirements (Virtual Reality Checks / VRCs)

- **Minimum permissions only.** Game engines/toolchains often silently add
  "dangerous" permissions (camera, location, mic, storage) that aren't
  actually used — check the manifest with `aapt dump badging` and strip
  anything unused. Document any permission that genuinely is needed in
  reviewer notes so it isn't flagged as suspicious.
- **Stability**: must install and run without crashes, freezes, or
  extended unresponsive states. Test with multiple people, not just one.
- **No progression blockers**: reviewers reportedly playtest for around 45
  minutes and will reject anything that leaves them stuck.
- **2D apps get a reduced VRC subset** compared to full immersive/game
  apps — worth confirming exactly where Virtual Moonlight's Productivity
  mode (a hybrid: an immersive OpenXR session, but showing a flat desktop,
  not a game) lands in that classification, since it isn't cleanly either
  category.

## Timeline and review reality

- **4-6 weeks** for review is the commonly cited expectation.
- **2-3 resubmission rounds** are described as common even for compliant
  apps — first-pass approval is the exception, not the norm.
- Submit at least **2 weeks before** any target launch date, per Meta's
  own guidance, and that's on top of the 4-6 week review window, not
  instead of it.

## Common rejection reasons (from Meta's own "common flags" page)

1. **Third-party IP / copyright**: *"Instances of assets or mentions of
   third party intellectual property will require approval by the
   copyright owner."* — see the project-specific risk section below, this
   is the one that actually matters for us.
2. Visual assets depicting VR hardware other than the target headset.
3. Hate speech / harmful stereotypes / incitement to violence.
4. Sexual content intended for gratification.
5. Age rating that doesn't match actual content.
6. **Meta's own trademarks** ("Rift," "Gear VR," "Go," "Quest," the Meta
   logo) can't appear anywhere in the app — the inverse direction from the
   copyright issue above, but the same principle: whoever owns a mark or
   copyright gets to control its use in your submission.

## The one project-specific risk that could actually block this

Virtual Moonlight is a fork of Sean Gilleece's Moonlight XR fork, itself
built on the broader open-source Moonlight client. Virtual Sunshine is a
fork of ClassicOldSong's Apollo, itself a fork of LizardByte's Sunshine.
All of this is properly GPL-licensed and credited — see the READMEs — and
that satisfies the *license*. **It does not automatically satisfy Meta's
own review bar.** Their policy explicitly requires **approval from the
copyright owner** for "assets or mentions of third party intellectual
property," and our own README/credits sections *do* mention "Moonlight,"
"Sunshine," "Apollo," and their original authors by name, as they should.

Meta's review process has no visibility into GPL license terms — it's a
human (or a policy-driven) review looking for exactly this pattern:
another project's name and branding appearing in a submission that isn't
that project's own official one. **The original Moonlight client itself
does not have an official Meta Horizon Store listing either** — there's an
open community feature request for one (`ideas.moonlight-stream.org`,
"Meta Quest Store Version") that has apparently gone nowhere. That's real,
relevant precedent: if the actual upstream Moonlight project — with a much
larger, more established maintainer base — hasn't gotten this done, it's
a reasonable signal that it's a genuinely hard bar to clear for anything
in this whole app family, not just our fork specifically.

**What this means practically**: before spending real time on a
submission, it's worth either (a) reaching out to Sean Gilleece /
ClassicOldSong for something in writing that could serve as "copyright
owner approval" if Meta's reviewers ask for it, or (b) accepting that this
attempt could stall specifically on this point regardless of how clean the
technical submission is, and treating the review fee (in time, not money)
as a real gamble rather than a formality.

## Recommendation

Given the above, the Universal Menu pin (see `BRAINSTORM.md`) remains the
pragmatic path for now — it costs nothing, doesn't require untangling the
IP-approval question, and gets most of the way to "avoid digging through
Unknown Sources every time." A real Store submission is worth revisiting
if/when the project matures further and there's an appetite to actually
resolve the attribution question with upstream maintainers first.

## Sources

- [Submitting your app — Meta Horizon OS Developers](https://developers.meta.com/horizon/resources/publish-submit/)
- [How to Prepare for a Successful App Lab Submission](https://developers.meta.com/horizon/blog/how-to-prepare-for-a-successful-app-lab-submission/)
- [Meta Quest Virtual Reality Check (VRC) guidelines](https://developers.meta.com/horizon/resources/publish-quest-req/)
- [Verify your organization](https://developers.meta.com/horizon/resources/publish-organization-verification/)
- [Business verification](https://developers.meta.com/horizon/resources/publish-organization-verification-business/)
- [Manage your financial account (bank/tax)](https://developers.meta.com/horizon/resources/publish-account-management-bank-tax/)
- [App policies — Meta Horizon OS Developers](https://developers.meta.com/horizon/policy/app-policies/)
- [Common flags during content review](https://developers.meta.com/horizon/resources/publish-common-flags/)
- [Brand guidelines](https://developers.meta.com/horizon/resources/publish-brand-guidelines/)
- [Meta Horizon Store — Wikipedia](https://en.wikipedia.org/wiki/Meta_Horizon_Store) (App Lab merger, Aug 2024)
- [Meta Quest Store Version — Moonlight Ideas and Suggestions](https://ideas.moonlight-stream.org/posts/435/meta-quest-store-version) (evidence upstream Moonlight itself isn't Store-listed)
