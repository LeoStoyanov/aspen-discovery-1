<?php

require_once ROOT_DIR . '/sys/DB/DataObject.php';

class HooplaLibrarySetting extends DataObject {
	public $__table = 'hoopla_library_settings';
	public $id;
	public $settingId;
	public $libraryId;
	public $enableFlex;
	public $enableInstant;
	public $dateAdded;
	public $dateUpdated;

	static array $_objectStructure = [];

	static function getObjectStructure($context = ''): array {
		if (isset(self::$_objectStructure[$context]) && self::$_objectStructure[$context] !== null) {
			return self::$_objectStructure[$context];
		}

		$libraryList = Library::getLibraryList(true);

		$structure = [
			'id' => [
				'property' => 'id',
				'type' => 'label',
				'label' => 'Id',
				'description' => 'The unique id',
			],
			'settingId' => [
				'property' => 'settingId',
				'type' => 'hidden',
				'label' => 'Setting Id',
				'description' => 'The Hoopla setting this belongs to',
			],
			'libraryId' => [
				'property' => 'libraryId',
				'type' => 'enum',
				'values' => $libraryList,
				'label' => 'Library',
				'description' => 'The library this setting applies to',
				'required' => true,
			],
			'enableFlex' => [
				'property' => 'enableFlex',
				'type' => 'checkbox',
				'label' => 'Enable Flex',
				'description' => 'Whether Flex titles are enabled for this library',
				'default' => 1,
			],
			'enableInstant' => [
				'property' => 'enableInstant',
				'type' => 'checkbox',
				'label' => 'Enable Instant',
				'description' => 'Whether Instant titles are enabled for this library',
				'default' => 1,
			],
		];

		self::$_objectStructure[$context] = $structure;
		return self::$_objectStructure[$context];
	}

	public function getLibraryName(): string
	{
		require_once ROOT_DIR . '/sys/LibraryLocation/Library.php';
		$library = new Library();
		$library->libraryId = $this->libraryId;
		if ($library->find(true)) {
			return $library->displayName;
		}
		return "Library {$this->libraryId}";
	}

	public function __toString() {
		return $this->getLibraryName() . ' (Flex: ' . ($this->enableFlex ? 'Yes' : 'No') . ', Instant: ' . ($this->enableInstant ? 'Yes' : 'No') . ')';
	}

	/**
	 * Get all libraries configured for a specific Hoopla setting
	 * @param int $settingId
	 * @return array Array of library configurations
	 */
	public static function getLibrariesForSetting($settingId): array
	{
		$librarySettings = new HooplaLibrarySetting();
		$librarySettings->settingId = $settingId;
		$librarySettings->find();

		$libraries = [];
		while ($librarySettings->fetch()) {
			$libraries[] = clone $librarySettings;
		}

		return $libraries;
	}

	/**
	 * Check if a library has a specific purchase model enabled
	 * @param int $libraryId
	 * @param string $purchaseModel 'Flex' or 'Instant'
	 * @return bool
	 */
	public static function isPurchaseModelEnabled($libraryId, $purchaseModel) {
		$librarySettings = new HooplaLibrarySetting();
		$librarySettings->libraryId = $libraryId;
		if ($librarySettings->find(true)) {
			if (strtolower($purchaseModel) === 'flex') {
				return (bool)$librarySettings->enableFlex;
			} elseif (strtolower($purchaseModel) === 'instant') {
				return (bool)$librarySettings->enableInstant;
			}
		}
		// Default to enabled if no configuration found
		return true;
	}

	/**
	 * Get all library IDs that have a specific purchase model enabled
	 * @param string $purchaseModel 'Flex' or 'Instant'
	 * @return array Array of library IDs
	 */
	public static function getLibrariesWithPurchaseModelEnabled($purchaseModel) {
		$librarySettings = new HooplaLibrarySetting();
		$field = (strtolower($purchaseModel) === 'flex') ? 'enableFlex' : 'enableInstant';
		$librarySettings->$field = 1;
		$librarySettings->find();

		$libraryIds = [];
		while ($librarySettings->fetch()) {
			$libraryIds[] = $librarySettings->libraryId;
		}

		return $libraryIds;
	}
}