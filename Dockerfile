# Compile the Java source with a JDK in a separate build stage.
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
COPY src/main/java ./src/main/java
RUN mkdir -p out && javac --add-modules jdk.httpserver -d out $(find src/main/java -name "*.java")

# Keep only the runtime, compiled classes, and page stylesheet in the final image.
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/out ./out
COPY src/main/resources ./src/main/resources
RUN mkdir -p /app/data
ENV PORT=10000
EXPOSE 10000
CMD ["java", "--add-modules", "jdk.httpserver", "-cp", "out", "preppath.Main"]
