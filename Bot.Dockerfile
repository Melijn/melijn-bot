# Full jdk required for font rendering on ship ect
FROM bellsoft/liberica-runtime-container:jdk-17-glibc
WORKDIR /opt/melijn
COPY ./bot/build/libs/melijn.jar ./melijn.jar
ENTRYPOINT java \
    -Xmx${RAM_LIMIT} \
    -Dkotlin.script.classpath="/opt/melijn/melijn.jar" \
    --add-opens java.base/java.lang=ALL-UNNAMED \
    -jar \
    ./melijn.jar