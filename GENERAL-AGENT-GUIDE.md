# General Agent Guide for Backend Project Work

## Purpose

Use this guide when briefing an AI coding agent to act as a disciplined
backend-project partner. It describes the workflow, quality bar, repository
habits, branching strategy, and handoff format that should be applied to a new
project regardless of programming language or framework.

The agent is expected to deliver working, verified, documented increments.
It should not merely propose code or report that a build passes.

## Initial briefing

Before changing anything, the agent should:

1. Read the project's README, contribution instructions, architecture notes,
   progress tracker, and any existing handoff file.
2. Inspect the repository structure, build files, test layout, deployment
   configuration, and current Git status.
3. Identify the active branch, its merge base, recent commits, existing tags,
   and any uncommitted or untracked files.
4. Determine the next incomplete milestone from the authoritative project
   tracker.
5. State assumptions and ask one focused question when a design or scope
   decision materially affects the implementation.

The agent must treat the repository's handoff or project guide as authoritative
context, but must not assume that it is current without checking the actual
working tree and Git history.

## Core operating principles

### Implement the complete slice

For each requested feature, investigate and wire every relevant surface:

- domain model and business rules;
- application/use-case logic;
- ports, interfaces, or contracts;
- persistence and migrations;
- message producers and consumers;
- HTTP or other public adapters;
- authentication and authorization;
- configuration and deployment;
- tests at the appropriate levels;
- documentation and reproducible verification.

Do not stop after adding a class or endpoint if the feature requires changes in
other layers.

### Preserve boundaries

Follow the project's existing architecture and naming conventions. Prefer
existing helpers, abstractions, and patterns over duplicating logic. Keep
business policy independent from transport, persistence, and infrastructure
where the architecture calls for that separation.

### Make errors explicit

Do not use broad exception catches, silent fallbacks, invalid-input defaults,
or success-shaped error responses. Errors should be visible through the
project's standard exception, logging, notification, or problem-response
mechanism.

### Prefer root-cause fixes

Make precise, surgical changes. Do not fix unrelated pre-existing issues.
When a change reveals a tightly coupled defect, fix it as part of the same
coherent change. Avoid speculative refactors and risky shortcuts.

### Preserve type and interface safety

Use proper types, guards, and existing contracts. Avoid unnecessary casts,
reflection, global state, and hidden coupling. Match the language's idioms and
the repository's formatting and linting rules.

## Milestone workflow

Every milestone should follow this sequence.

### 1. Define the milestone

Write down:

- the user-visible or operational capability;
- the business rule or engineering problem being addressed;
- the scope and deliberate non-goals;
- the acceptance criteria;
- the evidence required to call it complete.

Use the existing progress tracker rather than inventing a second source of
truth.

### 2. Explore before editing

Search for:

- similar features and prior art;
- relevant domain types and use cases;
- existing tests and fixtures;
- persistence schemas and migrations;
- configuration and deployment wiring;
- documentation patterns;
- existing failure and error handling.

Read enough surrounding context to understand how the change is supposed to
fit. Batch independent file reads and avoid repeatedly rereading unchanged
files.

### 3. Implement the vertical slice

Build the smallest complete path through the system. For a backend feature,
this commonly includes:

1. domain behavior;
2. application policy;
3. adapter contract;
4. storage or messaging implementation;
5. public API or event handler;
6. configuration;
7. automated tests.

Use migrations for schema changes. Do not edit a live database manually and
leave the repository unable to reproduce the state.

### 4. Add behavior-focused tests

Tests should prove behavior, not just execution:

- normal success paths;
- invalid input;
- authorization failures;
- dependency failure;
- duplicate or retried requests;
- boundary values;
- state-transition rules;
- message redelivery where applicable.

Choose the smallest test level that proves each rule, then add integration or
end-to-end coverage for interactions that unit tests cannot prove.

### 5. Verify with the real system

Use the smallest relevant validation first:

- formatter or linter;
- targeted unit tests;
- module test suite;
- integration tests;
- architecture tests;
- build or type-check;
- end-to-end workflow through the real public entry point.

For distributed behavior, verify the actual database, broker, cache, gateway,
or deployment environment when the milestone claims those components work.

Do not mark a milestone complete because compilation succeeds or because a
mock-only test passes.

### 6. Capture evidence

Record reproducible commands and observable results:

- test counts and failures;
- API requests and responses;
- migration version;
- message or outbox state;
- cache behavior;
- query plans and benchmark parameters;
- metrics, traces, or dashboard observations;
- deployment and recovery output.

Negative results are valid evidence. If an optimization does not improve the
measurement, document that honestly rather than presenting an expected result
as a fact.

### 7. Update project documentation

Update only directly related documentation, normally:

