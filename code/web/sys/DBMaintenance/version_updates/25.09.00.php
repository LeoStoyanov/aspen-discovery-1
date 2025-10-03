<?php

function getUpdates25_09_00(): array {
	$curTime = time();
	return [
		/*'name' => [
			 'title' => '',
			 'description' => '',
			 'continueOnError' => false,
			 'sql' => [
				 ''
			 ]
		 ], //name*/

		//mark - Grove

		//katherine - Grove

		//kirstien - Grove

		//kodi - Grove

		// Myranda - Grove

		//Yanjun Li - ByWater

		// Leo Stoyanov - BWS
		'hoopla_entitlements_table' => [
			'title' => 'Create Hoopla Entitlements Table',
			'description' => 'Create a normalized table to track library entitlements for Hoopla content, replacing the inefficient scopedLibraryIds string field',
			'continueOnError' => false,
			'sql' => [
				"CREATE TABLE IF NOT EXISTS hoopla_entitlements (
					hooplaId bigint(20) NOT NULL,
					settingId bigint(20) NOT NULL,
					active tinyint(1) DEFAULT 1,
					purchaseModel varchar(20) DEFAULT NULL COMMENT 'Instant, Flex, or other purchase model for this library',
					dateAdded timestamp DEFAULT CURRENT_TIMESTAMP,
					dateUpdated timestamp DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
					PRIMARY KEY (hooplaId, settingId),
					INDEX idx_hoopla_id (hooplaId),
					INDEX idx_setting_id (settingId),
					INDEX idx_setting_active (settingId, active),
					INDEX idx_active (active),
					INDEX idx_purchase_model (purchaseModel)
				) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci"
			]
		], //hoopla_entitlements_table

		'migrate_scoped_library_ids_to_entitlements' => [
			'title' => 'Migrate Existing Scoped Library IDs to Entitlements Table',
			'description' => 'Migrate existing scopedLibraryIds data from hoopla_export to the new hoopla_entitlements table',
			'continueOnError' => true,
			'sql' => [
				// Migration query to parse scopedLibraryIds and insert into new table
				"INSERT IGNORE INTO hoopla_entitlements (hooplaId, settingId, active, dateAdded)
				 SELECT
					 e.hooplaId,
					 CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(e.scopedLibraryIds, '~', n.n), '~', -1) AS UNSIGNED) as settingId,
					 1 as active,
					 NOW() as dateAdded
				 FROM hoopla_export e
				 CROSS JOIN (
					 SELECT 1 n UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5
					 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9 UNION ALL SELECT 10
				 ) n
				 WHERE e.scopedLibraryIds IS NOT NULL
				   AND e.scopedLibraryIds != ''
				   AND CHAR_LENGTH(e.scopedLibraryIds) - CHAR_LENGTH(REPLACE(e.scopedLibraryIds, '~', '')) >= n.n - 1
				   AND SUBSTRING_INDEX(SUBSTRING_INDEX(e.scopedLibraryIds, '~', n.n), '~', -1) != ''
				   AND SUBSTRING_INDEX(SUBSTRING_INDEX(e.scopedLibraryIds, '~', n.n), '~', -1) REGEXP '^[0-9]+$'"
			]
		], //migrate_scoped_library_ids_to_entitlements

		// Laura Escamilla - ByWater Solutions

		//alexander - Open Fifth

		//chloe - Open Fifth

		//Jacob - Open Fifth

		//Pedro - Open Fifth

		//James Staub - Nashville Public Library

		//Lucas Montoya - Theke Solutions

		//other

		//Talpa Search

		'migrate_hoopla_entitlements_to_library_id' => [
			'title' => 'Migrate Hoopla Entitlements to Use Library ID',
			'description' => 'Convert hoopla_entitlements from settingId to libraryId before schema restructure',
			'continueOnError' => true,
			'sql' => [
				// First, update existing entitlements to use libraryId from the hoopla_settings
				"UPDATE hoopla_entitlements he
				 INNER JOIN hoopla_settings hs ON he.settingId = hs.id
				 SET he.settingId = hs.libraryId
				 WHERE hs.libraryId IS NOT NULL"
			]
		], //migrate_hoopla_entitlements_to_library_id

		'hoopla_flex_availability_per_library' => [
			'title' => 'Update Hoopla Flex Availability for Per-Library Support',
			'description' => 'Add libraryId to hoopla_flex_availability to support per-library availability tracking',
			'continueOnError' => false,
			'sql' => [
				// Add libraryId to hoopla_flex_availability
				"ALTER TABLE hoopla_flex_availability
				 ADD COLUMN libraryId int(11) NOT NULL DEFAULT 0 AFTER hooplaId,
				 DROP INDEX hooplaId,
				 ADD UNIQUE KEY unique_hoopla_library (hooplaId, libraryId),
				 ADD INDEX idx_library_id (libraryId),
				 ADD FOREIGN KEY (libraryId) REFERENCES library(libraryId) ON DELETE CASCADE"
			]
		], //hoopla_flex_availability_per_library

		'hoopla_multi_library_schema' => [
			'title' => 'Update Hoopla Schema for Multi-Library Support',
			'description' => 'Restructure Hoopla tables to support one setting serving multiple libraries with per-library purchase model configuration',
			'continueOnError' => false,
			'sql' => [
				// Create junction table for hoopla settings to libraries first
				"CREATE TABLE IF NOT EXISTS hoopla_library_settings (
					id int(11) NOT NULL AUTO_INCREMENT,
					settingId bigint(20) NOT NULL,
					libraryId int(11) NOT NULL,
					enableFlex tinyint(1) DEFAULT 1 COMMENT 'Whether this library has Flex titles enabled',
					enableInstant tinyint(1) DEFAULT 1 COMMENT 'Whether this library has Instant titles enabled',
					dateAdded timestamp DEFAULT CURRENT_TIMESTAMP,
					dateUpdated timestamp DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
					PRIMARY KEY (id),
					UNIQUE KEY unique_setting_library (settingId, libraryId),
					INDEX idx_setting_id (settingId),
					INDEX idx_library_id (libraryId),
					INDEX idx_flex_enabled (enableFlex),
					INDEX idx_instant_enabled (enableInstant),
					FOREIGN KEY (libraryId) REFERENCES library(libraryId) ON DELETE CASCADE
				) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci",

				// Migrate existing hoopla_settings to junction table
				"INSERT IGNORE INTO hoopla_library_settings (settingId, libraryId, enableFlex, enableInstant)
				 SELECT id, libraryId, 1, 1 FROM hoopla_settings WHERE libraryId IS NOT NULL",

				// Remove the single libraryId from hoopla_settings
				"ALTER TABLE hoopla_settings DROP COLUMN IF EXISTS libraryId",

				// Update hoopla_entitlements to use libraryId instead of settingId
				"ALTER TABLE hoopla_entitlements
				 DROP FOREIGN KEY IF EXISTS fk_hoopla_entitlements_setting,
				 DROP INDEX IF EXISTS idx_setting_id,
				 DROP INDEX IF EXISTS idx_setting_active,
				 CHANGE COLUMN settingId libraryId int(11) NOT NULL,
				 DROP PRIMARY KEY,
				 ADD PRIMARY KEY (hooplaId, libraryId),
				 ADD INDEX idx_library_id (libraryId),
				 ADD INDEX idx_library_active (libraryId, active),
				 ADD FOREIGN KEY (libraryId) REFERENCES library(libraryId) ON DELETE CASCADE"
			]
		], //hoopla_multi_library_schema

		'hoopla_settings_add_last_record_processed' => [
			'title' => 'Add lastRecordProcessed to Hoopla Settings',
			'description' => 'Add field to track resume point for global content sync if interrupted',
			'continueOnError' => false,
			'sql' => [
				"ALTER TABLE hoopla_settings ADD COLUMN lastRecordProcessed BIGINT(20) DEFAULT 0 AFTER lastUpdateOfEntitlements"
			]
		], //hoopla_settings_add_last_record_processed

		'hoopla_export_remove_active_and_type' => [
			'title' => 'Remove Active and HooplaType from Hoopla Export',
			'description' => 'Remove active and hooplaType columns from hoopla_export as they belong in hoopla_entitlements (determined by entitlements API, not global content)',
			'continueOnError' => false,
			'sql' => [
				"ALTER TABLE hoopla_export DROP COLUMN IF EXISTS active",
				"ALTER TABLE hoopla_export DROP COLUMN IF EXISTS hooplaType"
			]
		], //hoopla_export_remove_active_and_type

		'hoopla_entitlements_add_hoopla_type' => [
			'title' => 'Add HooplaType to Hoopla Entitlements',
			'description' => 'Add hooplaType column to hoopla_entitlements since this info comes from entitlements API',
			'continueOnError' => false,
			'sql' => [
				"ALTER TABLE hoopla_entitlements ADD COLUMN IF NOT EXISTS hooplaType VARCHAR(20) DEFAULT NULL COMMENT 'Instant or Flex' AFTER purchaseModel"
			]
		], //hoopla_entitlements_add_hoopla_type

		'hoopla_settings_add_country_code' => [
			'title' => 'Add Country Code to Hoopla Settings',
			'description' => 'Add countryCode field to determine which country pricing and ratings to use (US, CA, NZ, AU)',
			'continueOnError' => false,
			'sql' => [
				"ALTER TABLE hoopla_settings ADD IF NOT EXISTS countryCode VARCHAR(2) DEFAULT 'US' COMMENT 'Country code for pricing and ratings (US, CA, NZ, AU)' AFTER lastRecordProcessed"
			]
		], //hoopla_settings_add_country_code

	];
}