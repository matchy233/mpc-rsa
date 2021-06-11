FROM openjdk:11 AS builder
LABEL stage=builder
ADD . /mpc-project
WORKDIR /mpc-project
RUN chmod +x ./gradlew
RUN ./gradlew :app:installDist
RUN chmod +x ./app/build/install/app/bin/app

FROM openjdk:11-jre-slim AS worker
ENV PORT_NUM 8080
EXPOSE ${PORT_NUM}
WORKDIR /root/
COPY --from=builder /mpc-project/app/build/install/app .
ENTRYPOINT ./bin/app --mode worker --port $PORT_NUM

FROM openjdk:11-slim AS manager
ENV PORT_NUM 8080
ENV PARALLEL 0
EXPOSE ${PORT_NUM}
WORKDIR /root/
COPY --from=builder /mpc-project/app/build/install/app .
ENTRYPOINT ./bin/app --mode manager --port $PORT_NUM -P $PARALLEL