FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY build/libs/PhyloLib-1.0.0.jar /app/phylolib.jar
COPY docker/phylolib /usr/local/bin/phylolib

RUN chmod +x /usr/local/bin/phylolib

CMD ["phylolib", "help"]
