<?php

class HooplaFlexAvailability extends DataObject {
	public $__table = 'hoopla_flex_availability';   // table name

	public $id;
	public $hooplaId;
	public $holdsQueueSize;
	public $availableCopies;
	public $totalCopies;
	public $status;
}