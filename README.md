# DataTools4Heart Data Ingestion Suite
This repository includes the mapping definitions to convert hospital specific patient data into the DT4H CDM, which is specified in https://github.com/DataTools4Heart/common-data-model.

The data ingestion suite contains a number of concepts as pieces of the overall mapping process. These concepts are **schemas**, **terminology services**, **concept maps**, **mappings** and **mapping jobs**. They are instantiated for each clinical site based on the characteristics of the data at each site.

## Schemas
Schemas are used to have well-defined representations for the source healthcare resources such as conditions, medications, lab measurements, etc. These representations are processed by the mappings to extract the actual data from the data source.

## Terminology services
Terminology services are used to convert codes from one terminology system to another one e.g. ICD9 to ICD10. 

## Concept maps
Concept maps contain auxilary information that is needed during the mapping process. Having a tabular format, they can be seen as a key-value mapping such that the values in the first column are keys and the values in the rest of the columns are values. Concept maps are usually used to convert proprietary value sets / codes to their CDM correspondings and unit conversions i.e. converting units used in the original data sources to the units used in the CDM via some conversion functions. Concept maps are referred by mappings via mapping context configurations.

## Mappings
Mappings are the actual scripts transforming the original EHR data entities to the CDM, specifically corresponding profiles composing the CDM. Mappings extract information from the original data sources and put the extracted data into the specified fields of the CDM profile. Mappings can utilise terminology services and concept maps to apply code and unit conversions and extract any information needed from concept maps.

## Mapping jobs
Mapping jobs are used to configure input and output specifications for mappings: where the data source is located (database or file system) and where the mapped resources will be written to (file system or FHIR server). In addition, each mapping job has one or more mappings to be executed.

## toFhir
The toFhir folder contains DT4H-specific utility functions to be injected into the toFhir engine. These functions are used in FHIR Path expressions for mappings extract and map the source data to the CDM as needed.

---

# Deployment Guideline (with Nginx)

## Requirements

- Git
- Docker

---

## Downloading DT4H Mapping Configurations

DT4H mapping configurations are maintained in the project’s GitHub repository.
Create and navigate into a working directory to run the tools: `<workspaceDir>`

```bash
git clone https://github.com/DataTools4Heart/data-ingestion-suite.git
git clone https://github.com/DataTools4Heart/common-data-model.git
```

---

## onFHIR Deployment

Run inside the working directory:

```bash
sh ./common-data-model/docker/run.sh
```

If you don't have execution access:

```bash
chmod +x ./common-data-model/docker/run.sh
```

---

## toFHIR Deployment

1. Download the password file for SRDC’s private Docker repository:
   [Download password file](https://srdcltd.sharepoint.com/:t:/s/DataTools4Heart/ERRInhWGLy5Cvfwn9JFaNOEBUmc1jjjP-bjCz3lAvYu9DQ?e=AsvrVi). Please contact us for access.

2. Copy the file into `<workspaceDir>`.

3. Run the following scripts:

```bash
sh ./data-ingestion-suite/docker/deployment/pull.sh
sh ./data-ingestion-suite/docker/deployment/run.sh
```

If needed, give execution access and run the scripts:

```bash
chmod +x ./data-ingestion-suite/docker/deployment/pull.sh
chmod +x ./data-ingestion-suite/docker/deployment/run.sh
```

---

## Running Behind Nginx Configuration

This deployment has been tested with Nginx and its use is recommended. 
To use our predefined Nginx Docker container:

```bash
sh ./data-ingestion-suite/docker/proxy/run.sh
```

If your host machine is already running Nginx, add the following proxy configuration and restart Nginx:

```nginx
location /dt4h/tofhir/api {
    proxy_pass http://127.0.0.1:6085/tofhir;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
}

location /dt4h/tofhir {
    proxy_pass http://127.0.0.1:6082/;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
}

location /dt4h/tofhir/kibana/ {
    proxy_pass http://127.0.0.1:6601/;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
}

location /dt4h/onfhir {
   proxy_pass http://127.0.0.1:6080/fhir;
   proxy_set_header Host $host;
   proxy_set_header X-Real-IP $remote_addr;
}
```

---

## MIMIC Dataset Deployment (Optional)

>Only follow this section if you're working with MIMIC-IV v3.1.
>If you are using a different MIMIC version, the mappings may not work correctly.

### MIMIC Dataset Preparation
* Ensure your dataset includes a folder `hosp/` containing required CSVs like `admissions.csv`, `patients.csv`, etc.

Example folder path:
```
C:/development/data/mimic-iv-3.1/
└── hosp/
    ├── admissions.csv
    ├── emar.csv
    ├── patients.csv
    └── ...
```

### Update Dataset Path
Uncomment the line 23 of:
```bash
./data-ingestion-suite/docker/deployment/docker-compose-tofhir.yml
```
Replace only the part before the colon (`:`) with your dataset path.

### Execute Mappings for MIMIC Dataset

1. Navigate to `http://<hostname>/dt4h/tofhir`
2. Click `hosp` → **Open**
3. Click **Executions**
4. Click the green arrow next to the **mimic-hosp-csv-to-fhir-server** entry
5. Click the double-right-arrow icon to select all mappings
6. Click **Run**
7. Use the **Refresh** icon to monitor execution status and check mapping results inside the “x-deploy” job.

>View logs and errors at: `http://<hostname>/dt4h/tofhir/kibana`
>
>In Kibana: click the top-left menu and choose Discover under Analytics

---

## General Execution of Mappings

1. Navigate to `http://<hostname>/dt4h/tofhir`
2. Click your project and click **Open**
3. Click **Executions**
4. Click the green arrow next to the “x-deploy” entry
5. Click the double-right-arrow icon to select all mappings
6. Click **Run**
7. Use the **Refresh** icon to monitor execution status and check mapping results inside the “x-deploy” job.

>View logs and errors at: `http://<hostname>/dt4h/tofhir/kibana`
>
>In Kibana: click the top-left menu and choose Discover under Analytics

---

## Automated Docker Container Update (Optional)

> If you’re installing for the first time, you can skip this section. 
> This section is only for updating the existing installation.

### 1. Stop all running containers

```bash
sh ./data-ingestion-suite/docker/deployment/stop.sh
sh ./common-data-model/docker/stop.sh
sh ./data-ingestion-suite/docker/proxy/stop.sh  # Optional
```

### 2. Pull the latest updates

```bash
cd common-data-model
git pull
cd ..
sh ./common-data-model/docker/pull.sh

cd data-ingestion-suite
git pull
cd ..
sh ./data-ingestion-suite/docker/deployment/pull.sh
```

### 3. Restart all containers

```bash
sh ./common-data-model/docker/run.sh
sh ./data-ingestion-suite/docker/deployment/run.sh
sh ./data-ingestion-suite/docker/proxy/run.sh  # Optional
```

---

For more details, visit [toFHIR.io](https://toFHIR.io) or [onfhir.io](https://onfhir.io).
