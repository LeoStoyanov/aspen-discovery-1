<?php

class HooplaEntitlementScope extends DataObject {
	public $__table = 'hoopla_entitlement_scopes';
	public $entitlementId;
	public $libraryId;

	static function getObjectStructure($context = ''): array {
		return [
			'entitlementId' => ['property' => 'entitlementId', 'type' => 'text', 'label' => 'Entitlement ID', 'description' => 'The Hoopla ID'],
			'libraryId' => ['property' => 'libraryId', 'type' => 'text', 'label' => 'Library ID', 'description' => 'The Library ID'],
		];
	}
}
