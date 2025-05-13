FROM maven:3.9.6-eclipse-temurin-17 as build
WORKDIR /app
COPY . .
RUN mvn clean install

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/wedding-bot-1.0.0.jar app.jar
CMD ["java", "-jar", "app.jar"]