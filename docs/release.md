# Release

Two tag shapes, and only two.

```
v0.4.2                                     release, pushed by a person
v0.4.3-dev.2026-08-25.11-30-00.g1a2b3c4    pre-release, cut by every push to main
```

Both are valid SemVer 2.0.0, and the ordering is the point. §11 sorts a
pre-release below the release sharing its core version, so
`v0.4.2 < v0.4.3-dev.… < v0.4.3`. Anything that sorts tags gets "newest" right
without knowing this scheme exists, which is why a pre-release is numbered for
the patch that has not happened yet.

## The tag is the version

There is nothing to bump before tagging. Pushing the tag is the entire release
act, and if the next release should be a minor, tag a minor.

An earlier design also declared a version in a committed file and had a gate
refusing a tag that disagreed with it. That check existed only because there
were two sources; deleting the second source makes the invariant hold by
construction, and takes the gate with it. `version=0.0.0` in `gradle.properties`
is a fallback for builds that have no tag, visibly a placeholder rather than a
claim. It is never an input to the algebra.

## Where the algebra lives

[`build-logic/`](../build-logic), an included build, in Kotlin, with unit tests
that `ci.yml` runs. Not in YAML, not in bash, not in a Gradle script. The tests
check the laws rather than the happy path, because every law here fails silently
when it is broken:

| Law | What breaks without it | Test |
|---|---|---|
| Precedence is major, then minor, then patch, numerically | a string comparison puts 0.10.0 below 0.9.0 | `versions compare field by field and numerically` |
| The stamp is fixed width, zero padded, UTC | `9-30-00` sorts above `11-30-00` and two builds ninety minutes apart come back reversed | `later builds sort later, an hour apart and either side of ten` |
| The commit renders behind a literal `g` | an all-digit abbreviation is a numeric SemVer identifier and ranks below every alphanumeric sibling | `a commit is never an all-digit identifier` |
| The base version is `newest_release_tag.successor()`, one operand | nothing, until a second source disagrees with the first | `the base version is the newest release tag's successor and nothing else` |

`resolve` is total: every event has a publish. The one untrusted string, the ref
name, is parsed at the boundary that receives it, so `Event.Release` carries a
`Version` rather than a tag and there is no state downstream that has to ask
whether the tag was well formed.

## versionCode

A monotonically increasing integer, which the tag scheme does not produce. It is
encoded from the tag rather than counted in parallel:

```
major 6 bits | minor 8 bits | patch 8 bits | revision 8 bits
```

Byte aligned deliberately, so `0x00010107` reads as 0.1.1 revision 7. The law is
order preservation: for publishes `a < b` by SemVer precedence,
`code(a) < code(b)`. It is tested as a property over a simulated history with
several releases and the pre-releases between them, not at hand-picked points.

`revision` is 255 for a release, which is the field's maximum. The obvious
encoding gives a release the revision it was cut at, which sorts it below the
pre-releases that led to it and makes Play reject it as a downgrade. The maximum
reproduces what SemVer already does with the same intent.

For a pre-release, `revision` counts commits since the newest release tag. **The
cap is not 2^31.** Google Play's maximum is 2,100,000,000, which is below
`Int.MAX_VALUE`, so a 31-bit packing overflows something that looks like it
fits. Thirty bits reach 1,073,741,823 and cannot.

Every field and the total are validated and a violation is refused, never
wrapped: `shl` truncates in silence, so a 64th major version would land outside
its field and corrupt the one above it, and a number already accepted by Play
cannot be withdrawn. A build that cannot be numbered fails.

### Two hazards, named

**No release tag at all.** The revision would count from the repository root.
boreas-core reached 108 commits with zero releases and this field holds 255, so
`v0.0.0` was cut before any of this code existed. It is an anchor, not a shipped
artefact.

**A force-push to main** changes a commit count and can make a later build
repeat an earlier `versionCode`. Play rejects a duplicate, which is the good
outcome. Branch protection forbidding force-push on `main` is the real fix, and
signed commits belong in the same setting.

## The pipeline

`resolve` → `gate` → `build` → `publish`, in
[`release.yml`](../.github/workflows/release.yml).

`resolve` is one command returning the identity, and the identity travels as job
outputs. No later job works it out again: the tag carries a timestamp, so two
jobs reading their own clocks would tag the release one way and stamp the binary
another. `:app` takes `-Pboreas.versionName`, `-Pboreas.versionCode`, and
`-Pboreas.provenance` and computes none of them.

`gate` calls `ci.yml` rather than copying its steps, so there is one definition
of "green". It must run inside the release workflow: `ci.yml` runs on the same
push, but the two are independent workflows racing each other, and a red `main`
must not publish.

`publish` attaches SLSA build provenance with `actions/attest@v4` and a
`SHA256SUMS` over every asset. Pre-releases are marked as such, so
`gh release download` with no tag never returns one and "Latest" always means a
real release. Concurrency queues rather than cancels: a cancelled release leaves
a tag with no assets, which is the one state a consumer cannot recover from.

**No dependency cache in the release workflow.** `actions/cache` restores across
refs, so an entry written by a run on any branch is offered to a run on a tag,
and a Gradle cache holds executable jars and compiled build logic. Restoring one
would let an unreviewed branch decide part of what a published artefact is built
from, which is the supply-chain edge the attestation exists to close. The cost is
a cold dependency download per release. `ci.yml` caches freely; its outputs are
for inspection and are not published.

## Play track mapping

There is no Play publishing step yet, and a pre-release must never acquire one.
The mapping, when it lands: a pre-release publishes to `internal` and nothing
else, and only a tag push may name `production`. That is enforceable rather than
conventional, because `prerelease` is a projection of which variant `resolve`
returned and not a flag a step can set.

## Signing

Four values, or none. `BOREAS_KEYSTORE_BASE64`, `BOREAS_KEYSTORE_PASSWORD`,
`BOREAS_KEY_ALIAS`, and `BOREAS_KEY_PASSWORD` as repository secrets; the build
demands the other three the moment the keystore appears rather than defaulting
them to empty and having `apksigner` report it fifteen minutes later.

Absence is a state, not a failure: a fork with no key still produces an artefact
to inspect. The assets are named `-unsigned` when it happens, so a tester learns
before `adb install` does. **The secrets are not set in this repository yet**, so
today's releases carry unsigned assets.

## What a release records

Both halves of the composition, in the release notes and on the app's About
screen:

```
app  0.1.1-dev.2026-08-25.11-30-00.g1a2b3c4  (v0.1.0 + 7)
core v0.1.0-dev.2026-08-25.09-14-02.ge18b70f
```

A bug report maps to one (app version, core version) pair or it maps to nothing,
and the app half is only legible if it names the release it is an offset from.
