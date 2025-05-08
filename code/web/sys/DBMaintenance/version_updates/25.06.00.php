<?php

function getUpdates25_06_00(): array {
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
		'set_check_in_dates_for_orphaned_reading_history_entries' => [
			'title' => 'Set Check-In Dates for Orphaned Reading History Entries',
			'description' => 'Set the check-in dates for reading history entries that are non-existent grouped works in Aspen.',
			'sql' => [
				"UPDATE user_reading_history_work rhe 
    			 LEFT JOIN grouped_work gw ON gw.permanent_id = rhe.groupedWorkPermanentId 
				 SET rhe.checkInDate = COALESCE(rhe.checkOutDate, UNIX_TIMESTAMP()) 
				 WHERE rhe.checkInDate IS NULL AND (rhe.groupedWorkPermanentId = '' OR gw.id IS NULL);",
			]
		], //set_check_in_dates_for_orphaned_reading_history_entries

		// Laura Escamilla - ByWater Solutions

		//alexander - Open Fifth

		//chloe - Open Fifth

		//James Staub - Nashville Public Library

		//Lucas Montoya - Theke Solutions

		//other

	];
}