- README feature or roadmap status;
- progress tracker;
- milestone E2E guide;
- architecture decision record for a significant trade-off;
- known limitations when behavior or constraints changed.

Documentation should explain how to reproduce the result, what was verified,
and why the implementation was chosen.

### E2E guide format

Give every milestone its own reproducible E2E guide, named consistently (for
example `docs/e2e/e2e-v<major>.<minor>.<patch>.md`) and indexed from a single
`docs/e2e/README.md`. Each guide should describe **only what that milestone
introduced**; when a later milestone supersedes earlier behavior, fold the
older guide's still-relevant content into the newest one and remove the
superseded guide rather than keeping two guides in disagreement.

Use this structure for every guide:

1. **Title and one-paragraph summary** — the milestone name and what is being
   proven.
2. **Pre-conditions** — bullet list of what must already be true: installed
   tools, running infrastructure, prior passing commands, required state.
3. **Steps** — numbered sections, each with a short heading, copy-pasteable
   commands (`bash`/`powershell`/`curl`), and an inline `# expect: ...` comment
   stating the exact expected result so the step is self-checking.
4. **Results** — a table (or metrics block) with the concrete captured
   numbers: counts, scores, latencies, before/after values. Never state a
   result without a real run backing it; a negative or inconclusive result is
   still a valid, honestly documented result.
5. **Post-conditions** — the observable end state that proves the milestone
   works, phrased as verifiable facts.
6. **Gotchas** — scope limitations, known caveats, environment quirks, and
   anything a future agent must not misinterpret as a broader guarantee.
7. **Interview talking points** — 2-4 short bullets explaining the underlying
   engineering concept in plain language, aimed at articulating *why* the
   milestone matters, not just *what* it did.

Keep the index table (`docs/e2e/README.md`) in sync: one row per tag with the
guide link and a one-line description of verified scope. Do not reference a
temporary handoff file from an E2E guide or README.

### 8. Commit and tag the milestone

Before committing:

- inspect the full staged diff;
- exclude secrets, credentials, generated build output, reports, and temporary
  files;
- confirm the intended files only are staged;
- check for unexpected changes.

Use the repository's commit convention, normally Conventional Commits. Keep
the subject concise and explain intent in the body when the change is
non-trivial. Include the repository-required co-author trailer if configured.

Create a version tag only after implementation, tests, E2E verification, and
documentation are all complete. The tag must point to the documentation-
inclusive milestone tip.

Never amend a commit, force-push, skip hooks, or overwrite a tag without
explicit approval.

## Testing and quality policy

### Layered test strategy

Use a balanced test pyramid:

- **Unit tests:** domain invariants and application policies.
- **Contract/API tests:** request validation, response shapes, and permissions.
- **Integration tests:** real persistence, cache, broker, and transaction
  behavior.
- **End-to-end tests:** critical workflows through the real entry point.
- **Architecture tests:** dependency direction and module boundaries.

### Coverage interpretation

Line coverage indicates which code executed. It does not show whether the
assertions detect incorrect behavior.

Mutation testing should target business-critical application/domain packages
where unit tests are the right proof. Report results per service or bounded
module. A broad aggregate across infrastructure-heavy code can be misleading
because it may measure missing integration coverage rather than test strength.

Record:

- mutations generated;
- killed mutations;
- survived mutations;
- no-coverage mutations;
- mutation score;
- the exact scope and command.

Close meaningful survived or no-coverage mutants with tests that express the
missing behavior. Do not weaken the mutation configuration merely to produce a
better number.

## Distributed-systems checklist

For every remote call or asynchronous workflow, explicitly decide:

- timeout and retry budget;
- backoff and maximum attempts;
- circuit-breaker or fail-fast behavior;
- fail-open versus fail-closed policy;
- idempotency key or duplicate-detection mechanism;
- ordering and out-of-order handling;
- transaction boundary;
- event publication guarantee;
- replay and dead-letter behavior;
- observability for failure and recovery.

For every cache, decide:

- source of truth;
- cache key and TTL;
- invalidation trigger;
- stale-data policy;
- behavior during cache outage;
- stampede or concurrency protection if required.

## Security checklist

- Define the authentication mechanism and trust boundary.
- Define roles or permissions before writing route rules.
- Enforce authorization at every relevant boundary.
- Validate all external input.
- Keep secrets out of source control and logs.
- Use least-privilege database, broker, and deployment credentials.
- Return safe, stable errors without leaking internals.
- Test 401, 403, valid access, and direct-boundary access where applicable.
- Make security behavior observable without logging credentials or tokens.

## Performance case-study workflow

When adding a performance milestone:

1. Select one concrete bottleneck.
2. Define the workload, dataset shape, concurrency, duration, and success
   threshold.
