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
		'user_list_transfer_requests' => [
			'title' => 'Create user_list_transfer_requests table',
			'description' => 'Creates the table for tracking user list transfer requests',
			'continueOnError' => false,
			'sql' => [
				"CREATE TABLE `user_list_transfer_requests` (
				  `id` int(11) NOT NULL AUTO_INCREMENT,
				  `listId` int(11) NOT NULL,
				  `fromUserId` int(11) NOT NULL,
				  `toUserId` int(11) NOT NULL,
				  `status` enum('pending','accepted','rejected','cancelled') NOT NULL DEFAULT 'pending',
				  `created` datetime DEFAULT CURRENT_TIMESTAMP,
				  `updated` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
				  PRIMARY KEY (`id`),
				  KEY `listId` (`listId`),
				  KEY `fromUserId` (`fromUserId`),
				  KEY `toUserId` (`toUserId`),
				  CONSTRAINT `fk_ultr_list` FOREIGN KEY (`listId`) REFERENCES `user_list` (`id`) ON DELETE CASCADE,
				  CONSTRAINT `fk_ultr_fromUser` FOREIGN KEY (`fromUserId`) REFERENCES `user` (`id`) ON DELETE CASCADE,
				  CONSTRAINT `fk_ultr_toUser` FOREIGN KEY (`toUserId`) REFERENCES `user` (`id`) ON DELETE CASCADE
				) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;"
			]
		],
		'list_transfer_permissions' => [
			'title' => 'Add List Transfer Permissions',
			'description' => 'Add permission group and permissions for transferring user lists.',
			'continueOnError' => false,
			'sql' => [
				"INSERT IGNORE INTO `permission_groups` (`groupKey`,`sectionName`,`label`,`description`) VALUES
					('transferLists','User Lists','Transfer Lists','Specify whether the role can transfer their own lists or force-transfer any list.')",
				"INSERT IGNORE INTO `permissions` (`sectionName`,`name`,`requiredModule`,`weight`,`description`) VALUES
					('User Lists', 'Transfer Your Lists', '', 10, 'Allows the user to transfer ownership of their own lists to another user with notification approval.'),
					('User Lists', 'Transfer All Lists', '', 11, 'Allows the user to immediately transfer ownership of any list without notification approval.')",
				"INSERT IGNORE INTO `permission_group_permissions` (`groupId`,`permissionId`) 
					SELECT pg.id, p.id 
					FROM `permission_groups` pg 
					JOIN `permissions` p ON p.name IN ('Transfer Your Lists','Transfer All Lists') 
					WHERE pg.groupKey = 'transferLists'"
			]
		],
		'allow_list_transfers_preference' => [
			'title' => 'Add User Preference for List Transfers',
			'description' => 'Add allowListTransfers column to user table to allow users to opt out of receiving list transfer requests.',
			'continueOnError' => false,
			'sql' => [
				"ALTER TABLE user ADD COLUMN allowListTransfers TINYINT(1) DEFAULT 1"
			]
		],
		'user_messages_date_created' => [
			'title' => 'Add date_created to user_messages',
			'description' => 'Adds a timestamp column to user_messages for sorting and SSE.',
			'continueOnError' => false,
			'sql' => [
				"ALTER TABLE user_messages ADD COLUMN date_created DATETIME DEFAULT CURRENT_TIMESTAMP"
			]
		],

		//alexander - Open Fifth

		//chloe - Open Fifth

		//James Staub - Nashville Public Library

		//Lucas Montoya - Theke Solutions

		//other
		
	];
}
