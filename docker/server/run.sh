#!/bin/bash

rm -f data-ingestion-suite/projects.json
docker compose -f data-ingestion-suite/docker/server/docker-compose-tofhir.yml --project-directory ./ -p dt4h-tofhir up -d --wait