3. Capture a baseline.
4. Inspect the query plan or runtime profile.
5. Make one focused change.
6. Run the same workload under comparable conditions.
7. Compare latency, throughput, errors, resource use, and plan changes.
8. Document the result, including a negative or inconclusive result.

Do not compare incomparable runs or claim causality from a single unexplained
number.

## Operational readiness

A production-oriented backend should make it possible to answer:

- Is the service alive and ready for traffic?
- What is the request rate and error rate?
- What is the latency distribution?
- Which dependency is slow or failing?
- Are messages delayed, duplicated, or dead-lettered?
- Is the database or cache saturated?
- Can one request be traced across components?
- How is the service restarted, rolled back, migrated, and recovered?

Add health probes, structured logs, metrics, traces, resource limits, graceful
shutdown, and recovery instructions as the project grows.

## Branching and worktree strategy

Keep project work isolated and make branch ownership explicit.

### Recommended branch layout

```text
main
  └── feature/<project-or-language>
        ├── agents/<task-or-session>
        └── feature/<milestone-or-topic>   (optional)
```

- `main` is protected and should not be changed directly by an agent.
- `feature/<project-or-language>` is the user's durable project branch.
- `agents/<task-or-session>` is an isolated implementation branch or worktree
  used for one focused task.
- A milestone branch is useful when a larger feature needs multiple reviewable
  commits, but it should merge back into the durable feature branch rather than
  bypassing it.

### Starting a new project in the same repository

If a new language or project is initialized in the same repository:

1. Start from the intended base branch, usually the latest `main` or the
   existing project branch selected by the user.
2. Create a distinct durable branch such as
   `feature/go-order-platform`, `feature/typescript-catalog`, or
   `feature/python-payments`.
3. Use a separate worktree for parallel agent work when possible.
4. Do not mix unrelated project files with the current project's milestone
   commit.
5. Keep project-specific README, progress, E2E guides, and tags scoped to the
   new branch and naming convention.
6. Confirm the branch and worktree before editing.

Example:

```powershell
git fetch origin
git switch main
git pull --ff-only origin main
git switch -c feature/go-order-platform
```

If the new project intentionally builds on the current project branch, state
that dependency and use a clear branch name. Do not silently fork from a
stale or dirty working tree.

### Propagating an isolated agent branch

When work is complete in an isolated branch:

1. Inspect the commit list and verify the target branch does not already
   contain the commits.
2. Prefer a fast-forward when the target branch is strictly behind and the
   history is intentionally linear.
3. Otherwise cherry-pick the milestone commits in dependency order or merge
   through the repository's normal review process.
4. Resolve conflicts by preserving both the target branch's newer work and the
   milestone's intended behavior.
5. Re-run the relevant tests after propagation.
6. Recreate or verify tags only after confirming the final commit.
7. Push only when the user explicitly asks or the workflow grants approval.

Useful checks:

```powershell
git merge-base --is-ancestor <target-branch> <agent-branch>
git log --oneline <target-branch>..<agent-branch>
git diff <target-branch>...<agent-branch>
```

### Tag discipline

- Tags are local until pushed.
- A tag should identify one verified, documented milestone.
- Never silently move a published tag.
- If a local tag must be corrected, delete and recreate it only with explicit
  user intent, then report the old and new commit.
- If the user asks to delete a tag, clarify whether local, remote, or both are
  intended before deleting a remote tag.

## Handoff file strategy

Maintain a temporary handoff file when work spans sessions or agents. It
should contain:

- repository and branch state;
- current milestone and acceptance status;
- commits and tags created;
- tests and E2E evidence;
- runtime recovery commands;
- known limitations and generated-artifact warnings;
- exact next steps and open decisions.

The handoff file is session context, not product documentation. Keep it
untracked unless the user explicitly requests otherwise. Never reference a
temporary handoff file from public README or milestone documentation.

At the end of a session, refresh it to the actual current state. Do not leave
completed work described as pending or report an old score after a follow-up
test has changed the result.

## Completion checklist

Before telling the user a milestone is complete, confirm:

- [ ] Scope and acceptance criteria are clear.
- [ ] The complete implementation path is wired.
- [ ] Failure, boundary, and security cases are handled.
- [ ] Targeted tests pass.
- [ ] Relevant integration and architecture tests pass.
- [ ] The real E2E path was verified when claimed.
- [ ] Performance evidence is comparable when relevant.
- [ ] Documentation and progress tracking are updated.
- [ ] Generated artifacts and secrets are excluded.
- [ ] The commit follows repository conventions.
- [ ] The tag points to the final documented commit.
- [ ] The handoff reflects the current state.
- [ ] The user knows what was changed, what was verified, and what remains.

The agent's standard is: implement the behavior, prove the behavior, document
the reasoning, and leave the next agent with an accurate starting point.
