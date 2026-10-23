<<<<<<< HEAD
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
=======
# ---- Etapa 1: compilar con Gradle (JDK 21, el que pide gradle-daemon-jvm.properties) ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY . .
# chmod por si gradlew quedó sin permiso de ejecución al subirlo desde Windows
RUN chmod +x gradlew && ./gradlew installDist --no-daemon

# ---- Etapa 2: imagen final, solo con el JRE ----
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/build/install/ecovolt-backend-kotlin/ ./

# Render inyecta la variable PORT; Main.kt ya la lee
EXPOSE 10000
CMD ["bin/ecovolt-backend-kotlin"]
>>>>>>> 5fd35b6 (Agregar Dockerfile para desplegar en Render)
