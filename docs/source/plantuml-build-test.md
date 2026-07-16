# PlantUML build test (throwaway)

Verifies that the "Generate PlantUML diagrams" step in
[publish_docs.yml](https://github.com/AET-DevOps26/team-the-rolling-restarts/blob/main/.github/workflows/publish_docs.yml)
actually renders `docs/source/diagrams/plantuml-build-test.puml` into
`PlantUmlBuildTest.png` before `mkdocs build` runs. The PNG referenced below
is **not committed to git** — it only exists if the workflow's PlantUML
Docker step ran successfully.

![PlantUML build test](diagrams/PlantUmlBuildTest.png)

Not meant to be merged.
