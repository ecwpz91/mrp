# Mars Rover Photos (MRP)

Quarkus image browser for NASA Mars rover photos. Volunteers and researchers can browse rovers, inspect mission metadata, and view random high-resolution camera shots—supporting citizen-science work around seasonal fans, blotches, spiders, and Swiss cheese terrain used to train and validate AI models.

## Prerequisites

- **JDK 17+**
- **Maven 3.9+** (or use the included Maven Wrapper: `./mvnw`, which downloads 3.9.11)
- A free [Mars Vista API key](https://marsvista.dev/) ([API docs](https://api.marsvista.dev/swagger/index.html))

## Configure the Mars Vista API key

NASA’s Mars Rover Photos API was retired. This app uses the [Mars Vista v2 API](https://marsvista.dev/docs/reference/photos).

Do not commit a real API key. Set it via environment variable (preferred):

```bash
export MARSVISTA_API_KEY=your-marsvista-api-key
```

Or create an untracked local override (already listed in `.gitignore`):

```bash
# edit src/main/resources/application-local.properties
# api.key=your-marsvista-api-key
```

Then run with the `local` profile:

```bash
./mvnw quarkus:dev -Dquarkus.profile=local
```

`application.properties` keeps a placeholder and reads `MARSVISTA_API_KEY` when set. Auth is sent as the `X-API-Key` header.

## Run locally

```bash
# from the repo root
export MARSVISTA_API_KEY=your-marsvista-api-key
./mvnw quarkus:dev
```

Open [http://localhost:8080](http://localhost:8080).

Useful paths:

| Path | Description |
|------|-------------|
| `/` | Landing page |
| `/rovers` | List all rovers from Mars Vista v2 (with cameras) |
| `/rover/{name}` | Rover detail with NASA Image Library highlights (e.g. `/rover/curiosity`) |
| `/photo/{name}?landingDate=YYYY-MM-DD&maxDate=YYYY-MM-DD` | Picks a random hazcam photo, then redirects to a stable URL |
| `/photo/{name}?earthDate=YYYY-MM-DD&photoId=…&camera=…` | Same photo on refresh (use Rovers → Random Photo for a new one) |
| `/q/health` | Health checks |
| `/q/metrics` | Prometheus metrics |

The photo page shows the Mars Vista hazcam image alongside a [Mars Trek](https://trek.nasa.gov/tiles/apidoc/trekAPI.html?body=mars) landing-site mosaic (HiRISE for Curiosity/Spirit/Opportunity; global Viking centered on Jezero for Perseverance). Trek tiles load from `trek.nasa.gov` in the browser—no NASA API key. The map is mission landing-area context labeled with the photo’s earth date/sol, not the rover’s exact position that day.

Rover detail pages also pull a few curated stills (selfies, landing, mission highlights) from the [NASA Image and Video Library](https://images-api.nasa.gov) search API—no API key required. Captions and credits link back to [images.nasa.gov](https://images.nasa.gov).

### Build and run the JAR

```bash
./mvnw clean package -DskipTests
java -jar target/quarkus-app/quarkus-run.jar
```

### Tests

```bash
./mvnw test
```

### Docker

```bash
docker build -t mrp:local .
docker run --rm -p 8080:8080 -e MARSVISTA_API_KEY=your-marsvista-api-key mrp:local
```

Prefer passing `MARSVISTA_API_KEY` at runtime so secrets stay out of image layers.

### Native image (optional)

```bash
./mvnw package -Dnative -DskipTests
```

## Project layout

```
src/main/java/com/redhat/mrp/
  client/                        # Mars Vista v2 REST client + mapping
  web/                           # JAX-RS + Qute resources
  model/                         # View models (rovers, photos, Trek context)
src/main/resources/
  templates/RoverResource/       # Qute HTML views
  META-INF/resources/            # Static HTML, CSS (Shards UI), images
  application.properties
deploy/                          # Kustomize app rollout (Deployment, Service, ConfigMap, Route)
  registry-pull-secret.yaml      # Optional one-shot registry pull secrets
  tkn/                           # Tekton / OpenShift Pipelines CI (build + deploy on push)
```

Stack: Java 17+, Quarkus 3.40 LTS (REST, Qute, REST Client, SmallRye Health, Micrometer/Prometheus).

## Deploy

Runtime objects are managed with [Kustomize](https://kustomize.io/) under [`deploy/`](deploy/). Edit `deploy/configmap.yaml` and set a real `api.key` before applying (do not commit it). Prefer a Secret for production. The Deployment mounts ConfigMap `mrp-app-config` at `/etc/mrp` and sets `QUARKUS_CONFIG_LOCATIONS` so Quarkus loads that `application.properties`.

Image builds are handled by the Tekton pipeline (Buildah), not an OpenShift BuildConfig.

### OpenShift (step by step)

Prerequisites: [`oc`](https://docs.openshift.com/container-platform/latest/cli_reference/openshift_cli/getting-started-cli.html) logged into a cluster (`oc login …`), and this repo cloned locally.

The published image is `quay.io/ecwpz91/mrp:latest`. Roll out with `oc apply -k deploy/` (Deployment, Service, ConfigMap, Route).

#### 1. Create the project

```bash
# from the repo root
oc new-project mrp
# or, if the project already exists:
# oc project mrp
```

#### 2. Registry pull secret (Quay)

If the Quay repository is private, create a pull secret so the project can pull `quay.io/ecwpz91/mrp`.

**Option A — interactive (recommended):**

```bash
oc create secret docker-registry quayio-reg \
  --docker-server=quay.io \
  --docker-username=<quay-username> \
  --docker-password=<quay-password-or-robot-token> \
  --docker-email=<email>
```

**Option B — apply the placeholder secret:** edit `deploy/registry-pull-secret.yaml` and replace `encrypted-password-here` with a base64-encoded Docker config JSON, then:

```bash
oc apply -f deploy/registry-pull-secret.yaml
```

Link the secret to the default service account so Deployments can pull the image:

```bash
oc secrets link default quayio-reg --for=pull
```

(The `docker-hub` secret in that file is optional and only needed if a build or base image pull requires Docker Hub credentials.)

#### 3. Import the Quay image into the project

This creates an ImageStream in the project and pulls/tags the remote image:

```bash
oc import-image mrp:latest \
  --from=quay.io/ecwpz91/mrp:latest \
  --confirm
```

Verify:

```bash
oc get imagestream mrp
oc describe imagestream mrp
```

#### 4. Deploy the app (`oc apply -k`)

Edit `deploy/configmap.yaml` and set a real `api.key` (do not commit it), then:

```bash
oc apply -k deploy/
```

This applies the ConfigMap, Deployment, Service, and Route. After pods are running:

```bash
oc get pods,svc,route
oc get route mars-rover-photos -o jsonpath='{.spec.host}{"\n"}'
```

Open `https://<route-host>/` in a browser.

For continuous build-and-deploy on every commit to `main`, use the Tekton pipeline under [`deploy/tkn/`](deploy/tkn/) (see [CI](#ci)).

### CI

Tekton manifests in [`deploy/tkn/`](deploy/tkn/) replace the former Jenkins pipeline. On each push to `main` on [ecwpz91/mrp](https://github.com/ecwpz91/mrp), an EventListener starts Pipeline `mrp-build-deploy`, which:

1. Clones the commit from GitHub (`git-clone`)
2. Builds and pushes `quay.io/ecwpz91/mrp:latest` (`buildah`)
3. Applies `deploy/` with Kustomize and rolls out Deployment `mars-rover-photos` (`openshift-client`)

#### Prerequisites

- OpenShift project with the app already deployed (steps 1–4 above)
- [Red Hat OpenShift Pipelines](https://docs.redhat.com/en/documentation/red_hat_openshift_pipelines/) operator installed (provides Tekton + Triggers and cluster tasks such as `git-clone`, `buildah`, and `openshift-client`)
- Quay pull/push secret `quayio-reg` in the project (step 2), with **push** credentials so Buildah can publish the image

#### Install the pipeline

```bash
# from the repo root, in project mrp
oc project mrp

# pipeline SA needs edit rights to apply deploy/ and restart the Deployment
oc apply -f deploy/tkn/rbac.yaml

# allow the pipeline SA to use the Quay secret for image push
oc secrets link pipeline quayio-reg --for=pull,mount

oc apply -f deploy/tkn/pipeline.yaml
oc apply -f deploy/tkn/triggers.yaml
```

Expose the EventListener so GitHub can reach it:

```bash
oc expose svc el-mrp-listener
oc get route el-mrp-listener -o jsonpath='{.spec.host}{"\n"}'
```

#### Wire GitHub to deploy on every commit

In the [ecwpz91/mrp](https://github.com/ecwpz91/mrp) repository: **Settings → Webhooks → Add webhook**.

| Field | Value |
| --- | --- |
| Payload URL | `https://<el-mrp-listener-route-host>/` |
| Content type | `application/json` |
| Secret | (optional) leave empty unless you add a GitHub interceptor secret |
| Events | Just the `push` event |

The trigger filters to `refs/heads/main`, so only commits on `main` start a PipelineRun. After you push to `main`:

```bash
tkn pipelinerun list
tkn pipelinerun logs -f
oc get pods -l app=mars-rover-photos
```

#### Manual run (no webhook)

```bash
oc create -f deploy/tkn/pipelinerun.yaml
tkn pipelinerun logs -f
```

## License

See [LICENSE](LICENSE).
