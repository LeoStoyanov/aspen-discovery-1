<?php /** @noinspection PhpMissingFieldTypeInspection */

class HooplaExtract extends DataObject {
	public $id;
	public $hooplaId;
	public $active;
	public $title;
	public $kind;
	public $pa;  //Parental Advisory
	public $demo;
	public $profanity;
	public $rating; // eg TV parental guidance rating
	public $abridged;
	public $price;
	public $rawChecksum;
	public $rawResponse;
	public $dateFirstDetected;
	public $type;

	public $__table = 'hoopla_export';

	public function getCompressedColumnNames(): array {
		return ['rawResponse'];
	}

}