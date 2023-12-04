#!/bin/bash

docker login nexus.srdc.com.tr:18445 --username tofhir --password-stdin < srdc-docker-registry-password.txt
docker-compose -f data-ingestion-suite/docker/docker-compose-tofhir.yml --project-directory ./ -p dt4h-tofhir up -d
