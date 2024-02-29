#!/bin/bash

docker compose -f data-ingestion-suite/docker/clinical-site/docker-compose-tofhir.yml --project-directory ./ -p dt4h-tofhir up -d
