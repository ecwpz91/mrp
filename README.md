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
| `/rover/{name}` | Rover detail (e.g. `/rover/curiosity`) |
| `/photo/{name}?landingDate=YYYY-MM-DD&maxDate=YYYY-MM-DD` | Picks a random hazcam photo, then redirects to a stable URL |
| `/photo/{name}?earthDate=YYYY-MM-DD&photoId=…&camera=…` | Same photo on refresh (use Rovers → Random Photo for a new one) |
| `/q/health` | Health checks |
| `/q/metrics` | Prometheus metrics |

The photo page shows the Mars Vista hazcam image alongside a [Mars Trek](https://trek.nasa.gov/tiles/apidoc/trekAPI.html?body=mars) landing-site mosaic (HiRISE for Curiosity/Spirit/Opportunity; global Viking centered on Jezero for Perseverance). Trek tiles load from `trek.nasa.gov` in the browser—no NASA API key. The map is mission landing-area context labeled with the photo’s earth date/sol, not the rover’s exact position that day.

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
k8s/                             # Kubernetes manifests
openshift/                       # OpenShift / Jenkins CI assets
```

Stack: Java 17+, Quarkus 3.40 LTS (REST, Qute, REST Client, SmallRye Health, Micrometer/Prometheus).

## Deploy

### Kubernetes

Apply the ConfigMap first (so the Deployment can mount it), then the runtime manifests:

```bash
# edit openshift/configmap.yaml and set a real api.key (do not commit it)
kubectl apply -f openshift/configmap.yaml
kubectl apply -f k8s/
```

`k8s/deployment.yaml` mounts ConfigMap `mrp-app-config` at `/etc/mrp` and sets `QUARKUS_CONFIG_LOCATIONS` so Quarkus loads that `application.properties` (including `api.key`).

### OpenShift (step by step)

Prerequisites: [`oc`](https://docs.openshift.com/container-platform/latest/cli_reference/openshift_cli/getting-started-cli.html) logged into a cluster (`oc login …`), and this repo cloned locally.

The published image is `quay.io/ecwpz91/mrp:latest`. Runtime manifests live under `k8s/`; OpenShift helpers (registry secrets, ConfigMap, BuildConfigs) live under `openshift/`.

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

**Option B — apply the placeholder secret:** edit `openshift/quayioreg.yaml` and replace `encrypted-password-here` with a base64-encoded Docker config JSON, then:

```bash
oc apply -f openshift/quayioreg.yaml
```

Link the secret to the default service account so Deployments can pull the image:

```bash
oc secrets link default quayio-reg --for=pull
```

(`openshift/dockerhub.yaml` is optional and only needed if a build or base image pull requires Docker Hub credentials.)

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

#### 4. Apply app configuration (API key)

Edit `openshift/configmap.yaml` and set a real `api.key` (do not commit it), then:

```bash
oc apply -f openshift/configmap.yaml
```

Prefer a Secret for production. The Deployment mounts this ConfigMap; apply it before (or with) the Deployment so the pod can start.

#### 5. Deploy the app (`oc apply`)

```bash
oc apply -f k8s/deployment.yaml
oc apply -f k8s/service.yaml
oc apply -f k8s/route.yaml
```

Or apply the whole directory:

```bash
oc apply -f k8s/
```

`k8s/deployment.yaml` pulls `quay.io/ecwpz91/mrp:latest`, mounts ConfigMap `mrp-app-config` as `/etc/mrp/application.properties`, and points Quarkus at it via `QUARKUS_CONFIG_LOCATIONS`. After pods are running:

```bash
oc get pods,svc,route
oc get route mars-rover-photos -o jsonpath='{.spec.host}{"\n"}'
```

Open `https://<route-host>/` in a browser.

#### 6. Optional: in-cluster image builds

To build from this repo’s `Dockerfile` and push to Quay (requires Quay push credentials on the builder service account):

```bash
oc apply -f openshift/buildcfg.yaml
oc secrets link builder quayio-reg --for=pull,mount
oc start-build mars-rover-photos --from-dir=. --follow
```

To wire the Jenkins pipeline BuildConfig (cluster must have OpenShift Pipelines / Jenkins integration as expected by `Jenkinsfile`):

```bash
oc apply -f openshift/pipeline.yaml
```

### CI

`Jenkinsfile` drives an OpenShift binary build against BuildConfig `mars-rover-photos` (see `openshift/buildcfg.yaml` and `openshift/pipeline.yaml`).

## License

See [LICENSE](LICENSE).
