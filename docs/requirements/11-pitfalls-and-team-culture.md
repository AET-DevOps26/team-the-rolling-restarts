# Common pitfalls and how to avoid them

Creating a project in a team with other people, even if they are your friends, is not easy. Truly effective collaboration with people who have distinct views, skills, and working styles requires significant effort in order for it to work well. To help you with it, we would like to provide recommendations based on personal experience from continuous observation of different teams, as well as general good practices.

> A smart man learns from his mistakes, a wise man learns from the mistakes of others.

## Effective project

### Reliability > Feature count

One of the very common reasons for a team to fail is feature orientation. It might seem reasonable that the best strategy is to build as many features as possible in the shortest time. But quality can never beat quantity this way. If you cannot reliably deploy or run every new feature you create, you will eventually fail with the whole flow of project execution. One of the important DevOps principles is that fast, reliable flow is more important than feature count. We recommend the following strategy: keep the scope small, make the system deployable early, and iterate on it.

### The system is a single pipeline

Every part of the system is a different component, but they are all interconnected and dependent on each other. A big mistake is to think about coding, deployment, and monitoring as three separate components — the system you create with this strategy would end up just a codebase. The best practice you can implement is to link every component of the system into one chain: code → test → build → deploy → observe → improve

### Reproducibility

The joke "it works on my machine" is as old as time. This might create the impression that this problem is no longer relevant or already solved by someone. In reality, this is still one of the major problems for people who do not keep reproducibility in mind at all times. Try to ask yourself after each feature is implemented: "Can someone else run your system without you?" Typical failures include many manual steps and undocumented environments. The best advice is to make setup trivial: try to eliminate manual configuration and make sure to test the setup yourself from scratch at least a couple of times.

### Visible system behaviour

Monitoring might seem like the easiest and most fun part of the responsibilities. In reality, it requires significant effort, understanding of system functionality, awareness of common pitfalls, understanding your system's drawbacks, and an analytical mindset to create truly useful monitoring. Many teams "install monitoring", but dashboards show nothing useful. Remember that all dashboards must be linked to system behaviour. You need to be able to monitor what can break. Visualisation of latency, failures, and load will help you and your team understand the system state, instead of collecting data without purpose.

## Patterns of failure

### Project as a checklist

The easiest way to think about the project is to treat it as a collection of checkpoints you need to mark. Required to have dashboards and alerts? Install Prometheus, add 1 dashboard, add 1 alert ✅ done. If you only aim to pass the requirement, the quality of the system drops drastically. Requirements should serve only as a starting point, on top of which you add your own ideas, understanding, and experience. When you think about the project only as a collection of checkpoints, you distance yourself from it and forget its purpose and logical functionality. Try to give every requirement meaning and connect it to real system behaviour.

### Late integration

What fails most projects one week before submission? Late integration. To the question "If you could start the project again, what would you do differently?", the most common answer is: "I would start integration much earlier." Too many teams build components separately and integrate them at the very end. Every time the result is the same: CI/CD breaks, deployment becomes unstable, and eventually the system is incomplete. Try to follow this recommendation and start integration as early as possible with your team.

### Fake CI/CD

It's easy to have CI/CD, but it's extremely hard to have good CI/CD. Very often the pipeline exists, but tests are meaningless or missing. It is easy to get frustrated if your pipeline runs too long, fails randomly, or requires manual approval for small changes. Try to make your life easier early and follow good CI/CD practices from the beginning: <https://about.gitlab.com/blog/how-to-keep-up-with-ci-cd-best-practices/>

### GenAI as decoration

GenAI might not be everyone's cup of tea. If you are not interested in working with this technology, it might be a frustrating experience, and you might catch yourself integrating GenAI just as a checkbox. In reality, when working on industrial projects, you often need to work with things you would not choose yourself. A very important quality of a DevOps engineer is flexibility and willingness to learn. Use this opportunity to learn a new technology and think about real challenges modern teams are solving.

### "I will document it later"

Needless to say, we all fall into this trap. You spend hours debugging a feature, finally fix it, push, commit… and then: "I will document it tomorrow." Then the next day you come back and no longer fully understand your own code. Always start a new class or method with a short comment describing its purpose. This helps structure your thinking and understand what you are doing. Try to document as you go and make it a habit. It helps not only you, but also anyone who looks at your code later, and improves maintainability.

<https://www.aleksandrhovhannisyan.com/blog/writing-better-documentation/>

## Team culture

A good project is not built by perfect individuals working all by themselves. The best teams consist of people who communicate effectively, take responsibility, and support each other when needed.

### Other people cannot read your thoughts

If something is bothering you, bring it up for discussion as soon as possible. The earlier you do it, the easier it is to resolve. Otherwise, issues accumulate over time and can lead to frustration. At the same time, do not blame others for things you never communicated as concerns. If you encounter a problem you cannot resolve within your team, contact your tutor or instructor.

### Clear roles and responsibilities

It is easy to avoid responsibility if you do not clearly understand what you are responsible for. Right after forming a team, one of the first things to discuss is individual responsibilities. Try to define roles similar to those in real projects, and throughout the project execution define responsibilities and accountability for various tasks. We recommend using a RACI matrix for this: <https://www.atlassian.com/work-management/project-management/raci-chart>

### Individual strength

Everyone in a team has different strengths and weaknesses. Some may already have industrial experience and a strong technical background, others are strong in theory, and others are better at planning and communication. Good teams are technically strong, but great teams consist of people who complement each other. We would like you to use this opportunity to work with different people, and being eager to teach what you know, and learn what you do not.
