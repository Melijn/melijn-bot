FROM toxicmushroom/openjdk:16-glibc as builder
WORKDIR /etc/melijn
COPY ./ ./
USER root
RUN chmod +x ./gradlew
RUN ./gradlew :bot:shadowJar

# Full jdk required for font rendering on ship ect
FROM toxicmushroom/openjdk:16-glibc
WORKDIR /opt/melijn
COPY --from=builder ./etc/melijn/bot/build/libs/ .
ENTRYPOINT java \
    -Xmx${RAM_LIMIT} \
    -Dkotlin.script.classpath="/opt/melijn/melijn.jar" \
    --add-opens java.base/java.lang=ALL-UNNAMED \
    -jar \
    ./melijn.jar