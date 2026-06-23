# syntax=docker/dockerfile:1

# =========================================================================
# Stage 1 — Build the Spring Boot fat jar with Maven (Java 17 / Temurin)
# =========================================================================
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# 1. Install the local-only PDF library (not on Maven Central) into the
#    build's local Maven repo, using its real POM so transitive deps
#    (OpenHTMLToPDF 1.1.37, PDFBox 3.0.7, ICU4J) resolve correctly.
COPY PDF-Gen/thai-pdf-glyphshaping-0.1.0.jar /build/lib/
COPY docker/lib/thai-pdf-glyphshaping-0.1.0.pom /build/lib/
RUN mvn -q install:install-file \
        -Dfile=/build/lib/thai-pdf-glyphshaping-0.1.0.jar \
        -DpomFile=/build/lib/thai-pdf-glyphshaping-0.1.0.pom

# 2. Resolve application dependencies first (cached layer when pom is unchanged)
COPY pom.xml /build/pom.xml
RUN mvn -q -B dependency:go-offline

# 3. Compile + package the application
COPY src /build/src
RUN mvn -q -B clean package -DskipTests

# =========================================================================
# Stage 2 — Lightweight runtime (JRE 17 + Thai fonts)
# =========================================================================
FROM eclipse-temurin:17-jre
WORKDIR /app

# Thai-capable fonts + tools for the PDF renderer and container healthcheck.
# fonts-thai-tlwg gives broad Thai coverage; the bundled Noto Sans Thai is the
# primary shaping font used by the report templates.
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
         fonts-thai-tlwg fontconfig curl \
    && rm -rf /var/lib/apt/lists/* \
    && fc-cache -f

# Primary shaping font (referenced by FONT_NOTO env, see docker-compose.yml)
COPY docker/fonts/NotoSansThai-Regular.ttf /opt/fonts/NotoSansThai-Regular.ttf

# Application jar
COPY --from=build /build/target/*.jar /app/app.jar

ENV JAVA_OPTS="-Djava.awt.headless=true" \
    FONT_NOTO=/opt/fonts/NotoSansThai-Regular.ttf \
    FONT_TAHOMA="" \
    FONT_SARABUN=""

EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=4s --start-period=40s --retries=5 \
    CMD curl -fsS http://localhost:8080/ >/dev/null || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
