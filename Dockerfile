# Build: o Maven baixa as dependências numa camada própria, que fica em cache
# enquanto o pom.xml não muda. Os testes rodam no job test do CI, não aqui.
FROM docker.io/library/maven:3.9-eclipse-temurin-25 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src src
RUN mvn -B -q package -DskipTests

FROM docker.io/library/eclipse-temurin:25-jre
RUN useradd --system --create-home tino
USER tino
WORKDIR /home/tino
COPY --from=build /build/target/tino.jar tino.jar
# O banco é um Postgres à parte. DATABASE_URL, DATABASE_USER, DATABASE_PASSWORD e
# DISCORD_TOKEN vêm do ambiente (o .env da VM), nunca da imagem.
# `docker run ... migrar` aplica as migrations e sai.
ENTRYPOINT ["java", "-jar", "tino.jar"]
