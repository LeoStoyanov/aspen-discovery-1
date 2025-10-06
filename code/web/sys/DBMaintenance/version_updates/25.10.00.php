<?php

/** @noinspection PhpUnused */
function getUpdates25_10_00(): array {
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
		'addOptionsForIndexing896To899AsSeries' => [
			'title' => 'Add Options For Indexing 896 To 899 As Series',
			'description' => 'Add Options For Indexing 896 To 899 As Series',
			'continueOnError' => false,
			'sql' => [
				'ALTER TABLE indexing_profiles ADD COLUMN index896asSeries TINYINT(1) DEFAULT 1',
				'ALTER TABLE indexing_profiles ADD COLUMN index897asSeries TINYINT(1) DEFAULT 1',
				'ALTER TABLE indexing_profiles ADD COLUMN index898asSeries TINYINT(1) DEFAULT 1',
				'ALTER TABLE indexing_profiles ADD COLUMN index899asSeries TINYINT(1) DEFAULT 1'
			]
		], //addOptionsForIndexing896To899AsSeries
		'addHooplaRecordExtractionBatchSize' => [
			'title' => 'Add Hoopla Record Extraction Batch Size',
			'description' => 'Add Hoopla Record Extraction Batch Size',
			'continueOnError' => false,
			'sql' => [
				'ALTER TABLE hoopla_settings ADD COLUMN recordExtractionBatchSize INT DEFAULT 500',
			]
		], //addHooplaRecordExtractionBatchSize
		'add_permission_for_econtent_sorting' => [
			'title' => 'Add permissions for eContent sorting',
			'description' => 'Add permissions for eContent sorting',
			'continueOnError' => false,
			'sql' => [
				"INSERT INTO permissions (sectionName, name, requiredModule, weight, description) VALUES ('Grouped Work Display', 'Administer All eContent Sorting', '', 60, 'Allows users to change how eContent Sources are sorted within a grouped work for all libraries.')",
				"INSERT INTO permissions (sectionName, name, requiredModule, weight, description) VALUES ('Grouped Work Display', 'Administer Library eContent Sorting', '', 70, 'Allows users to change how eContent Sources are sorted within a grouped work for their library.')",
				"INSERT INTO role_permissions(roleId, permissionId) VALUES ((SELECT roleId from roles where name='opacAdmin'), (SELECT id from permissions where name='Administer All eContent Sorting'))",
			]
		], //add_permission_for_econtent_sorting
		'add_permission_group_for_econtent_sorting' => [
			'title' => 'Add permission group for eContent sorting',
			'description' => 'Add permission group for eContent sorting',
			'continueOnError' => false,
			'sql' => [
				"INSERT INTO `permission_groups` (`groupKey`,`sectionName`,`label`,`description`) VALUES
					('adminEContentSorting','Grouped Work Display','Administer eContent Source Sorting','Allows users to change how eContent Sources are sorted within a grouped work.');",
				"INSERT IGNORE INTO `permission_group_permissions` (`groupId`,`permissionId`) SELECT pg.id, p.id FROM `permission_groups` pg JOIN `permissions` p ON p.name IN ('Administer All eContent Sorting','Administer Library eContent Sorting') WHERE pg.groupKey = 'adminEContentSorting'",
			]
		], //add_permission_group_for_econtent_sorting
		'create_econtent_sorting_tables' => [
			'title' => 'Create eContent sorting tables',
			'description' => 'Create eContent sorting tables',
			'continueOnError' => true,
			'sql' => [
				'CREATE TABLE IF NOT EXISTS grouped_work_econtent_sort_group (
					id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
					name VARCHAR(255) NOT NULL UNIQUE,
					sortAvailableSourcesFirst TINYINT(1) DEFAULT 1,
					sortMethod TINYINT(1) DEFAULT 1
				)',
				'CREATE TABLE IF NOT EXISTS grouped_work_econtent_sort (
					id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
					eContentSortingGroupId INT(11) NOT NULL,
					eContentSource VARCHAR(255) NOT NULL,
					weight INT(11) NOT NULL,
					UNIQUE(eContentSortingGroupId, eContentSource)
				)',
			],
		], //create_econtent_sorting_tables
		'create_default_econtent_sorting' => [
			'title' => 'Create default eContent sorting',
			'description' => 'Create default eContent sorting',
			'continueOnError' => false,
			'sql' => [
				"INSERT INTO grouped_work_econtent_sort_group (id, name, sortAvailableSourcesFirst, sortMethod) VALUES (1, 'Default', 1, 1)"
			]
		], //create_default_econtent_sorting
		'link_econtent_sorting_to_display_settings' => [
			'title' => 'Link eContent sorting to display settings',
			'description' => 'Link eContent sorting to display settings',
			'continueOnError' => false,
			'sql' => [
				'ALTER TABLE grouped_work_display_settings ADD COLUMN eContentSortingGroupId INT(11) DEFAULT 1'
			]
		], //link_econtent_sorting_to_display_settings
		'add_series_sort_method' => [
			'title' => 'Add series sorting method',
			'description' => 'Add series sorting method',
			'continueOnError' => false,
			'sql' => [
				'ALTER TABLE series ADD COLUMN sortMethod TINYINT DEFAULT 1'
			]
		], //add_series_sort_method

		//katherine - Grove
		'add_include_in_reports_option_to_event_type' => [
			'title' => 'Add Include In Reports option to Event Types',
			'description' => 'Allows specific event types to be excluded from reports',
			'continueOnError' => false,
			'sql' => [
				'ALTER TABLE event_type ADD COLUMN includeInReports TINYINT DEFAULT 1',
			]
		], //add_include_in_reports_option_to_event_type

		//kirstien - Grove
		'addEditionPromptSettingForLibrary' => [
			'title' => 'Add Option For Prompting For Edition When Placing Hold',
			'description' => 'Add Option For Prompting For Edition When Placing Hold at the Library Level',
			'continueOnError' => false,
			'sql' => [
				'ALTER TABLE library ADD COLUMN holdPromptForEditions TINYINT DEFAULT 0',
			]
		],
		//addEditionPromptSettingForLibrary
		'addEditionPromptSettingForUser' => [
			'title' => 'Add Options For Storing User Preference on Prompting For Edition When Placing Hold',
			'description' => 'Add Options For Storing User Preference on Prompting For Edition When Placing Hold',
			'continueOnError' => false,
			'sql' => [
				'ALTER TABLE user ADD COLUMN rememberHoldPromptForEdition TINYINT DEFAULT 0',
				'ALTER TABLE user ADD COLUMN holdPromptForEdition TINYINT DEFAULT 1',
			]
		],//addEditionPromptSettingForUser
		'removeHoldPromptForEditionSettingForUser' => [
			'title' => 'Remove hold prompt for edition setting',
			'description' => 'Remove hold prompt for edition setting, only rememberHoldPromptForEdition is needed',
			'continueOnError' => false,
			'sql' => [
				'ALTER TABLE user DROP COLUMN holdPromptForEdition',
			]
		],//removeHoldPromptForEditionSettingForUser

		//kodi - Grove

		// Myranda - Grove
		'add_dark_mode_checkbox' => [
			'title' => 'Add checkbox for if theme is dark mode or not',
			'description' => 'Adds checkbox to themes for additional CSS modifications applicable to dark color schemes',
			'continueOnError' => true,
			'sql' => [
				'ALTER TABLE themes ADD COLUMN isDarkColorScheme TINYINT(1) DEFAULT 0',
			]
		],
		//add_high_contrast_checkbox

		//Yanjun Li - ByWater
		'add_hoopla_configurable_indexing_time' => [
			'title' => 'Add Configurable Hoopla Indexing Time',
			'description' => 'Add Hoopla Indexing Time',
			'continueOnError' => false,
			'sql' => [
				'ALTER TABLE hoopla_settings ADD COLUMN indexingTime INT DEFAULT 1',
			]
		], //add_hoopla_configurable_indexing_time

		// Leo Stoyanov - BWS
		'add_indexes_for_more_user_list_sort_options' => [
			'title' => 'Add Indexes For More User List Sort Options',
			'description' => 'Add indexes idx_publicationDateId and idx_callNumberId for faster User List sorting.',
			'continueOnError' => false,
			'sql' => [
				'CREATE INDEX idx_publicationDateId ON grouped_work_records(publicationDateId)',
				'CREATE INDEX idx_callNumberId ON grouped_work_record_items (callNumberId)'
			],
		], // add_indexes_for_more_user_list_sort_options
		'add_num_total_entries_to_show_in_more_to_grouped_work_facet' => [
			'title' => 'Add Total Num Entries To Show In More To Grouped Work Facet',
			'description' => 'Add configurable field to control how many facet values show in the "More..." popup/expansion.',
			'continueOnError' => false,
			'sql' => [
				'ALTER TABLE grouped_work_facet ADD COLUMN numTotalEntriesToShowInMore INT(11) NOT NULL DEFAULT 30',
			]
		], // add_num_total_entries_to_show_in_more_to_grouped_work_facet
		'add_show_copies_for_periodicals_with_no_items_setting' => [
			'title' => 'Add Show Copies for Periodicals with No Items Setting',
			'description' => 'Add a setting to control whether Copies accordion is shown for periodicals with no items.',
			'continueOnError' => false,
			'sql' => [
				'ALTER TABLE grouped_work_display_settings ADD COLUMN IF NOT EXISTS showCopiesForPeriodicalsWithNoItems TINYINT(1) DEFAULT 0'
			]
		], //add_show_copies_for_periodicals_with_no_iems_setting
		'add_enable_third_party_sms_notifications_option' => [
			'title' => 'Add "Enable Third Party SMS Notifications" Option',
			'description' => 'Add "Enable Third Party SMS Notifications" option for CarlX to Library System settings.',
			'continueOnError' => true,
			'sql' => [
				'ALTER TABLE library ADD COLUMN enableThirdPartySMSNotifications TINYINT(1) DEFAULT 0'
			],
		], // add_enable_third_party_sms_notifications_option
		'remove_request_tracker_tables' => [
			'title' => 'Remove Request Tracker Database Tables',
			'description' => 'Drop all database tables related to the Request Tracker implementation.',
			'continueOnError' => true,
			'sql' => [
				'DROP TABLE IF EXISTS component_ticket_link',
				'DROP TABLE IF EXISTS development_task_ticket_link',
				'DROP TABLE IF EXISTS request_tracker_connection',
				'DROP TABLE IF EXISTS ticket',
				'DROP TABLE IF EXISTS ticket_component_feed',
				'DROP TABLE IF EXISTS ticket_queue_feed',
				'DROP TABLE IF EXISTS ticket_severity_feed',
				'DROP TABLE IF EXISTS ticket_status_feed',
				'DROP TABLE IF EXISTS ticket_trend_bugs_by_severity',
				'DROP TABLE IF EXISTS ticket_trend_by_component',
				'DROP TABLE IF EXISTS ticket_trend_by_partner',
				'DROP TABLE IF EXISTS ticket_trend_by_queue'
			]
		], // remove_request_tracker_tables
		'remove_request_tracker_permissions' => [
			'title' => 'Remove Request Tracker Permissions',
			'description' => 'Remove permissions and role assignments related to the Request Tracker implementation.',
			'continueOnError' => true,
			'sql' => [
				'DELETE FROM role_permissions WHERE permissionId IN (SELECT id FROM permissions WHERE name IN ("Submit Ticket", "Administer Request Tracker Connection", "View Active Tickets", "Set Development Priorities"))',
				'DELETE FROM permissions WHERE name IN ("Submit Ticket", "Administer Request Tracker Connection", "View Active Tickets", "Set Development Priorities")',
				'DROP TABLE IF EXISTS development_priorities'
			]
		], // remove_request_tracker_permissions
		'remove_request_tracker_greenhouse_settings' => [
			'title' => 'Remove Request Tracker Greenhouse Settings',
			'description' => 'Remove Request Tracker fields from greenhouse_settings table.',
			'continueOnError' => true,
			'sql' => [
				'ALTER TABLE greenhouse_settings DROP COLUMN IF EXISTS requestTrackerBaseUrl',
				'ALTER TABLE greenhouse_settings DROP COLUMN IF EXISTS requestTrackerAuthToken'
			]
		], //remove_request_tracker_greenhouse_settings
		'remove_ticket_email_system_variable' => [
			'title' => 'Remove Ticket Email System Variable',
			'description' => 'Remove ticketEmail column from system_variables table.',
			'continueOnError' => true,
			'sql' => [
				'ALTER TABLE system_variables DROP COLUMN IF EXISTS ticketEmail'
			]
		], // remove_ticket_email_system_variable
		'themes_show_button_shimmer' => [
			'title' => 'Themes - Show Button Shimmer',
			'description' => 'Add showButtonShimmer setting to themes table to allow libraries to disable shimmer effect on circulation buttons.',
			'continueOnError' => true,
			'sql' => [
				'ALTER TABLE themes ADD COLUMN showButtonShimmer TINYINT(1) DEFAULT 1',
			]
		], // themes_show_button_shimmer

		//alexander - Open Fifth

		//chloe - Open Fifth


		//Jacob - Open Fifth

		//Pedro - Open Fifth


		//James Staub - Nashville Public Library

		//Lucas Montoya - Theke Solutions

		//other
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
			'description' => 'Add countryCode field to determine which country pricing and ratings (US, CA, NZ, AU)',
			'continueOnError' => false,
			'sql' => [
				"ALTER TABLE hoopla_settings ADD IF NOT EXISTS countryCode VARCHAR(2) DEFAULT 'US' COMMENT 'Country code for pricing and ratings (US, CA, NZ, AU)' AFTER lastRecordProcessed"
			]
		], //hoopla_settings_add_country_code

		'hoopla_library_settings_add_purchase_model_flags' => [
			'title' => 'Add Purchase Model Control Flags to Hoopla Library Settings',
			'description' => 'Add flags to control full entitlements updates and clearing disabled purchase models',
			'continueOnError' => false,
			'sql' => [
				"ALTER TABLE hoopla_library_settings ADD COLUMN runFullEntitlementsUpdate TINYINT(1) DEFAULT 0 COMMENT 'Force full entitlements sync on next run' AFTER enableInstant",
				"ALTER TABLE hoopla_library_settings ADD COLUMN clearDisabledFlex TINYINT(1) DEFAULT 0 COMMENT 'Run Flex entitlements one more time to clear inactive titles' AFTER runFullEntitlementsUpdate",
				"ALTER TABLE hoopla_library_settings ADD COLUMN clearDisabledInstant TINYINT(1) DEFAULT 0 COMMENT 'Run Instant entitlements one more time to clear inactive titles' AFTER clearDisabledFlex"
			]
		], //hoopla_library_settings_add_purchase_model_flags

		//Talpa Search

		// Brendan Lawlor

	];
}
