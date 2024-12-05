# Linux
docker run -d \
    -v ./data-ingestion-suite:/usr/local/tofhir/conf \
    -v ./tofhir-engine-logs:/usr/local/tofhir/logs \
    -v C:/development/data/mimic-iv-3.1:/usr/local/tofhir/data/mimic-iv-3.1 \
    --network=onfhir-network \
    --env APP_CONF_FILE=/usr/local/tofhir/conf/docker/engine-only/tofhir-engine.conf \
    --env LOGBACK_CONF_FILE=/usr/local/tofhir/conf/docker/engine-only/logback.conf \
    --env CONTEXT_PATH=conf \
    --env DATA_FOLDER_PATH=data/mimic-iv-3.1 \
    --env FHIR_REPO_URL=http://onfhir:8080/fhir \
    --name tofhir-engine srdc/tofhir-engine:latest \
    run --job /usr/local/tofhir/conf/mapping-jobs/mimic/mimic-hosp-csv-to-fhir-server.json

#Windows
docker run -d `
    -v .\data-ingestion-suite:/usr/local/tofhir/conf `
    -v .\tofhir-engine-logs:/usr/local/tofhir/logs `
    -v C:\development\data\mimic-iv-3.1:/usr/local/tofhir/data/mimic-iv-3.1 `
    --network=onfhir-network `
    --env APP_CONF_FILE=/usr/local/tofhir/conf/docker/engine-only/tofhir-engine.conf `
    --env LOGBACK_CONF_FILE=/usr/local/tofhir/conf/docker/engine-only/logback.conf `
    --env CONTEXT_PATH=conf `
    --env DATA_FOLDER_PATH=/usr/local/tofhir/data/mimic-iv-3.1 `
    --env FHIR_REPO_URL=http://onfhir:8080/fhir `
    --name tofhir-engine srdc/tofhir-engine:latest `
    run --job /usr/local/tofhir/conf/mapping-jobs/mimic/mimic-hosp-csv-to-fhir-server.json