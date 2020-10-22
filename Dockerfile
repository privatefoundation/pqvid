FROM --platform=$BUILDPLATFORM openjdk:8-jre-alpine AS builder

RUN apk update && apk add gradle git && rm -rf /var/lib/apk/* /var/cache/apk/*

WORKDIR /pqvid
COPY . .
RUN ./gradlew shadowJar

FROM openjdk:8-jre-alpine

RUN apk update && apk add bash && rm -rf /var/lib/apk/* /var/cache/apk/*

VOLUME /etc/pqvid
VOLUME /var/pqvid
EXPOSE 8090

ENV JAVA_OPTS=""
ENV CONF_FILE_PATH="/etc/pqvid/pqvid.yaml"
ENV SIGN_KEY_PATH="/var/pqvid/sign.key"
ENV SQLITE_DATABASE_PATH="/var/pqvid/pqvid.db"

CMD [ "/start.sh" ]

ADD src/docker/start.sh /start.sh
ADD src/script/pqvid /app/pqvid
COPY --from=builder /pqvid/build/libs/pqvid.jar /app/pqvid.jar
