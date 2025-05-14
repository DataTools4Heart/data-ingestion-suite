CREATE TABLE "0000000004_DT4H".DEATH (
			person_id varchar(128) NOT NULL,
			death_date date NOT NULL,
			death_datetime TIMESTAMP NULL,
			death_type_concept_id integer NULL,
			cause_concept_id integer NULL,
			cause_source_value varchar(50) NULL,
			cause_source_concept_id integer NULL 
);


CREATE TABLE "0000000004_DT4H".CARE_SITE (
			care_site_id integer NOT NULL,
			care_site_name varchar(255) NULL,
			place_of_service_concept_id integer NULL,
			location_id integer NULL,
			care_site_source_value varchar(50) NULL,
			place_of_service_source_value varchar(50) NULL );


CREATE TABLE "0000000004_DT4H".CONDITION_OCCURRENCE (
			condition_occurrence_id integer NOT NULL,
			person_id varchar(128) NOT NULL,
			condition_concept_id integer NOT NULL,
			condition_start_date date NOT NULL,
			condition_start_datetime TIMESTAMP NULL,
			condition_end_date date NULL,
			condition_end_datetime TIMESTAMP NULL,
			condition_type_concept_id integer NOT NULL,
			stop_reason varchar(20) NULL,
			provider_id integer NULL,
			visit_occurrence_id varchar(128) NULL,
			visit_detail_id integer NULL,
			condition_source_value varchar(50) NULL,
			condition_source_concept_id integer NULL,
			condition_status_source_value varchar(50) NULL, 
			condition_status_concept_id integer NULL
			);



CREATE TABLE "0000000004_DT4H".DRUG_EXPOSURE (
			drug_exposure_id integer NOT NULL,
			person_id varchar(128) NOT NULL,
			drug_concept_id integer NOT NULL,
			drug_exposure_start_date date NOT NULL,
			drug_exposure_start_datetime TIMESTAMP NULL,
			drug_exposure_end_date date NOT NULL,
			drug_exposure_end_datetime TIMESTAMP NULL,
			verbatim_end_date date NULL,
			drug_type_concept_id integer NOT NULL,
			stop_reason varchar(20) NULL,
			refills integer NULL,
			quantity double NULL,
			days_supply integer NULL,
			sig CLOB NULL,
			route_concept_id integer NULL,
			lot_number varchar(50) NULL,
			provider_id integer NULL,
			visit_occurrence_id varchar(128) NULL,
			visit_detail_id integer NULL,
			drug_source_value varchar(50) NULL,
			drug_source_concept_id integer NULL,
			route_source_value varchar(50) NULL,
			dose_unit_source_value varchar(50) NULL );


CREATE TABLE "0000000004_DT4H".MEASUREMENT (
			measurement_id integer NOT NULL,
			person_id varchar(128) NOT NULL,
			measurement_concept_id integer NOT NULL,
			measurement_date date NOT NULL,
			measurement_datetime TIMESTAMP NULL,
			measurement_time varchar(10) NULL,
			measurement_type_concept_id integer NOT NULL,
			operator_concept_id integer NULL,
			value_as_number double NULL,
			value_as_concept_id integer NULL,
			unit_concept_id integer NULL,
			range_low double NULL,
			range_high double NULL,
			provider_id integer NULL,
			visit_occurrence_id varchar(128) NULL,
			visit_detail_id integer NULL,
			measurement_source_value varchar(50) NULL,
			measurement_source_concept_id integer NULL,
			unit_source_value varchar(50) NULL,
			value_source_value varchar(50) NULL);


CREATE TABLE "0000000004_DT4H".OBSERVATION (
			observation_id integer NOT NULL,
			person_id varchar(128) NOT NULL,
			observation_concept_id integer NOT NULL,
			observation_date date NOT NULL,
			observation_datetime TIMESTAMP NULL,
			observation_type_concept_id integer NOT NULL,
			value_as_number double NULL,
			value_as_string varchar(60) NULL,
			value_as_concept_id integer NULL,
			qualifier_concept_id integer NULL,
			unit_concept_id integer NULL,
			provider_id integer NULL,
			visit_occurrence_id varchar(128) NULL,
			visit_detail_id integer NULL,
			observation_source_value varchar(50) NULL,
			observation_source_concept_id integer NULL,
			unit_source_value varchar(50) NULL,
			qualifier_source_value varchar(50) NULL );


CREATE TABLE "0000000004_DT4H".PERSON (
			person_id varchar(128) NOT NULL,
			gender_concept_id integer NOT NULL,
			year_of_birth integer NOT NULL,
			month_of_birth integer NULL,
			day_of_birth integer NULL,
			birth_datetime TIMESTAMP NULL,
			race_concept_id integer NOT NULL,
			ethnicity_concept_id integer NOT NULL,
			location_id integer NULL,
			provider_id integer NULL,
			care_site_id integer NULL,
			person_source_value varchar(50) NULL,
			gender_source_value varchar(50) NULL,
			gender_source_concept_id integer NULL,
			race_source_value varchar(50) NULL,
			race_source_concept_id integer NULL,
			ethnicity_source_value varchar(50) NULL,
			ethnicity_source_concept_id integer NULL );


CREATE TABLE "0000000004_DT4H".PROCEDURE_OCCURRENCE (
			procedure_occurrence_id integer NOT NULL,
			person_id varchar(128) NOT NULL,
			procedure_concept_id integer NOT NULL,
			procedure_date date NOT NULL,
			procedure_datetime TIMESTAMP NULL,
			procedure_type_concept_id integer NOT NULL,			
			modifier_concept_id integer NULL,
			quantity integer NULL,
			provider_id integer NULL,
			visit_occurrence_id varchar(128) NULL,
			visit_detail_id integer NULL,
			procedure_source_value varchar(50) NULL,
			procedure_source_concept_id integer NULL,
			modifier_source_value varchar(50) NULL );			



CREATE TABLE "0000000004_DT4H".VISIT_OCCURRENCE (
			visit_occurrence_id varchar(128) NOT NULL,
			person_id varchar(128) NOT NULL,
			visit_concept_id integer NOT NULL,
			visit_start_date date NOT NULL,
			visit_start_datetime TIMESTAMP NULL,
			visit_end_date date NOT NULL,
			visit_end_datetime TIMESTAMP NULL,
			visit_type_concept_id Integer NOT NULL,
			provider_id integer NULL,
			care_site_id integer NULL,
			visit_source_value varchar(50) NULL,
			visit_source_concept_id integer NULL,
			admitting_source_concept_id integer NULL,
			admitting_source_value varchar(50) NULL,
			discharge_to_concept_id integer NULL,
			discharge_to_source_value varchar(50) NULL,
			preceding_visit_occurrence_id varchar(50) NULL );

