# System Architecture

The system must be structured as a set of interacting but separated components. At minimum, this includes a client side, a server side, a database, and a separate GenAI component. The client side must provide a usable interface and communicate with the server over REST. The server side must expose REST APIs, coordinate business logic, and interact with persistent storage. The database must support persistent data storage and must have a documented schema. The GenAI component must run as an independent service and communicate with the remaining services over a defined interface.

The server side must be implemented in Spring Boot and must consist of at least three microservices. These services do not need to be large, but they must have distinct responsibilities and communicate in a controlled and documented way.

The client side may be implemented in React, Angular, or Vue.js. It must provide a usable and responsive interface and interact with the server over REST APIs. The database may be MySQL, PostgreSQL, or a similar relational or persistent database system, but it must run via Docker in local development and support documented persistent storage in the deployed setup.

| Component | Technology | Notes |
| --- | --- | --- |
| Client Side | React, Angular, Vue.js | Usable, responsive UI that interacts with server over REST |
| Server Side | Spring Boot (Java) | Must expose REST APIs and consist of at least 3 microservices; modular architecture required |
| Database | MySQL or PostgreSQL or similar | Must support persistent storage; schema must be documented; run via Docker |
