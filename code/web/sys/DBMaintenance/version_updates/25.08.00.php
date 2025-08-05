<?php

function getUpdates25_08_00(): array {
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
		'library_local_ill_email' => [
			'title' => 'Library - Local ILL Email',
			'description' => 'Add Local ILL Email to Library Settings',
			'continueOnError' => false,
			'sql' => [
				"ALTER TABLE library ADD COLUMN localIllEmail varchar(255) default ''"
			]
		], //library_local_ill_email
		'materials_request_add_source' => [
			'title' => 'Materials Request - Add Source',
			'description' => 'Add Source to Materials Request to differentiate between Local ILL and standard requests',
			'sql' => [
				"ALTER TABLE materials_request ADD COLUMN source TINYINT DEFAULT 1"
			]
		], //materials_request_add_source

		//katherine - Grove

		//kirstien - Grove

		//kodi - Grove
		'library_you_might_also_like' => [
			'title' => 'Library You Might Also Like Setting',
			'description' => 'Add a setting for libraries for the "You Might Also Like" feature to disable or enable with restrictions.',
			'sql' => [
				"ALTER TABLE library ADD COLUMN showYouMightAlsoLike TINYINT(1) DEFAULT 1;",
				"UPDATE library SET showYouMightAlsoLike =0 WHERE showWhileYouWait=0;"
			],
		], //library_you_might_also_like
		// Myranda - Grove

		//Yanjun Li - ByWater

		// Leo Stoyanov - BWS
		'add_allow_material_requests_branch_choice_setting' => [
			'title' => 'Add Allow Material Requests Branch Choice Setting',
			'description' => 'Add "Allow Material Requests Branch Choice" setting for the ILS Request System.',
			'continueOnError' => false,
			'sql' => [
				'ALTER TABLE library ADD COLUMN IF NOT EXISTS allowMaterialRequestsBranchChoice tinyint(1) DEFAULT 0;'
			]
		],//add_allow_material_requests_branch_choice_setting
		'update_record_to_include_defaults' => [
			'title' => 'Update RecordToInclude Column Defaults to Match PHP Defaults',
			'description' => 'Update database column defaults for includeHoldableOnly, includeItemsOnOrder, and includeEContent to match the PHP class defaults.',
			'continueOnError' => false,
			'sql' => [
				'ALTER TABLE library_records_to_include CHANGE COLUMN includeHoldableOnly includeHoldableOnly tinyint(1) NOT NULL DEFAULT 0',
				'ALTER TABLE library_records_to_include CHANGE COLUMN includeItemsOnOrder includeItemsOnOrder tinyint(1) NOT NULL DEFAULT 1',
				'ALTER TABLE library_records_to_include CHANGE COLUMN includeEContent includeEContent tinyint(1) NOT NULL DEFAULT 1',
				'ALTER TABLE location_records_to_include CHANGE COLUMN includeHoldableOnly includeHoldableOnly tinyint(1) NOT NULL DEFAULT 0',
				'ALTER TABLE location_records_to_include CHANGE COLUMN includeItemsOnOrder includeItemsOnOrder tinyint(1) NOT NULL DEFAULT 1',
				'ALTER TABLE location_records_to_include CHANGE COLUMN includeEContent includeEContent tinyint(1) NOT NULL DEFAULT 1',
			]
		], //update_record_to_include_defaults
		'add_updating_contact_info_from_ils' => [
			'title' => 'Add the Option to "Automatically Update Contact Information from the ILS"',
			'description' => 'Add the Option to "Automatically Update Contact Information from the ILS" ',
			'continueOnError' => false,
			'sql' => [
				"ALTER TABLE location ADD COLUMN allowUpdatingContactInfoFromILS TINYINT(1) DEFAULT 0",
			],
		], // add_updating_contact_info_from_ils
		'add_max_hold_cancellation_date_field' => [
			'title' => 'Add Max Hold Cancellation Date Field',
			'description' => 'Add the Max Hold Cancellation Date field for when hold cancellations are enabled.',
			'continueOnError' => true,
			'sql' => [
				'ALTER TABLE library ADD COLUMN IF NOT EXISTS maxHoldCancellationDate int(11) DEFAULT -1 AFTER defaultNotNeededAfterDays;'
			]
		], //add_max_hold_cancellation_date_field

		// Laura Escamilla - ByWater Solutions

		//alexander - Open Fifth
		'control_display_of_user_dropdown_in_community_engagement_admin_view' => [
			'title' => 'Control User Select Type in Admin View',
			'description' => 'Add options for how to select users in the admin view section',
			'sql' => [
				"ALTER TABLE library ADD COLUMN communityEngagementAdminUserSelect VARCHAR(20) DEFAULT 'dropdown'",
			],
		], //control_display_of_user_dropdown_in_community_engagement_admin_view
		'display_only_users_from_current_library_in_user_search_admin_view' => [
			'title' => 'Display Only Users From Current Library in User Search Admin View',
			'description' => 'Add option to display users from all libraries or only the current library location when searching by user in Admin View',
			'sql' => [
				"ALTER TABLE library ADD COLUMN displayOnlyUsersForLocationInUserAdmin TINYINT(1) DEFAULT 0",
			],
		], //display_only_users_from_current_library_in_user_search_admin_view
		'allow_admin_to_enroll_users_via_admin_view' => [
			'title' => 'Allow Admin To Enroll Users Via Admin View',
			'description' => 'Add control over whether admin can enroll users via the admin view page',
			'sql' => [
				"ALTER TABLE library ADD COLUMN allowAdminToEnrollUsersInAdminView TINYINT(1) DEFAULT 0",
			],
		], //allow_admin_to_enroll_users_via_admin_view
		'add_admin_control_over_digital_reward_display' => [
			'title' => 'Add Admin Control Over Digital Reward Display',
			'description' => 'Add the option for libraries to choose whether the digital reward displays all the time or only once awarded',
			'sql' => [
				"ALTER TABLE library ADD COLUMN displayDigitalRewardOnlyWhenAwarded TINYINT(1) DEFAULT 0",
			]
		], //add_admin_control_over_digital_reward_display
		'add_ability_to_upload_placeholder_image' => [
			'title' => 'Add Ability to Upload Placeholder Image',
			'description' => 'Add the ability to upload a placeholder image',
			'sql' => [
				"ALTER TABLE library ADD COLUMN digitalRewardPlaceholderImage VARCHAR(100) DEFAULT ''",
			]
		], //add_ability_to_upload_placeholder_image
		'add_table_for_extra_credit' => [
			'title' => 'Add Table For Extra Credit',
			'description' => 'Add a table to for extra credit activites',
			'sql' => [
				"CREATE TABLE ce_extra_credit (
					id INT NOT NULL AUTO_INCREMENT PRIMARY KEY, 
					name VARCHAR(100) NOT NULL, 
					description VARCHAR(225),
					allowPatronProgressInput TINYINT DEFAULT 0
				)ENGINE = InnoDB"
			],
		], //add_table_for_extra_credit
		'add_extra_credit_to_campaigns' => [
			'title' => 'Add Extra Credit to Campaigns',
			'description' => 'Add the ability to add extra credit activities to campaigns',
			'sql' => [
				"ALTER TABLE ce_campaign ADD COLUMN addExtraCreditActivities TINYINT DEFAULT 0 "
			],
		], //add_extra_credit_to_campaigns
		'add_campaign_extra_credit_activities' => [
			'title' => 'Add Campaign Extra Credit Activities',
			'description' => 'Add a new table to link campaigns and extra credit activities',
			'sql' => [
				"CREATE TABLE ce_campaign_extra_credit (
					id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
					weight INT(11) NOT NULL DEFAULT 0,
					campaignId INT NOT NULL,
					extraCreditId INT NOT NULL,
					goal INT DEFAULT 0, 
					reward INT(11) DEFAULT -1
				)ENGINE = InnoDB",
			],
		], //add_campaign_extra_credit_activities
		'add_extra_credit_progress_table' => [
			'title' => 'Add Extra Credit Progress Table',
			'description' => 'Store progress for of extra credit activites for each user',
			'sql' => [
				"CREATE Table ce_campaign_extra_credit_activity_users_progress (
					 id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
					 userId INT NOT NULL,
					 campaignId INT NOT NULL,
					 extraCreditId INT NOT NULL,
					 progress INT NOT NULL,
					 rewardGiven TINYINT DEFAULT 0
				)ENGINE = InnoDB",
			],
		], //add_extra_credit_progress_table

		//chloe - Open Fifth


		//Jacob - Open Fifth

		//James Staub - Nashville Public Library
		'librarian_facebook_report_permissions' => [
			'title' => 'View Librarian Facebook report permissions',
			'description' => 'Create permissions for Librarian Facebook report',
			'continueOnError' => true,
			'sql' => [
				"INSERT INTO permissions (sectionName, name, requiredModule, weight, description) VALUES 
					('Circulation Reports', 'View Librarian Facebook', '', 70, 'Allows the user to view the Librarian Facebook.')
				",
			]
		], //librarian_facebook_report_permissions

		//Lucas Montoya - Theke Solutions

		//other

		//Talpa Search
		
	];
}
