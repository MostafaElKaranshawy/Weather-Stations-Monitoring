#!/bin/bash

set -e

CLUSTER_NAME="weather-monitoring"

if [ -d /usr/lib/jvm/java-21-openjdk-amd64 ]; then
    export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
    export PATH="$JAVA_HOME/bin:$PATH"
fi

mvn clean package -DskipTests

docker build -t weather-station:latest -f weather-station/Dockerfile .
docker build -t central-station:latest -f central-station/Dockerfile .
docker build -t kafka-stream:latest -f kafka-stream/Dockerfile .
docker build -t elasticsearch-client:latest -f elasticsearch-client/Dockerfile .
docker build -t open-meteo-adapter:latest -f open-meteo-adapter/Dockerfile .


kind load docker-image weather-station:latest --name "${CLUSTER_NAME}"
kind load docker-image central-station:latest --name "${CLUSTER_NAME}"
kind load docker-image kafka-stream:latest --name "${CLUSTER_NAME}"
kind load docker-image elasticsearch-client:latest --name "${CLUSTER_NAME}"
kind load docker-image open-meteo-adapter:latest --name "${CLUSTER_NAME}"

docker pull zookeeper:3.9.2
docker pull apache/kafka:3.9.0
docker pull docker.elastic.co/elasticsearch/elasticsearch:8.13.4
docker pull docker.elastic.co/kibana/kibana:8.13.4

kind load docker-image zookeeper:3.9.2 --name "${CLUSTER_NAME}"
kind load docker-image apache/kafka:3.9.0 --name "${CLUSTER_NAME}"
kind load docker-image docker.elastic.co/elasticsearch/elasticsearch:8.13.4 --name "${CLUSTER_NAME}"
kind load docker-image docker.elastic.co/kibana/kibana:8.13.4 --name "${CLUSTER_NAME}"


kubectl apply -f k8s/

kubectl rollout restart deployment
kubectl rollout restart statefulset 2>/dev/null || true