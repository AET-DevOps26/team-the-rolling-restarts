# Development Workflow

The project must be developed in a GitHub mono-repository. Therefore, the system must be treated as one integrated deliverable. A mono-repo makes it possible to version client, server, GenAI service, deployment files, CI/CD workflows, and documentation together, and to validate changes across the whole system.

All work must be structured through pull requests. Each feature or bugfix must be developed in a dedicated feature branch. Direct commits to the main branch are not acceptable as a normal workflow. A pull request must be opened, reviewed, and approved before the change is merged into main. Team members must peer-review each other's work. Review is part of the workflow and should be treated as a normal step before merging changes.

The CI pipeline must run automatically on every pull request. At a minimum, it must build the relevant services and execute the automated tests. On merge to main, the CD pipeline must automatically deploy the system to a Kubernetes environment. The intended workflow is therefore: develop in a feature branch, validate through CI, review through PR, merge into main, and deploy automatically.

| Aspect | Requirement |
| --- | --- |
| Repository | GitHub mono-repo |
| Branching | Each feature or bugfix developed in a feature branch |
| Pull Requests | Mandatory before merge into main |
| Code review | Peer review and approval by team members required |
| CI checks | Automated tests and validation on every PR |
| CD behaviour | Automatic deployment to Kubernetes on merge to main |
