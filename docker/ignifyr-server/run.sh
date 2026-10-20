#!/bin/bash

rm -f data-ingestion-suite/projects.json
docker compose -f data-ingestion-suite/docker/ignifyr-server/docker-compose-ignifyr.yml --project-directory ./ -p fyr-stack up -d --wait
