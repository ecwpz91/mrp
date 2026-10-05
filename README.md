# Mars Rover Photos (MRP)

Spring Boot image browser for NASA Mars rover photos. Volunteers and researchers can browse rovers, inspect mission metadata, and view random high-resolution camera shots—supporting citizen-science work around seasonal fans, blotches, spiders, and Swiss cheese terrain used to train and validate AI models.

## Prerequisites

- **JDK 11+**
- **Maven 3.6+** (or use the included Maven Wrapper: `./mvnw`)
- A free [Mars Vista API key](https://marsvista.dev/) ([API docs](https://api.marsvista.dev/swagger/index.html))

## Configure the Mars Vista API key

NASA’s Mars Rover Photos API was retired. This app uses the [Mars Vista v2 API](https://marsvista.dev/docs/reference/photos).

Do not commit a real API key. Set it via environment variable (preferred):

```bash
export MARSVISTA_API_KEY=your-marsvista-api-key
```

Or create an untracked local override (already listed in `.gitignore`):

```bash
cp src/main/resources/application.properties src/main/resources/application-local.properties
# edit application-local.properties and set api.key=your-marsvista-api-key
```

Then run with the `local` profile:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

`src/main/resources/application.properties` keeps a placeholder and reads `MARSVISTA_API_KEY` when set. Auth is sent as the `X-API-Key` header (not a query parameter).

## Run locally

```bash
# from the repo root
export MARSVISTA_API_KEY=your-marsvista-api-key
./mvnw spring-boot:run
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

The photo page shows the Mars Vista hazcam image alongside a [Mars Trek](https://trek.nasa.gov/tiles/apidoc/trekAPI.html?body=mars) landing-site mosaic (HiRISE for Curiosity/Spirit/Opportunity; global Viking centered on Jezero for Perseverance). Trek tiles load from `trek.nasa.gov` in the browser—no NASA API key. The map is mission landing-area context labeled with the photo’s earth date/sol, not the rover’s exact position that day.

### Build and run the JAR

```bash
./mvnw clean package -DskipTests
java -jar target/mrp-0.0.1.jar
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

Note: the image bakes in `application.properties`. Prefer passing `MARSVISTA_API_KEY` at runtime so secrets stay out of the image layers you push.

## Project layout

```
src/main/java/com/redhat/mrp/
  RoverClientApplication.java   # Spring Boot entry + RestTemplate bean
  client/                        # Mars Vista v2 HTTP client + mapping
  controller/                    # MVC endpoints
  model/                         # View models (rovers, photos, Trek context)
src/main/resources/
  templates/                     # Thymeleaf views
  static/                        # HTML, CSS (Shards UI), images
k8s/                             # Kubernetes manifests
openshift/                       # OpenShift / Jenkins CI assets
```

Stack: Java 11, Spring Boot 2.5.3 (Web, Thymeleaf, Actuator), Micrometer/Prometheus, optional H2 on the classpath (not used by the current controllers).

## Deploy

- **Kubernetes**: apply manifests under `k8s/`
- **OpenShift**: see `openshift/` (build config, pipeline, ConfigMap). Put the API key in the ConfigMap or a Secret—not in git.
- **CI**: `Jenkinsfile` drives an OpenShift binary build

## Quarkus migration notes

This app is a good Quarkus candidate: one MVC controller, POJO models, Thymeleaf views, and outbound HTTP to Mars Vista—no JPA, security, or messaging.

| Area | Current | Quarkus approach | Effort |
|------|---------|------------------|--------|
| Build | `spring-boot-starter-parent` 2.5.3 | Quarkus BOM + `quarkus-maven-plugin` | Low |
| Entry point | `@SpringBootApplication` | Remove; Quarkus bootstraps via extensions | Low |
| MVC | `@Controller` + view names | Prefer JAX-RS + Qute (`quarkus-rest-qute`), or Spring compatibility extensions as a bridge | Medium |
| Templates | Thymeleaf (`th:*`) | Migrate to [Qute](https://quarkus.io/guides/qute) (OpenRewrite has a Thymeleaf→Qute recipe) | Medium |
| HTTP client | `RestTemplate` | `quarkus-rest-client-jackson` typed client for `api.marsvista.dev` | Low–medium |
| Config | `api.key` / `MARSVISTA_API_KEY` | `api.key` in `application.properties` or `%dev` profile; map with `@ConfigProperty` | Low |
| Actuator / metrics | Spring Actuator + Prometheus | `quarkus-smallrye-health` + `quarkus-micrometer-registry-prometheus` | Low |
| H2 | Runtime dependency, unused | Drop unless you add persistence | None |
| Java version | 11 | Quarkus 3.x typically wants **17+**; plan a JDK bump | Required |
| Native image | N/A | Feasible after migration (Jackson models + REST client are usually fine) | Optional |

**Recommended path**

1. Bump to Java 17 and a current Spring Boot 3.x *or* go straight to Quarkus 3.x.
2. Use Quarkus Spring compatibility (`quarkus-spring-web`, `quarkus-spring-di`) only as a short bridge—or migrate directly to CDI + JAX-RS + Qute for a smaller long-term footprint.
3. Replace `RestTemplate` with a MicroProfile Rest Client interface for the Mars Vista rovers API.
4. Port `rovers.html` / `rover.html` / `photo.html` from Thymeleaf to Qute (syntax differs; layout/static assets can stay).
5. Keep K8s/OpenShift deploy shape; swap the container base to a Quarkus JVM or native image.

Automation helpers: [Quarkus Spring migration guide](https://quarkus.io/spring/migrate/), OpenRewrite `SpringBootToQuarkus` / `SpringBootThymeleafToQuarkus`.

Overall: **highly migratable** for a small team in a focused effort; the main work is templates and the controller/client layer, not data or infrastructure redesign.

## License

See [LICENSE](LICENSE).
