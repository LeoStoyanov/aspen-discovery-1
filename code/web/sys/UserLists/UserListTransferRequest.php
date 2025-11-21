<?php
/** @noinspection PhpMissingFieldTypeInspection */

class UserListTransferRequest extends DataObject {
	public $__table = 'user_list_transfer_requests';
	public $id;
	public $listId;
	public $fromUserId;
	public $toUserId;
	public $status;
	public $created;
	public $updated;

	public function getNumericColumnNames(): array {
		return [
			'listId',
			'fromUserId',
			'toUserId',
		];
	}
}
