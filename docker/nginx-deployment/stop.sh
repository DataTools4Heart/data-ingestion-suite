#!/bin/bash

docker compose -f data-ingestion-suite/docker/nginx-deployment/docker-compose-tofhir.yml -p dt4h-tofhir down
