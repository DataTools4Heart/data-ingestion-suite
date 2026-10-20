#!/bin/bash
echo "Pulling ignifyr-server, elasticsearch, kibana, and fluentd images from public Docker Hub..."
docker compose -f data-ingestion-suite/docker/ignifyr-server/docker-compose-ignifyr.yml pull mongo repofyr ignifyr-server elasticsearch kibana fluentd
echo "Pulling ignifyr-web image from private SRDC repo..."
docker login docker.srdc.com.tr --username tofhir --password-stdin < srdc-docker-registry-password.txt
docker pull docker.srdc.com.tr/srdc/ignifyr-web:dt4h
docker tag docker.srdc.com.tr/srdc/ignifyr-web:dt4h srdc/ignifyr-web:dt4h
