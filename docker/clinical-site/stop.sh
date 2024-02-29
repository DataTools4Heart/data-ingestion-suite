#!/bin/bash

docker compose -f data-ingestion-suite/docker/clinical-site/docker-compose-tofhir.yml -p dt4h-tofhir down
