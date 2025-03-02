<?php

function getUpdates25_03_00(): array {
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

		// Leo Stoyanov - BWS
		'add_ignore_on_order_records_for_title_selection' => [
			'title' => 'Add ignoreOnOrderRecordsForTitleSelection to indexing profiles',
			'description' => 'Adds a setting to skip on-order records when selecting titles for display in grouped works (Koha-specific)',
			'sql' => [
				"ALTER TABLE indexing_profiles ADD COLUMN ignoreOnOrderRecordsForTitleSelection TINYINT(1) DEFAULT 0"
			],
		], // add_ignore_on_order_records_for_title_selection

		//alexander - PTFS-Europe

		//chloe - PTFS-Europe

		//James Staub - Nashville Public Library

		//Lucas Montoya - Theke Solutions

		//other

	];
}