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
