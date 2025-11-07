<?php

require_once ROOT_DIR . '/Action.php';
require_once ROOT_DIR . '/services/Admin/ObjectEditor.php';
require_once ROOT_DIR . '/sys/LibraryLocation/Sublocation.php';

class Admin_Sublocations extends ObjectEditor {

	function getObjectType(): string {
		return 'Sublocation';
	}

	function getToolName(): string {
		return 'Sublocations';
	}

	function getPageTitle(): string {
		return 'Sublocations';
	}

	function getAllObjects($page, $recordsPerPage): array {
		$object = new Sublocation();
		$object->orderBy($this->getSort());
		$allowedLocationIds = Sublocation::getPermittedLocationIdsForCurrentUser();
		if (is_array($allowedLocationIds)) {
			if (count($allowedLocationIds) > 0) {
				$object->whereAddIn('locationId', $allowedLocationIds, false);
			} else {
				$object->whereAdd('1 = 0');
			}
		}
		$object->orderBy($this->getSort());
		$this->applyFilters($object);
		$object->limit(($page - 1) * $recordsPerPage, $recordsPerPage);
		$object->find();
		$list = [];
		while ($object->fetch()) {
			$list[$object->id] = clone $object;
		}
		return $list;
	}

	function getDefaultSort(): string {
		return 'weight asc';
	}

	function getObjectStructure($context = ''): array {
		$structure = Sublocation::getObjectStructure($context);
		unset ($structure['weight']);
		return $structure;
	}

	function getPrimaryKeyColumn(): string {
		return 'id';
	}

	function getIdKeyColumn(): string {
		return 'id';
	}

	function getBreadcrumbs(): array {
		$breadcrumbs = [];
		$breadcrumbs[] = new Breadcrumb('/Admin/Home', 'Administration Home');
		$breadcrumbs[] = new Breadcrumb('/Admin/Home#primary_configuration', 'Primary Configuration');
		if (!empty($this->activeObject) && $this->activeObject instanceof Sublocation) {
			$breadcrumbs[] = new Breadcrumb('/Admin/Locations?objectAction=edit&id=' . $this->activeObject->locationId, 'Location');
		}
		$breadcrumbs[] = new Breadcrumb('', 'Sublocation');
		return $breadcrumbs;
	}

	function getActiveAdminSection(): string {
		return 'primary_configuration';
	}

	function getNumObjects(): int {
		if ($this->_numObjects == null) {
			$object = new Sublocation();
			$allowedLocationIds = Sublocation::getPermittedLocationIdsForCurrentUser();
			if (is_array($allowedLocationIds)) {
				if (count($allowedLocationIds) > 0) {
					$object->whereAddIn('locationId', $allowedLocationIds, false);
				} else {
					$object->whereAdd('1 = 0');
				}
			}
			$this->_numObjects = $object->count();
		}
		return $this->_numObjects;
	}

	function canView(): bool {
		return Sublocation::userCanAdminSublocations();
	}

	public function canAddNew(): bool {
		return Sublocation::userCanAdminSublocations();
	}

	public function canDelete(): bool {
		return Sublocation::userCanAdminSublocations();
	}

	public function canEdit(DataObject $object): bool {
		/** @var Sublocation $object */
		return $this->canManageLocationId($object->locationId);
	}

	public function canBatchEdit(): bool {
		return Sublocation::userCanAdminSublocations() && parent::canBatchEdit();
	}

	public function canBatchDelete(): bool {
		return Sublocation::userCanAdminSublocations() && parent::canBatchDelete();
	}

	public function canExportToCSV(): bool {
		return Sublocation::userCanAdminSublocations() && parent::canExportToCSV();
	}

	function showReturnToList() : bool {
		return false;
	}

	private function canManageLocationId(?int $locationId) : bool {
		if (!Sublocation::userCanAdminSublocations()) {
			return false;
		}
		if (Sublocation::userCanAdminAllSublocations()) {
			return true;
		}
		$allowedLocationIds = Sublocation::getPermittedLocationIdsForCurrentUser();
		if ($allowedLocationIds === null) {
			return true;
		}
		return in_array($locationId, $allowedLocationIds, true);
	}
}
