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
		'library_sso_settings' => [
			'title' => 'Library SSO Settings Junction Table',
			'description' => 'Create junction table to support multiple SSO settings per library',
			'continueOnError' => false,
			'sql' => [
				"CREATE TABLE IF NOT EXISTS library_sso_settings (
					id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
					libraryId INT NOT NULL,
					ssoSettingId INT NOT NULL,
					weight INT DEFAULT 0,
					enabled TINYINT DEFAULT 1,
					UNIQUE KEY library_sso (libraryId, ssoSettingId),
					INDEX idx_library (libraryId),
					INDEX idx_sso (ssoSettingId)
				)",
			],
		],
		'library_sso_settings_migration' => [
			'title' => 'Migrate Existing SSO Settings',
			'description' => 'Migrate data from library.ssoSettingId to library_sso_settings',
			'continueOnError' => true,
			'sql' => [
				"INSERT IGNORE INTO library_sso_settings (libraryId, ssoSettingId, weight, enabled)
				 SELECT libraryId, ssoSettingId, 0, 1
				 FROM library
				 WHERE ssoSettingId > 0",
			],
		],
		'library_remove_ssoSettingId' => [
			'title' => 'Remove old ssoSettingId column from library',
			'description' => 'Remove the old ssoSettingId column - replaced by junction table',
			'continueOnError' => true,
			'sql' => [
				"ALTER TABLE library DROP COLUMN IF EXISTS ssoSettingId",
			],
		],
		'ip_address_sso_settings' => [
			'title' => 'IP Address SSO Settings Junction Table',
			'description' => 'Create junction table to support multiple SSO settings per IP address',
			'continueOnError' => false,
			'sql' => [
				"CREATE TABLE IF NOT EXISTS ip_address_sso_settings (
					id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
					ipAddressId INT NOT NULL,
					ssoSettingId INT NOT NULL,
					UNIQUE KEY ip_sso (ipAddressId, ssoSettingId),
					INDEX idx_ip (ipAddressId),
					INDEX idx_sso (ssoSettingId)
				)",
			],
		],
		'ip_address_remove_ssoLogin' => [
			'title' => 'Remove old ssoLogin column from ip_lookup',
			'description' => 'Remove the old boolean ssoLogin column - replaced by junction table',
			'continueOnError' => true,
			'sql' => [
				"ALTER TABLE ip_lookup DROP COLUMN IF EXISTS ssoLogin",
			],
		],

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
