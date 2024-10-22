FROM eclipse-temurin:21 as builder
COPY . ./bff
WORKDIR /bff

RUN ./gradlew assemble

FROM eclipse-temurin:21-jre-alpine
USER nobody
ARG JAR_FILE=/bff/app/build/libs/app.jar
COPY --from=builder ${JAR_FILE} app.jar
EXPOSE 9080
ENTRYPOINT [ "java","-jar","app.jar" ]