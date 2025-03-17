<?php

function getUpdates25_04_00(): array {
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

		//Yanjun Li - ByWater
		'Hoopla_Settings_Updates' => [
        	    'title' => 'Migrate Hoopla Flex Settings',
	            'description' => 'Seperate Hoopla Flex and Hoopla Instant settings',
	            'continueOnError' => false,
	            'sql' => [
			    'ALTER TABLE hoopla_settings ADD COLUMN IF NOT EXISTS hooplaInstantEnabled TINYINT(1) DEFAULT 0',
                	    'ALTER TABLE hoopla_settings CHANGE COLUMN IF EXISTS runFullUpdate runFullUpdateInstant TINYINT(1) DEFAULT 0',
               		    'ALTER TABLE hoopla_settings CHANGE COLUMN IF EXISTS lastUpdateOfChangedRecords lastUpdateOfChangedRecordsInstant BIGINT DEFAULT 0',
		            'ALTER TABLE hoopla_settings CHANGE COLUMN  IF EXISTS lastUpdateOfAllRecords lastUpdateOfAllRecordsInstant BIGINT DEFAULT 0',
	                    'ALTER TABLE hoopla_settings ADD COLUMN IF NOT EXISTS hooplaFlexEnabled TINYINT(1) DEFAULT 0',
	                    'ALTER TABLE hoopla_settings ADD COLUMN IF NOT EXISTS runFullUpdateFlex TINYINT(1) DEFAULT 0',
	                    'ALTER TABLE hoopla_settings ADD COLUMN IF NOT EXISTS lastUpdateOfChangedRecordsFlex BIGINT DEFAULT 0',
    	                    'ALTER TABLE hoopla_settings ADD COLUMN IF NOT EXISTS lastUpdateOfAllRecordsFlex BIGINT DEFAULT 0',
           	    ]
        	],//Hoopla_Settings_Updates
        	'Hoopla_Flex_Availability' => [
            		'title' => 'Hoopla Flex Availability',
            		'description' => 'Get availability for Hoopla Flex titles',
            		'continueOnError' => false,
            		'sql' => [
		                'CREATE TABLE IF NOT EXISTS hoopla_flex_availability (
		                   id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
		                    hooplaId BIGINT NOT NULL,
		                    holdsQueueSize INT NOT NULL,
		                    availableCopies INT NOT NULL,
		                    totalCopies INT NOT NULL,
		                    status VARCHAR(10) NOT NULL,
		                    UNIQUE KEY `hooplaId` (hooplaId)
		                )'
		            ]
	        ],//Hoopla Flex Availability

	        'Hoopla_export_table_add_type_column' => [
	            'title' => 'Hoopla Export Table Add Type Column',
	            'description' => 'Add type column to hoopla_export table',
	            'continueOnError' => false,
	            'sql' => [
	                'ALTER TABLE hoopla_export ADD COLUMN IF NOT EXISTS type VARCHAR(10) DEFAULT NULL'
	            ]
		],//Hoopla export table add type column

		// Leo Stoyanov - BWS
		'add_ignore_on_order_records_for_title_selection' => [
			'title' => 'Add ignoreOnOrderRecordsForTitleSelection to indexing profiles',
			'description' => 'Adds a setting to skip on-order records when selecting titles for display in grouped works (Koha-specific)',
			'sql' => [
				"ALTER TABLE indexing_profiles ADD COLUMN IF NOT EXISTS ignoreOnOrderRecordsForTitleSelection TINYINT(1) DEFAULT 0"
			],
		], // add_ignore_on_order_records_for_title_selection
		'remove_palace_project_regroup_flag' => [
			'title' => 'Remove Unused Palace Project Regroup Option',
			'description' => 'Remove regroupAllRecords column from palace_project_settings table as it is never used.',
			'continueOnError' => false,
			'sql' => [
				'ALTER TABLE palace_project_settings DROP COLUMN IF EXISTS regroupAllRecords'
			]
		], //remove_palace_project_regroup_flag


		//alexander - PTFS-Europe

		//chloe - PTFS-Europe

		//James Staub - Nashville Public Library

		//Lucas Montoya - Theke Solutions

		//other

	];
}