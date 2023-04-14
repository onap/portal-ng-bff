FROM openjdk:17 as builder
COPY . ./portalbff
WORKDIR /portalbff

# assemble does not run tests (as opposed to build)
RUN ./gradlew assemble

# Run locally (docker build --target=prod -t <tag> .)
FROM openjdk:17 as prod
ARG JAR_FILE=/portalbff/app/build/libs/app.jar
COPY --from=builder ${JAR_FILE} app.jar
EXPOSE 9080
ENTRYPOINT [ "java","-jar","app.jar" ]

# Run in pipeline (docker build --target=pipeline -t <tag> .)
FROM openjdk:17 as pipeline
WORKDIR /app

ARG JAR_FILE=app/build/libs/app.jar
COPY ${JAR_FILE} app.jar

ENTRYPOINT [ "java","-jar","app.jar" ]
EXPOSE 9080