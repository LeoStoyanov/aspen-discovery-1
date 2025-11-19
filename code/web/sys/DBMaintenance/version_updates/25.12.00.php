<?php

/** @noinspection PhpUnused */
function getUpdates25_12_00(): array {
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

		//kirstien - Grove

		//kodi - Grove

		// Myranda - Grove

		//Yanjun Li - ByWater

		// Leo Stoyanov - BWS
		'indexed_collection_code' => [
			'title' => 'Create indexed_collection_code Table',
			'description' => 'Create table to store collection codes for items.',
			'continueOnError' => false,
			'sql' => [
				"CREATE TABLE IF NOT EXISTS indexed_collection_code (
					id INT(11) NOT NULL AUTO_INCREMENT PRIMARY KEY,
					collectionCode VARCHAR(255) NOT NULL,
					UNIQUE KEY collectionCode (collectionCode)
				) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci"
			]
		],
		'grouped_work_record_items_collectionCodeId' => [
			'title' => 'Add collectionCodeId to grouped_work_record_items',
			'description' => 'Add column to store collection code reference in items table.',
			'continueOnError' => false,
			'sql' => [
				"ALTER TABLE grouped_work_record_items ADD COLUMN IF NOT EXISTS collectionCodeId INT(11) DEFAULT NULL",
				"ALTER TABLE grouped_work_record_items ADD INDEX IF NOT EXISTS collectionCodeId (collectionCodeId)"
			]
		],
		'grouped_work_display_settings_showCollectionCode' => [
			'title' => 'Add showCollectionCode to grouped_work_display_settings',
			'description' => 'Add setting to control whether collection code is shown in copy details.',
			'continueOnError' => false,
			'sql' => [
				"ALTER TABLE grouped_work_display_settings ADD COLUMN IF NOT EXISTS showCollectionCode TINYINT(1) DEFAULT 0"
			]
		],

		//alexander - Open Fifth

		//chloe - Open Fifth

		//James Staub - Nashville Public Library

		//Lucas Montoya - Theke Solutions

		//other
		
	];
}
