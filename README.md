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

- **Kubernetes**: apply manifests under `k8s/`
- **OpenShift**: see `openshift/` (build config, pipeline, ConfigMap). Put the API key in the ConfigMap or a Secret—not in git.
- **CI**: `Jenkinsfile` drives an OpenShift binary build

Update deploy manifests to run `target/quarkus-app/quarkus-run.jar` (or a Quarkus container image) instead of the old Spring Boot fat JAR name if they still reference `mrp-0.0.1.jar`.

## License

See [LICENSE](LICENSE).
