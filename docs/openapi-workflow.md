# OpenAPI Workflow

This repository uses a small set of commands to lint the OpenAPI spec, generate client/server artifacts, and wire those steps into `pre-commit`.

## Install dependencies

Install the tooling from the repository root first:

```bash
npm ci
```

That installs the Node-based generators used by [`api/scripts/gen-all.sh`](../api/scripts/gen-all.sh).

For the Python generator used by the GenAI service, install its dev dependencies and the OpenAPI client generator:

```bash
cd services/gen-ai
python -m venv .venv
source .venv/bin/activate
pip install -e ".[dev]"
python -m pip install openapi-python-client
```

If you plan to run `pre-commit`, install it once in your environment:

```bash
python -m pip install pre-commit
```

## Register pre-commit hooks

The repo defines two hooks in [`.pre-commit-config.yaml`](../.pre-commit-config.yaml):

- `redocly-lint` to lint [`api/openapi.yaml`](../api/openapi.yaml)
- `generate-api-clients` to run [`api/scripts/gen-all.sh`](../api/scripts/gen-all.sh)

Install the hooks once with:

```bash
pre-commit install
pre-commit install --hook-type post-checkout
pre-commit install --hook-type post-merge
```

The extra hook types matter because `generate-api-clients` is configured for `post-checkout` and `post-merge`. You do not need to re-run them unless you want to re-register or repair the local hooks.

## Lint the OpenAPI spec

Run the spec lint directly with the same command used in CI:

```bash
npx @redocly/cli lint api/openapi.yaml
```

You can also run just the pre-commit hook:

```bash
pre-commit run redocly-lint --files api/openapi.yaml
```

To run all registered hooks, including the OpenAPI lint hook, use:

```bash
pre-commit run -a
```

## Generate the code

The repo generates three outputs from the OpenAPI definition:

- Spring server/client artifacts in [`services/spring-api/generated`](../services/spring-api/generated)
- Python client artifacts in [`services/gen-ai/generated`](../services/gen-ai/generated)
- TypeScript client types in [`web-client/src/generated/api.ts`](../web-client/src/generated/api.ts)

Run the generator script from the repository root:

```bash
./api/scripts/gen-all.sh
```

If you want to trigger the pre-commit hook manually, use the same stage it is registered for:

```bash
pre-commit run generate-api-clients --hook-stage post-checkout --all-files
```

## Notes

- [`services/spring-api`](../services/spring-api) is a Gradle project and uses the generated OpenAPI source root during its normal build.
- [`services/gen-ai`](../services/gen-ai) currently has no collected tests, so `pytest` exits with code `5` and CI treats that as a skip.
- The OpenAPI generator version is pinned in [`openapitools.json`](../openapitools.json).

## Example usages

Use the lint command when you want to verify the spec before committing changes:

```bash
npx @redocly/cli lint api/openapi.yaml
```

Use the generator script after changing the OpenAPI definition to refresh all generated outputs:

```bash
./api/scripts/gen-all.sh
```

Use the pre-commit hook directly when you want to reproduce the post-checkout or post-merge generation step:

```bash
pre-commit run generate-api-clients --hook-stage post-checkout --all-files
```

Use the full hook set when you want to run the repository checks locally in one pass:

```bash
pre-commit run -a
```

Use the generated Python client when you want to call the API from `services/gen-ai` without guessing the import paths:

```python
from open_api_definition_client.client import AuthenticatedClient
from open_api_definition_client.api.root_controller import dummy, root, test

with AuthenticatedClient(base_url="http://localhost:8080", token="dev-token") as client:
    dummy_message = dummy.sync(client=client)
    root_message = root.sync(client=client)
    test_message = test.sync(client=client)

    print(dummy_message.message if dummy_message else "no response")
    print(root_message.message if root_message else "no response")
    print(test_message.message if test_message else "no response")
```

Use the async client variant when you are already inside an async workflow:

```python
from open_api_definition_client.client import AuthenticatedClient
from open_api_definition_client.api.root_controller import dummy


async def fetch_dummy_message() -> None:
    async with AuthenticatedClient(base_url="http://localhost:8080", token="dev-token") as client:
        message = await dummy.asyncio(client=client)
        print(message.message if message else "no response")
```

Use the generated Spring interface as the stub contract when you implement the server side:

```java
package rolling_restarts.server.controller;

import org.openapitools.api.DummyApi;
import org.openapitools.model.Message;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RootController implements DummyApi {

    @Override
    public ResponseEntity<Message> dummy() {
        Message message = new Message();
        message.setMessage("Dummy response");
        return ResponseEntity.ok(message);
    }
}
```

Use the generated TypeScript types when you want to type a fetch response in `web-client`:

```ts
import type { components } from "@/generated/api";

type Message = components["schemas"]["Message"];

async function loadDummyMessage(): Promise<Message> {
    const response = await fetch("/dummy");

    if (!response.ok) {
        throw new Error(`Request failed with status ${response.status}`);
    }

    return (await response.json()) as Message;
}
```
