FROM eclipse-temurin:25-jre
WORKDIR /app
COPY target/payment-service-0.0.1-SNAPSHOT.jar app.jar
USER 10001:10001
EXPOSE 18081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]