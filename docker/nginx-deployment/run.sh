#!/bin/bash

docker compose -f data-ingestion-suite/docker/nginx-deployment/docker-compose-tofhir.yml --project-directory ./ -p dt4h-tofhir up -d
