# bff
Backend for Frontend (BFF) component of the portal-ng.

## Build
You can build and test the application with:  
``` sh
# Windows
gradlew clean build
# Unix
./gradlew clean build
```

## Test
``` sh
# run all tests
./gradlew test
# run all tests in a test class
./gradlew test --tests GetTileIntegrationTest
# run individual test in a test class
./gradlew test --tests GetTileIntegrationTest.thatTileCanBeRetrieved
# run individual test in a test-class with debug info
./gradlew test --tests GetTileIntegrationTest.thatTileCanBeRetrieved --info
```

## Generate JAR
To generate one JAR file including also the open-api part the following command can be used
```sh
# generate JAR to /library/build/libs
./gradlew shadowJar
```

## Publish JAR
To publish the generated JAR file run
```sh
# publish JAR to target repository
./gradlew publish
```

## Run locally
Currently there are three spring profiles that can be used to run the application (`application.yml`, `application-local.yml` and `application-development.yml`).

To launch the application with a specific profile run
``` sh
SPRING_PROFILES_ACTIVE=local ./gradlew app:bootRun
# or
export SPRING_PROFILES_ACTIVE=local
./gradlew app:bootRun
```

## Actuator
Only the `health`, `info` and `prometheus` actuator endpoints are exposed. `/actuator/health/**` (including the `liveness` and `readiness` probes), `/actuator/info` and `/actuator/prometheus` can be called without a token; every other actuator path, including the `/actuator` discovery page, requires authentication.

The paths that skip the Keycloak RBAC check can be overridden with the `RBAC_EXCLUDED_ENDPOINTS` environment variable (comma-separated). Its default is `/api-docs.html, /api.yaml, /webjars/**, /actuator/health/**, /actuator/info, /actuator/prometheus`; an override replaces the whole list.

## Development
You can run the service locally for evaluation or development purposes using the provided `docker-compose.yml` file in the development folder. This will launch a Keycloak and a Postgres db in the background.

To start the service execute the `run.sh` in the development folder:
```sh
development/run.sh
```

Example request against the preferences service can be run in your preferred IDE with the `request.http` file from the development folder.

You can access the Keycloak UI via browser.
URL: http://localhost:8080
**username:** admin  
**password:** password

To stop the preferences service, Keycloak and the databases run:
```sh
development/stop.sh
```
