# k3s deployment

Helm chart and per-environment values for running the platform on k3s, using the images that CI publishes
to ECR (tagged with the commit SHA). The single-host Docker Compose flow in each service's `compose.yaml`
and `DEPLOYMENT_RUNBOOK.md` is unchanged and still used by the existing EC2 host.

```text
deploy/
  charts/service/              one Spring Boot service, optionally with its own MongoDB
  values/<env>/<service>.yaml  per-service settings for an environment (staging, qa)
```

Each Helm release is one service in one namespace. The database Service keeps the Compose host name
(for example `order-mongodb:27017`), so service configuration matches `compose.yaml`.

## One-time host setup (EC2, Amazon Linux 2023, us-east-1)

1. Launch an x86 instance (t3a.xlarge, 50 GB gp3) with the IAM instance profile `ec2-ecr-pull`
   (`AmazonEC2ContainerRegistryReadOnly`).
2. Install k3s:

   ```bash
   curl -sfL https://get.k3s.io | sudo INSTALL_K3S_EXEC="--write-kubeconfig-mode 644" sh -
   ```

3. Let k3s pull from ECR with the instance role. k3s reads the credential provider from these default paths:

   ```bash
   D=/var/lib/rancher/credentialprovider
   sudo mkdir -p $D/bin
   sudo curl -sfL -o $D/bin/ecr-credential-provider \
     https://artifacts.k8s.io/binaries/cloud-provider-aws/v1.37.0/linux/amd64/ecr-credential-provider-linux-amd64
   sudo chmod 755 $D/bin/ecr-credential-provider
   sudo tee $D/config.yaml >/dev/null <<'EOF'
   apiVersion: kubelet.config.k8s.io/v1
   kind: CredentialProviderConfig
   providers:
     - name: ecr-credential-provider
       matchImages:
         - "971422709527.dkr.ecr.us-east-1.amazonaws.com"
       defaultCacheDuration: "12h"
       apiVersion: credentialprovider.kubelet.k8s.io/v1
   EOF
   sudo systemctl restart k3s
   ```

4. Install Helm and point it at k3s:

   ```bash
   curl -fsSL https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 | bash
   echo 'export KUBECONFIG=/etc/rancher/k3s/k3s.yaml' >> ~/.bashrc
   ```

## Deploy or upgrade a service

Use the commit SHA of a `main` build whose publish job succeeded:

```bash
helm upgrade --install order-service deploy/charts/service -n staging --create-namespace \
  -f deploy/values/staging/order-service.yaml --set image.tag=<commit-sha> --wait
```

Roll back to the previous release with `helm rollback order-service -n staging`.

## Check it

```bash
kubectl get pods,svc,pvc -n staging
kubectl logs -n staging deploy/order-service
kubectl run smoke --rm -i --restart=Never -n staging --image=curlimages/curl:8.11.1 -- \
  curl -s order-service:8082/actuator/health
```

MongoDB data lives on a PersistentVolume that is kept when a release is uninstalled; delete the
`data-<name>-mongodb-0` PVC to start from an empty database.
