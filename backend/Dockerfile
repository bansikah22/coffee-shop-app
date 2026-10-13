FROM eclipse-temurin:25-jre

RUN groupadd --system spring && useradd --system --gid spring --create-home spring

WORKDIR /app
COPY target/coffee-shop-api-*.jar app.jar

USER spring:spring
EXPOSE 18080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]