#!/bin/bash
echo "Pulling tofhir-server, elasticsearch, kibana, and fluentd images from public Docker Hub..."
docker compose -f data-ingestion-suite/docker/server/docker-compose-tofhir.yml pull mongo onfhir tofhir-server elasticsearch kibana fluentd
echo "Pulling tofhir-web image from private SRDC repo..."
docker login docker.srdc.com.tr --username tofhir --password-stdin < srdc-docker-registry-password.txt
docker pull docker.srdc.com.tr/srdc/tofhir-web:dt4h
docker tag docker.srdc.com.tr/srdc/tofhir-web:dt4h srdc/tofhir-web:dt4h
