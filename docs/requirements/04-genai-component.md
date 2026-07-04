# GenAI Component

The GenAI component must be implemented as a separate service in Python. It must be deployed as a modular microservice, containerised independently, networked with the server, and integrated through a defined interface.

Functionally, the GenAI component must fulfil a real user-facing use case. Acceptable examples include summarisation, generation, question answering, or a similarly meaningful feature that is accessible through the application workflow. It is not sufficient to include a GenAI service that exists technically but is not connected to an actual user-facing capability.

The system must support both cloud-based and local large language models. Cloud support may be implemented through providers such as the OpenAI API. Local model support may be implemented using technologies such as GPT4All or LLaMA. Teams do not need to demonstrate sophisticated model research, but they do need to demonstrate that the service architecture can work with both remote and local inference options where feasible.

As an optional advanced bonus, teams may implement a full retrieval-augmented generation setup using a vector database such as Weaviate.

| Aspect | Requirement |
| --- | --- |
| Language | Python |
| Deployment | Modular microservice, containerised and networked with the server |
| Functionality | Real user-facing use case, e.g. summarisation, generation, Q&A |
| Model support | Cloud-based models (e.g. OpenAI API) and local models (e.g. GPT4All, LLaMA) |
| Optional bonus | Full RAG architecture using a vector database such as Weaviate |
