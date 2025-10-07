<?php /** @noinspection PhpMissingFieldTypeInspection */

class HooplaFlexAvailability extends DataObject {
	public $__table = 'hoopla_flex_availability';   // table name

	public $id;
	public $hooplaId;
	public $libraryId;
	public $holdsQueueSize;
	/** @noinspection PhpUnused */
	public $availableCopies;
	public $totalCopies;
	public $status;
}
