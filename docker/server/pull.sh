#!/bin/bash
echo "Pulling tofhir-server, elasticsearch, kibana, and fluentd images from public Docker Hub..."
docker compose -f data-ingestion-suite/docker/server/docker-compose-tofhir.yml pull mongo onfhir tofhir-server elasticsearch kibana fluentd
echo "Pulling tofhir-web image from private SRDC repo..."
docker login nexus.srdc.com.tr:18445 --username tofhir --password-stdin < srdc-docker-registry-password.txt
docker pull nexus.srdc.com.tr:18445/srdc/tofhir-web:dt4h
docker tag nexus.srdc.com.tr:18445/srdc/tofhir-web:dt4h srdc/tofhir-web:dt4h
