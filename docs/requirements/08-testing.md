# Testing

Testing must validate the behaviour of the system. Tests must cover critical server-side logic, relevant parts of the GenAI component, and important client-side workflows and interactions.

Unit tests are mandatory for critical server and GenAI logic. Client-side tests should cover core workflows and interactions. All tests must run automatically in the CI pipeline. The pipeline should therefore act as the main enforcement point for system stability and should prevent broken changes from being merged.

| Aspect | Requirement |
| --- | --- |
| Unit Tests | Must cover critical server and GenAI logic |
| Client Tests | Should cover core workflows and interactions |
| CI Testing | All tests must run automatically in the CI pipeline |
