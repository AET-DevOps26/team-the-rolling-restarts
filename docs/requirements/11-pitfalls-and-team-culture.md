# 11 — Common Pitfalls & Team Culture

Guidance (not hard requirements), based on observation of past teams. Useful as a
sanity check on whether the project is being built the right way.

## Effective project

- **Reliability > feature count.** Feature orientation is a common cause of failure.
  Keep scope small, make the system **deployable early**, and iterate.
- **The system is a single pipeline.** Don't treat coding, deployment, and monitoring as
  separate things — link every component into one chain:
  `code → test → build → deploy → observe → improve`.
- **Reproducibility.** Ask after each feature: *"Can someone else run this without me?"*
  Eliminate manual configuration; test setup from scratch several times.
- **Visible system behaviour.** Dashboards must be linked to real system behaviour.
  Monitor **what can break** — latency, failures, load — not arbitrary data.

## Patterns of failure

- **Project as a checklist.** Treating requirements as boxes to tick tanks quality.
  Requirements are a **starting point**; add your own understanding on top and connect
  each to real behaviour.
- **Late integration.** The single most common regret. Building components separately and
  integrating at the end breaks CI/CD and leaves the system incomplete. **Integrate as
  early as possible.**
- **Fake CI/CD.** A pipeline that exists but has meaningless or missing tests. Follow
  good CI/CD practices from the start.
- **GenAI as decoration.** Integrating GenAI purely as a checkbox. Treat it as a real,
  connected capability (see [04-genai-component.md](04-genai-component.md)).
- **"I will document it later."** You forget your own code. Document **as you go** — start
  each class/method with a short purpose comment.

## Team culture

- **Others can't read your thoughts.** Raise concerns early; don't blame others for things
  you never communicated. Escalate to tutor/instructor if unresolved within the team.
- **Clear roles & responsibilities.** Define ownership and accountability early; a **RACI
  matrix** is recommended.
- **Individual strength.** Great teams complement each other — be eager to teach what you
  know and learn what you don't.
