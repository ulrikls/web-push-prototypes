# Build: docker build -t web-push-prototypes .
# Test:  docker run -ti --rm web-push-prototypes bash
# Run:   docker run -d --name web-push-prototypes -p 7070:7070 web-push-prototypes
# Stop:  docker stop web-push-prototypes

FROM gradle:8.3-jdk17-jammy AS builder

LABEL maintainer="ulrikls@gmail.com"

WORKDIR /home/gradle/web-push-prototypes

COPY app/src app/src
COPY app/build.gradle.kts app/build.gradle.kts
COPY build-logic/src build-logic/src
COPY build-logic/build.gradle.kts build-logic/build.gradle.kts
COPY build-logic/settings.gradle.kts build-logic/settings.gradle.kts
COPY settings.gradle.kts settings.gradle.kts
COPY gradle.properties gradle.properties

RUN gradle jar


FROM eclipse-temurin:17-jre-jammy

LABEL maintainer="ulrikls@gmail.com"

WORKDIR /root

COPY --from=builder /home/gradle/web-push-prototypes/app/build/libs/app.jar app.jar

EXPOSE 7070

CMD exec java -jar app.jar 1024 1000
