FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY . .
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew && ./gradlew installDist --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/build/install/ /app/install/
RUN mkdir -p /data
ENV DB_PATH=/data/ecovolt.db
CMD ["sh", "-c", "exec $(ls /app/install/*/bin/* | grep -v '\\.bat$' | head -1)"]
