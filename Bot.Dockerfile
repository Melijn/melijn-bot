# Full jdk required for font rendering on ship ect
FROM bellsoft/liberica-runtime-container:jdk-21-glibc
RUN apk add --no-cache libxrender libxi libxtst alsa-lib libx11 fontconfig libxext freetype zlib ttf-dejavu
RUN ln -s /usr/lib/libfontconfig.so.1 /usr/lib/libfontconfig.so && \
    ln -s /lib/libuuid.so.1 /usr/lib/libuuid.so.1 && \
    ln -s /lib/libc.musl-x86_64.so.1 /usr/lib/libc.musl-x86_64.so.1
ENV LD_LIBRARY_PATH /usr/lib
WORKDIR /opt/melijn
COPY ./bot/build/libs/melijn.jar ./melijn.jar
ENTRYPOINT java \
    -Xmx${RAM_LIMIT} \
    -Dkotlin.script.classpath="/opt/melijn/melijn.jar" \
    --add-opens java.base/java.lang=ALL-UNNAMED \
    -jar \
    ./melijn.jar