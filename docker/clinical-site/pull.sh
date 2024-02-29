#!/bin/bash

docker login nexus.srdc.com.tr:18445 --username tofhir --password-stdin < srdc-docker-registry-password.txt
docker pull nexus.srdc.com.tr:18445/srdc/tofhir-web:dt4h
docker tag nexus.srdc.com.tr:18445/srdc/tofhir-web:dt4h srdc/tofhir-web:dt4h
