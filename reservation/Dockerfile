FROM maven:3.9.6-eclipse-temurin-21 AS builder
WORKDIR /build

# Akka SDK requires authenticated Maven repo — settings.xml must exist at build context root
# (reservation/settings.xml on the host, gitignored — contains Akka repo token)
COPY settings.xml /root/.m2/settings.xml

# Copy parent pom and all required modules
COPY pom.xml ./pom.xml
COPY spi ./spi
COPY notifierstub ./notifierstub
COPY googlecalendar ./googlecalendar
COPY reservation ./reservation

# Install parent POM so sub-modules can resolve it as their parent
RUN mvn -q install -N

# Install stub modules to local Maven repo
RUN cd spi         && mvn -q package && mvn -q install:install-file -Dfile=target/spi-0.5.jar          -DgroupId=com.rezhub.reservation -DartifactId=spi          -Dversion=0.5 -Dpackaging=jar
RUN cd notifierstub && mvn -q package && mvn -q install:install-file -Dfile=target/notifierstub-0.5.jar -DgroupId=com.rezhub.reservation -DartifactId=notifierstub  -Dversion=0.5 -Dpackaging=jar
RUN cd googlecalendar && mvn -q package && mvn -q install:install-file -Dfile=target/googlecalendar-0.5.jar -DgroupId=com.rezhub.reservation -DartifactId=googlecalendar -Dversion=0.5 -Dpackaging=jar

# Compile main app and copy all runtime dependencies
RUN cd reservation && mvn -q compile dependency:copy-dependencies -DoutputDirectory=target/dependency -Pgoogle

# ---- runtime image ----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

COPY --from=builder /build/reservation/target/classes ./classes
COPY --from=builder /build/reservation/target/dependency ./lib
COPY --from=builder /build/reservation/src/main/resources ./classes

RUN mkdir -p /run/secrets
EXPOSE 9000

CMD ["java", \
     "-Dconfig.resource=application.conf", \
     "-Dlogback.configurationFile=logback.xml", \
     "-Dakka.javasdk.dev-mode.enabled=true", \
     "-cp", "classes:lib/*", \
     "kalix.runtime.AkkaRuntimeMain"]
