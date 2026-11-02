# Build the Appointment JAR from the BE root before building this runtime image.
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY services/appointment-service/target/appointment-service-1.0.0-SNAPSHOT.jar /app/appointment.jar
USER 10001:10001
EXPOSE 8083
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70", "-jar", "/app/appointment.jar"]
