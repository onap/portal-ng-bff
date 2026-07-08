FROM nexus3.onap.org:10001/eclipse-temurin:17 as builder
COPY . ./bff
WORKDIR /bff

RUN ./gradlew assemble

FROM nexus3.onap.org:10001/eclipse-temurin:17-jre-alpine
USER nobody
ARG JAR_FILE=/bff/app/build/libs/app.jar
COPY --from=builder ${JAR_FILE} app.jar
EXPOSE 9080
ENTRYPOINT [ "java","-jar","app.jar" ]