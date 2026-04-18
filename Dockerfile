FROM openjdk:21-jdk
WORKDIR /app
COPY target/myapp.jar /app/myapp.jar
EXPOSE 8080
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser
CMD ["java", "-jar", "myapp.jar"]