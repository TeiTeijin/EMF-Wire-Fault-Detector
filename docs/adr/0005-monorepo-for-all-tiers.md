# ADR-0005: One monorepo for firmware, relay backend, and frontend

- **Status:** Accepted
- **Date:** 2026-10-17
- **Deciders:** Project team (all)

## Context

The course requires a single repository link that contains firmware, backend,
and frontend code with a README explaining wiring, setup, and how to run each
tier. The three artifacts evolve together: any change to the line protocol
touches the sketch, the relay and the app parser in lockstep (see the change
control note in `docs/api-specification.md`). The project also has exactly one
delivery date and a small team.

## Decision

Keep **one public repository** with three top-level folders — `firmware/`,
`backend/`, `frontend/` — plus `docs/` (this ADR series, the API
specification, the validation report, the flowcharts). Protocol changes,
firmware and app-side parser updates are committed together.

## Alternatives considered

| Option | Why not |
|---|---|
| Three separate repositories | The submission asks for one link; protocol changes would need coordinated commits across repos, with real risk of version skew. |
| One repo per tier plus submodules | Overhead without benefit at this team size; submodules complicate grading. |
| Single folder with mixed sources | Loses the tier structure the requirements explicitly ask for. |

## Consequences

- **Positive:** one clone gives the full project; the README, API spec and ADRs
  version with the code they describe; graders and the facilitator can verify
  deliverables (flowchart, ADRs, API spec, validation report) inside the repo
  and in the printed documentation.
- **Negative:** the repository contains three toolchains (Arduino IDE for two
  boards, Gradle for Android); the README must explain each, and `.gitignore`
  files must cover each toolchain's build output (they do).
- **Neutral:** hardware (schematics, photos, demo video) stays out of scope for
  version control beyond what the documentation references.
