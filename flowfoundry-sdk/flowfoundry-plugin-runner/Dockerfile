FROM eclipse-temurin:17-jre
WORKDIR /app
COPY target/flowfoundry-plugin-runner-*-exec.jar /app/flowfoundry-plugin-runner.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/flowfoundry-plugin-runner.jar"]
