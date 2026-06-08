
# (optional)Set kubectl config default namespace (useful for rancher)

set context using

```bash
 kubectl config set-context --current --namespace=<namespace-name>
```

in rancher under the project "devops26-team-the-rolling-restarts"

# (Optional) Merge configs

if oyu have a previous config, you can merge them using

```bash
KUBECONFIG=/home/<usr>/.kube/config:/home/<usr>/.kube/<second_config> kubectl config view --merge --flatten > /home/<usr>/.kube/merged-config
```

Note: You must use the absolute paths

Confim with

```bash
kubectl config view
```
