FROM eclipse-temurin:17 as builder
COPY . ./portalbff
WORKDIR /portalbff

RUN ./gradlew build

FROM eclipse-temurin:17-jre-alpine
ARG JAR_FILE=/portalbff/app/build/libs/app.jar
COPY --from=builder ${JAR_FILE} app.jar
EXPOSE 9080
ENTRYPOINT [ "java","-jar","app.jar" ]