# Construction
FROM eclipse-temurin:21-jdk AS construction
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -q -B dependency:go-offline
COPY src src
RUN ./mvnw -q -B -DskipTests package

# Exécution
FROM eclipse-temurin:21-jre
RUN useradd --system --create-home annuaire
USER annuaire
WORKDIR /app
COPY --from=construction /src/target/annuaire-semantique-*.jar app.jar
EXPOSE 8096
ENTRYPOINT ["java", "-jar", "app.jar"]
