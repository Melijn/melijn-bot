# Full jdk required for font rendering on ship ect
FROM toxicmushroom/openjdk:16-glibc
WORKDIR /opt/melijn
COPY ./bot/build/libs/melijn.jar ./melijn.jar
ENTRYPOINT java \
    -Xmx${RAM_LIMIT} \
    -Dkotlin.script.classpath="/opt/melijn/melijn.jar" \
    --add-opens java.base/java.lang=ALL-UNNAMED \
    -jar \
    ./melijn.jar