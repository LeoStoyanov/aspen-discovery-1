<?php

require_once ROOT_DIR . '/sys/Grouping/GroupedWorkAlternateTitle.php';
require_once ROOT_DIR . '/services/Admin/ObjectEditor.php';

class Admin_AlternateTitles extends ObjectEditor {
	function getObjectType(): string {
		return 'GroupedWorkAlternateTitle';
	}

	function getToolName(): string {
		return 'AlternateTitles';
	}

	function getPageTitle(): string {
		return 'Manual Grouping Authorities';
	}

	function getAllObjects($page, $recordsPerPage): array {
		$object = new GroupedWorkAlternateTitle();
		$object->orderBy($this->getSort());
		$this->applyFilters($object);
		$object->limit(($page - 1) * $recordsPerPage, $recordsPerPage);
		$object->find();
		$objectList = [];
		while ($object->fetch()) {
			$objectList[$object->id] = clone $object;
		}
		return $objectList;
	}

	function getDefaultSort(): string {
		return 'dateAdded desc';
	}

	function getObjectStructure($context = ''): array {
		return GroupedWorkAlternateTitle::getObjectStructure($context);
	}

	function getPrimaryKeyColumn(): string {
		return 'id';
	}

	function getIdKeyColumn(): string {
		return 'id';
	}

	function getInstructions(): string {
		return 'https://help.aspendiscovery.org/help/catalog/groupedworks';
	}

	function getBreadcrumbs(): array {
		$breadcrumbs = [];
		$breadcrumbs[] = new Breadcrumb('/Admin/Home', 'Administration Home');
		$breadcrumbs[] = new Breadcrumb('/Admin/Home#cataloging', 'Catalog / Grouped Works');
		$breadcrumbs[] = new Breadcrumb('/Admin/AlternateTitles', 'Alternate Titles');
		return $breadcrumbs;
	}

	function getActiveAdminSection(): string {
		return 'cataloging';
	}

	function canView(): bool {
		return UserAccount::userHasPermission('Manually Group and Ungroup Works');
	}

	function canAddNew() {
		return false;
	}

	/**
	 * Define special filter mappings for fields that require custom handling
	 */
	protected function getSpecialFilterMappings(): array {
		return [
			'addedByName' => [
				'sourceField' => 'addedBy',
				'targetClass' => 'User',
				'targetMethod' => 'getDisplayName'
			]
		];
	}

	/**
	 * Override applyFilters to add debugging for the special filter
	 */
	function applyFilters(DataObject $object) {
		$filterFields = $this->getFilterFields($object::getObjectStructure($this->getContext()));
		$appliedFilters = $this->getAppliedFilters($filterFields);
		$specialMappings = $this->getSpecialFilterMappings();

		// Debug: Check what filters are being applied
		global $logger;
		$logger->log("Applied filters: " . print_r($appliedFilters, true), Logger::LOG_ERROR);
		$logger->log("Special mappings: " . print_r($specialMappings, true), Logger::LOG_ERROR);

		foreach ($appliedFilters as $fieldName => $filter) {
			$logger->log("Processing filter for field: $fieldName", Logger::LOG_ERROR);
			if (isset($specialMappings[$fieldName])) {
				$logger->log("Using special filter for: $fieldName", Logger::LOG_ERROR);
				// Handle special filter
				$this->applySpecialFilter($object, $specialMappings[$fieldName], $filter);
			} else {
				$logger->log("Using normal filter for: $fieldName", Logger::LOG_ERROR);
				// Handle normal filter
				$this->applyFilter($object, $fieldName, $filter);
			}
		}
	}
}