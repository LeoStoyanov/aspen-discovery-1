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
		'library_user_defined_fields_table' => [
			'title' => 'Library User Defined Fields Table',
			'description' => 'Create table for library user defined fields.',
			'sql' => [
				"CREATE TABLE IF NOT EXISTS library_user_defined_field (
					id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
					libraryId INT NOT NULL,
					fieldNumber VARCHAR(30) NOT NULL,
					label VARCHAR(255) DEFAULT '',
					required TINYINT(1) DEFAULT 0,
					maxLength INT DEFAULT 255,
					INDEX (libraryId),
					UNIQUE KEY library_field (libraryId, fieldNumber)
				) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",
			],
		],

		//alexander - Open Fifth

		//chloe - Open Fifth

		//James Staub - Nashville Public Library

		//Lucas Montoya - Theke Solutions

		//other
		
	];
}
