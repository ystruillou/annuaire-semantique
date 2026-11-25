# Interface d'administration (Angular)
FROM node:24 AS interface
WORKDIR /admin
COPY admin/package.json admin/package-lock.json ./
RUN npm ci
COPY admin/ ./
# La sortie est réglée sur ../src/main/resources/static/admin, soit /src/main/resources/static/admin ici.
RUN npx ng build

# Application
FROM eclipse-temurin:21-jdk AS construction
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -q -B dependency:go-offline
COPY src src
COPY --from=interface /src/main/resources/static/admin src/main/resources/static/admin
RUN ./mvnw -q -B -DskipTests package

# Exécution
FROM eclipse-temurin:21-jre
RUN useradd --system --create-home annuaire
USER annuaire
WORKDIR /app
COPY --from=construction /src/target/annuaire-semantique-*.jar app.jar
EXPOSE 8096
ENTRYPOINT ["java", "-jar", "app.jar"]
