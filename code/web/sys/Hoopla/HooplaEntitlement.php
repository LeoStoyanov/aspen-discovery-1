<?php

class HooplaEntitlement extends DataObject {
	public $__table = 'hoopla_entitlements';
	public $hooplaId;
	public $hooplaType;
	public $dateAdded;
	public $dateUpdated;

	static function getObjectStructure($context = ''): array {
		return [
			'hooplaId' => ['property' => 'hooplaId', 'type' => 'text', 'label' => 'Hoopla ID', 'description' => 'The Hoopla content ID', 'primaryKey' => true],
			'hooplaType' => ['property' => 'hooplaType', 'type' => 'text', 'label' => 'Hoopla Type', 'description' => 'Purchase model type (Instant or Flex)'],
			'dateAdded' => ['property' => 'dateAdded', 'type' => 'timestamp', 'label' => 'Date Added', 'description' => 'Date the entitlement was added'],
			'dateUpdated' => ['property' => 'dateUpdated', 'type' => 'timestamp', 'label' => 'Date Updated', 'description' => 'Date the entitlement was last updated'],
		];
	}

	/**
	 * Check if a library is entitled to a specific Hoopla title
	 * @param string $hooplaId The Hoopla content ID
	 * @param int $libraryId The library ID
	 * @return bool True if library has scope for this title
	 */
	public static function isLibraryEntitled(string $hooplaId, int $libraryId): bool {
		require_once ROOT_DIR . '/sys/Hoopla/HooplaEntitlementScope.php';
		$scope = new HooplaEntitlementScope();
		$scope->entitlementId = $hooplaId;
		$scope->libraryId = $libraryId;
		return $scope->find(true);
	}
}
