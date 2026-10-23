# Curfew Android handoff

Reader: The engineer or coding agent continuing on another machine.
Action: Finish Sunrise P1 acceptance, then deliver the remaining Android product work without treating the alarm as the complete MVP.
Snapshot: October 22, 2026, America/Los_Angeles.

## Resume the right checkout

Use [curfew-android](https://github.com/TheHypertextStudio/curfew-android), branch `codex/sunrise-p1`, and [draft PR #18](https://github.com/TheHypertextStudio/curfew-android/pull/18). The pre-handoff head is `10f6a03eddf362c4b5d2b526399b9c2551d3a695`. The last executable/test change is `172d951478251f0f3f456d735bc08fe9be46999d`; later changes record evidence and this handoff. The originating checkout has no pending app changes.

```sh
git clone --branch codex/sunrise-p1 git@github.com:TheHypertextStudio/curfew-android.git
cd curfew-android
./bootstrap worktree prepare
git clone --branch v0.0.13 --depth 1 git@github.com:TheHypertextStudio/curfew-protocols.git ../curfew-protocols
git -C ../curfew-protocols rev-parse HEAD
```

The protocol revision must be `764ec480a5e5424856150be767a33a8ab022ae08`. Use Java 21 and the Android SDK versions declared by this checkout. Bootstrap requires authenticated GitHub access to the private Studio bootstrap repository. Keep local mutable build outputs and device state separate. Do not use the dirty macOS checkout as an Android workspace.

Run the normal checks with bounded workers when code changes:

```sh
CURFEW_PROTOCOLS_CHECKOUT="$PWD/../curfew-protocols" ./gradlew \
  testPlainDebugUnitTest testGmsDebugUnitTest \
  lintPlainDebug lintGmsDebug \
  assemblePlainDebug assembleGmsDebug \
  assemblePlainDebugAndroidTest assembleGmsDebugAndroidTest \
  --no-parallel --max-workers=1
```

This is a normal build, not a physical-device test command. The README lists minified release, dependency isolation, SBOM, provenance, checksum, and license checks. Follow AGENTS.md and the failing-test-first process for executable changes.

## 1. Technical P1 acceptance

Technical local Sunrise P1 is accepted with explicit physical Pixel Doze
testing excluded by the owner. This is a waiver, not a Doze pass. The prior
interrupted attempts remain rejected and the reference driver stays unexecuted.
See [P1_ACCEPTANCE.md](P1_ACCEPTANCE.md) for source-boundary evidence and the
remaining launch limitations.

- [x] The Android 13 retry and all seven jobs in its run pass.
- [x] The continuation source also passes all seven jobs in CI.
- [x] Native Docket access is restored; the Codex-attributed update is read back.
- [x] The handoff archive is transferred over SSH and verified by matching hashes.
- [x] Isolated GMS acceptance app/test APKs assemble against pinned protocols.
- [x] Explicit physical Pixel Doze testing is excluded from acceptance by owner
  direction. This checkbox records scope reconciliation, not test execution.

No new phone test was run or alarm armed. Manual TalkBack, current-build
physical UX, natural overnight standby, production signing, and distribution
remain separate gates. Keep PR18 for final review; do not infer production or
installer publication permission from technical P1 acceptance.

## 2. Supply a consumer check-in method

This is the main consumer-launch gap. Current P1 requires an operator running a compatible HTTPS service. Curfew does not supply that service. The app can save a draft and explain the prerequisite; that does not give an ordinary person a usable release method.

- [ ] Choose and ship a real consumer release method. Trusted-person approval needs invitation, acceptance, bounded authority, an approval surface, revocation/replacement, and recovery. Another selected method needs an equally complete setup-to-approval path. The exact first-party service design remains undecided.
- [ ] Make the ordinary flow work without entering an endpoint, exporting a signing key, or operating a server. Keep the supported advanced integration available separately.
- [ ] Bind approval to the current occurrence. Reject stale, wrong-occurrence, replayed, expired, and revoked authority. Losing internet or an unreachable service supplies no approval.
- [ ] Test enrollment, pending approval, successful release, cancellation, failed delivery, offline behavior, and deliberate owner recovery as one consumer workflow. Do not advertise a provider catalog, QR enrollment, or trusted-person portal before it exists.

The existing advanced integration contract and operational requirements are in [VERIFIER_SETUP.md](VERIFIER_SETUP.md). Do not weaken authenticated release or add ordinary dismiss/snooze to conceal the missing consumer service.

## 3. Deliver P1b device-network integration

The [P1b project](https://clearthedocket.com/orgs/01KY2ZK3JXKTE6RENXVVJN2Y19/projects/01M5AXW8DZZCX2TBHS59WV6EBE) follows accepted P1. Audit current code before assuming that pre-existing account/sync plumbing completes these outcomes.

- [ ] Make pairing/enrollment turnkey. Eligible owner devices participate by default, exclusions persist, and joining during an active occurrence does not impose an undisclosed immediate restriction.
- [ ] Distribute one stable occurrence and retain confirmed local enforcement state. Distinguish intended, pending, applied, excluded, and unreachable devices. Each connected Android device acts on its owner's policy; it is not just a remote control.
- [ ] Propagate verified release with per-device acknowledgment, duplicate safety, revocation, and reconnect convergence. A morning release must not remove an unrelated bedtime restriction.
- [ ] Provide offline paired-code release only after the phone has valid release authority. Keep TOTP terminology and secret handling out of routine UI. Define occurrence eligibility, clock tolerance, rate limits, successful-code reuse, next-day reuse, rotation, and offline revocation limits. Standard TOTP alone does not bind a code to an occurrence. Do not add a challenge-response interaction without a demonstrated need.
- [ ] Verify Android-to-Mac online and partitioned journeys on installed builds. A coordinator response does not establish that a sleeping device applied or released its gate.

Wire changes belong in `curfew-protocols` first, with a tagged `0.0.x` publication and contract tests. Update `curfew-sync`, then the native consumers in their repositories. Keep the current `0.0.13` pin until a deliberate contract release replaces it. Do not invent wire fields in Android.

## 4. Implement bedtime Curfew and accept the full MVP

The [bedtime project](https://clearthedocket.com/orgs/01KY2ZK3JXKTE6RENXVVJN2Y19/projects/01M5AY6Q8GWGQBW79TGE6JRR5K) has four distinct outcomes:

- [ ] Prove and document the Android restriction mechanism, supported blocked surface, required permissions, bypasses, and distribution constraints using current official documentation and an installed device. Support one-night/recurring intervals and define DST, timezone, overlaps, and active edits. Do not substitute a reminder for actual restriction or silently narrow the product requirement.
- [ ] Enforce the declared bedtime restriction while preserving emergency access, Sunrise audio, its verification action, accessibility, and deliberate recovery. Exercise reopening apps and supported navigation routes. Missing access must produce an honest degraded state.
- [ ] Preserve the policy offline and through process death, relaunch, reboot, replacement, clock/timezone changes, and permission revocation. Distinguish before-first-unlock from after-unlock support and retain a tested recovery/rollback route.
- [ ] Accept a complete bedtime-to-morning routine. Bedtime ending cannot satisfy Sunrise; Sunrise approval cannot remove an active bedtime restriction. Verify each policy alone, both together, and offline. Both local capabilities are required for the Android MVP.

## 5. Finish physical UX and release validation

These are broader launch gates, not extra hardware prerequisites for Sunrise P1.

- [ ] Install the current normal candidate on the Pixel without erasing settings and review its complete first-use, permission-return, sound-check, check-in, editing, ringing/quiet, offline, error/retry, recovery, cancellation, and relaunch flows. The latest complete setup visual evidence is emulated, not a fresh physical review of every screen.
- [ ] Complete manual TalkBack, focus/order, reduced-motion, and keyboard review. Preserve equivalent recovery controls for assistive technology. Record natural overnight, physical routing, and allowed-full-screen behavior honestly where those behaviors are advertised. Additional OEM compatibility can follow the initial Pixel milestone; it does not block P1 by definition.
- [ ] Review every changed screen as a complete workflow. Use Curfew's established symbol, warm palettes, type roles, device-aware light/dark themes, Material 3 Expressive controls/icons, adaptive navigation, and connected motion. Avoid jargon, duplicated titles, self-narrating UI, fixed-size layouts, and implementation details in ordinary setup.
- [ ] Any screenshot handoff must include phone, tablet, foldable cover/inner windows, both orientations/themes, and narrow 200% text. Review keyboard, insets, short windows, errors, and motion. Distinguish emulated windows and injected hinge features from actual foldable hardware.
- [ ] Provision the protected production signing lineage for both Plain and GMS. The inspected CI release APKs are unsigned; neither checksums nor a passing job label establish signing. Verify install/update compatibility, minification, artifact provenance, SBOM/licenses, direct APK distribution, and Play AAB submission as applicable.
- [ ] Reconcile Play declarations, data safety/privacy, exact-alarm/full-screen access, foreground playback, recovery language, and actual product claims with the shipping behavior. Recheck current official requirements at implementation time. Do not imply unstoppable enforcement, before-first-unlock ringing, or offline approval from an unavailable verifier.

## 6. Keep optional follow-ups separate

Accountability calls remain later work. They need explicit enrollment, recipient consent, recorded or generated message handling, escalation timing, cancellation, cost controls, and delivery/failure behavior. They do not gate P1 or the defined local MVP. Do not place calls as part of this handoff.

## Current evidence and tracker references

[CI 39585385215](https://github.com/TheHypertextStudio/curfew-android/actions/runs/39585385215) passes all seven jobs at executable candidate `172d951`. Each API33–36 job reports 186 passes and seven guarded skips. Opt-in skips are not executed acceptance. The later documentation-only head triggered [repeat CI 39587495741](https://github.com/TheHypertextStudio/curfew-android/actions/runs/39587495741), whose Android 13 retry is described above. Fetch current state rather than treating this snapshot as live.

[P1_ACCEPTANCE.md](P1_ACCEPTANCE.md), [TESTING.md](TESTING.md), and [the accepted CI record](evidence/check-in-2026-10-21/ci-accepted-39585385215.json) retain requirement and source boundaries. [The current reviewed setup matrix](../design/android-review/native/check-in-startup-matrix/README.md) has 18 profiles, 180 state images, four controlled transition frames, and 30 contact sheets. No new screenshots are produced by this handoff.

The Docket organization is `01KY2ZK3JXKTE6RENXVVJN2Y19`. The [Android initiative](https://clearthedocket.com/orgs/01KY2ZK3JXKTE6RENXVVJN2Y19/initiatives/01M5AXW5ZZAE144JGFH5TJ0FYF) contains [P1](https://clearthedocket.com/orgs/01KY2ZK3JXKTE6RENXVVJN2Y19/projects/01M5AXW727EJSE5FM2H8WXJRWS), P1b, and bedtime work. Re-read current templates and entity state after connecting; use native agent attribution and literal checkbox acceptance criteria. The last verified P1 task states are:

| Task | ID | Last verified state |
| --- | --- | --- |
| Scheduling | `01M5AXW9SCGNXJY7HV1SZ1EQAD` | Done |
| Playback/readiness | `01M5AXWB4C1A3BV0JYH51RPTP3` | Done |
| Authenticated release | `01M5AXWCQPQPPA0BSJ1N92AWCF` | Done |
| Recovery and physical acceptance | `01M5AXWE2H8R2ETE60VDXXF640` | In progress |

The final status update was not published because native MCP authentication failed. The originating session's goal is marked blocked, not complete. Its tools no longer expose Docket. The new machine must establish its own connector session. No phone alarm or physical test remains running; the owned temporary emulators were removed. The CI retry is the only live verification job identified at handoff.

The original `Documentation/alarm-delivery-plan.md` and `Documentation/android-mvp.md` are untracked in the originating macOS checkout. A clone will not contain them. Their work boundaries are summarized here and exist in the linked Docket projects. The transfer archive also includes the original files and the reference-only light-Doze driver. It excludes APKs, signing material, callback secrets, databases, account credentials, and raw personal-device logs.
