FROM clojure:openjdk-11-tools-deps-slim-buster as builder

WORKDIR /usr/src/app

# install main deps
COPY deps.edn /usr/src/app/deps.edn
RUN clj -e :ok

# build
COPY src/ /usr/src/app/src
RUN clj -A:uberdeps

# use clean image
FROM openjdk:11-slim-buster

ENV PORT 9090
EXPOSE 9090

COPY --from=builder /usr/src/app/target/app.jar /usr/src/app/app.jar

CMD ["java","-XX:+UseContainerSupport","-XX:MaxRAMPercentage=85","-XX:+UnlockExperimentalVMOptions","-XX:+UseZGC","-cp","/usr/src/app/app.jar","clojure.main","-m","rave.avatar.core"]
