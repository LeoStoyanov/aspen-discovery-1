<?php

/** @noinspection PhpUnused */
function getUpdates25_11_00(): array {
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
		'saved_search_email_sms_notifications' => [
			'title' => 'Saved Search Email and SMS Notifications',
			'description' => 'Add user preferences for receiving email and SMS notifications when saved searches have new results.',
			'continueOnError' => false,
			'sql' => [
				'ALTER TABLE user ADD COLUMN IF NOT EXISTS notifySavedSearchViaEmail TINYINT(1) DEFAULT 0',
				'ALTER TABLE user ADD COLUMN IF NOT EXISTS notifySavedSearchViaSMS TINYINT(1) DEFAULT 0',
			]
		], // saved_search_email_sms_notifications
		'saved_search_email_template' => [
			'title' => 'Saved Search Email Template',
			'description' => 'Create default email template for saved search notifications.',
			'continueOnError' => false,
			'sql' => [
				"INSERT INTO email_template (name, templateType, languageCode, subject, plainTextBody) VALUES
				('Default Saved Search Notification', 'savedSearchUpdate', 'en',
				'New titles in your saved search \"{searchTitle}\"',
				'Hello,\n\nYour saved search \"{searchTitle}\" at {libraryName} has {numNewResults} new title(s).\n\nClick here to view the new results:\n{searchUrl}\n\nTo manage your notification preferences, visit your account settings.\n\nThank you,\n{libraryName}')",
			]
		], // saved_search_email_template

		//alexander - Open Fifth

		//chloe - Open Fifth


		//Jacob - Open Fifth

		//Pedro - Open Fifth


		//James Staub - Nashville Public Library

		//Lucas Montoya - Theke Solutions

		//other

		//Talpa Search

		// Brendan Lawlor

	];
}
