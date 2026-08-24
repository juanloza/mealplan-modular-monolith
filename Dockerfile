# Two stages: the JDK and Maven exist only to build. The final image carries a JRE and nothing
# else, with no compiler, no wrapper and no sources.
FROM eclipse-temurin:25-jdk AS build
WORKDIR /build

# config/ is not optional: Checkstyle is bound to the validate phase, which package runs, and
# without its ruleset the image build fails with "Unable to find configuration file". Copying it is
# better than passing -Dcheckstyle.skip: an image that silently switches off a check of the project
# is worse than one extra line.
COPY .mvn/ .mvn/
COPY config/ config/
COPY mvnw pom.xml ./
COPY src/ src/

# chmod is not cosmetic either: git does not preserve the executable bit when the repository is
# created from Windows, and without it ./mvnw fails with permission denied. The other half of the
# fix is .gitattributes, which pins it in the repository itself.
#
# maven.test.skip rather than skipTests: the second one still compiles the tests, which would pull
# ArchUnit and Testcontainers into the image for nothing. The image runs no tests; CI does that.
#
# The layered extraction is the Spring Boot 4 way, and --launcher is required: without it the
# application layer holds the whole jar and it would have to be started with java -jar.
RUN chmod +x mvnw \
 && ./mvnw -B -Dmaven.test.skip=true package \
 && java -Djarmode=tools -jar target/mealplan-api-1.0.0.jar extract --layers --launcher --destination extracted

FROM eclipse-temurin:25-jre AS runtime

# The Temurin base is Debian, hence groupadd and useradd.
RUN groupadd --system app && useradd --system --gid app --home /app app
WORKDIR /app

# Dependencies change far less often than the application, so a rebuild after touching one class
# rewrites only the last layer.
COPY --from=build --chown=app:app /build/extracted/dependencies/ ./
COPY --from=build --chown=app:app /build/extracted/spring-boot-loader/ ./
COPY --from=build --chown=app:app /build/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=app:app /build/extracted/application/ ./

USER app
EXPOSE 8080

# Exec form, so the Java process is PID 1 and receives SIGTERM directly. With the shell form PID 1
# would be an interpreter that does not forward it, and the graceful shutdown would never run.
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
