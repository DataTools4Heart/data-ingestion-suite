docker compose -f data-ingestion-suite/docker/ignifyr-server/proxy/docker-compose-ignifyr.yml -p dt4h-ignifyr-proxy down
docker compose -f data-ingestion-suite/docker/ignifyr-server/proxy/docker-compose-ignifyr.yml --project-directory ./ -p dt4h-ignifyr-proxy up -d
