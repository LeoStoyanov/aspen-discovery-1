<?php
require_once ROOT_DIR . '/services/Admin/ObjectEditor.php';
require_once ROOT_DIR . '/sys/UserLists/UserList.php';

class Admin_UserLists extends ObjectEditor {
	function getObjectType() {
		return 'UserList';
	}

	function getToolName() {
		return 'UserLists';
	}

	function getPageTitle() {
		return 'User Lists';
	}

	function getPermissionName() {
		return 'Administer Users';
	}

	function getObjectStructure($context = '') {
		return UserList::getObjectStructure('admin');
	}
}
