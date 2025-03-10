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

		// Leo Stoyanov - BWS
		'materials_request_archive_option' => [
			'title' => 'Add Archive Option for Materials Request',
			'description' => 'Add new isArchived field to materials_request_status table to allow archiving of materials requests.',
			'continueOnError' => true,
			'sql' => [
				"ALTER TABLE materials_request_status ADD COLUMN IF NOT EXISTS isArchived TINYINT(1) DEFAULT 0",
			],
		],

		//alexander - PTFS-Europe

		//chloe - PTFS-Europe

		//James Staub - Nashville Public Library

		//Lucas Montoya - Theke Solutions

		//other

	];
}