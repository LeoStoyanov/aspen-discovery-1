<?php
require_once ROOT_DIR . '/sys/Hoopla/HooplaScope.php';
require_once ROOT_DIR . '/sys/Hoopla/HooplaLibrarySetting.php';

class HooplaSetting extends DataObject {
	public $__table = 'hoopla_settings';
	public $id;
	public $apiUrl;
	public $apiUsername;
	public $apiPassword;
	public $accessToken;
	public $tokenExpirationTime;
	public $regroupAllRecords;
	public $runFullGlobalContentUpdate;
	public $lastUpdateOfChangedRecords;
	public $lastRecordProcessed;
	public $countryCode;

	private $_scopes;
	private $_librarySettings;
	static array $_objectStructure = [];

	public static function getObjectStructure($context = ''): array {
		if (isset(self::$_objectStructure[$context]) && self::$_objectStructure[$context] !== null) {
			return self::$_objectStructure[$context];
		}

		$hooplaScopeStructure = HooplaScope::getObjectStructure($context);
		unset($hooplaScopeStructure['settingId']);

		$structure = [
			'id' => [
				'property' => 'id',
				'type' => 'label',
				'label' => 'Id',
				'description' => 'The unique id',
			],
			'apiUrl' => [
				'property' => 'apiUrl',
				'type' => 'url',
				'label' => 'url',
				'description' => 'The URL to the API',
			],
			'apiUsername' => [
				'property' => 'apiUsername',
				'type' => 'text',
				'label' => 'API Username',
				'description' => 'The API Username provided by Hoopla when registering',
			],
			'apiPassword' => [
				'property' => 'apiPassword',
				'type' => 'storedPassword',
				'label' => 'API Password',
				'description' => 'The API Password provided by Hoopla when registering',
				'hideInLists' => true,
			],
			'globalContentEntitlements' => [
				'property' => 'globalContentEntitlements',
				'type' => 'section',
				'label' => 'Global Content & Entitlements API',
				'expandByDefault' => true,
				'properties' => [
					'countryCode' => [
						'property' => 'countryCode',
						'type' => 'enum',
						'label' => 'Country Code',
						'description' => 'Country code for pricing and ratings display',
						'values' => [
							'US' => 'United States (US)',
							'CA' => 'Canada (CA)',
							'NZ' => 'New Zealand (NZ)',
							'AU' => 'Australia (AU)',
						],
						'default' => 'US',
					],
					'runFullGlobalContentUpdate' => [
						'property' => 'runFullGlobalContentUpdate',
						'type' => 'checkbox',
						'label' => 'Run Full Global Content Update',
						'description' => 'Force a full global content sync on next run (ignores lastUpdateOfChangedRecords)',
						'default' => 0,
					],
					'regroupAllRecords' => [
						'property' => 'regroupAllRecords',
						'type' => 'checkbox',
						'label' => 'Regroup all Records',
						'description' => 'Whether or not all existing records should be regrouped',
						'default' => 0,
					],
					'lastUpdateOfChangedRecords' => [
						'property' => 'lastUpdateOfChangedRecords',
						'type' => 'timestamp',
						'label' => 'Last Update of Changed Records',
						'description' => 'Timestamp of last global content sync',
						'default' => 0,
					],
					'lastRecordProcessed' => [
						'property' => 'lastRecordProcessed',
						'type' => 'integer',
						'label' => 'Last Record Processed',
						'description' => 'Resume point for interrupted global content sync (0 = complete)',
						'default' => 0,
					],
				],
			],
			'librarySettings' => [
				'property' => 'librarySettings',
				'type' => 'oneToMany',
				'label' => 'Library Settings',
				'description' => 'Configure which libraries use this Hoopla setting and their purchase model preferences',
				'keyThis' => 'id',
				'keyOther' => 'settingId',
				'subObjectType' => 'HooplaLibrarySetting',
				'structure' => HooplaLibrarySetting::getObjectStructure($context),
				'sortable' => false,
				'storeDb' => true,
				'allowEdit' => true,
				'canEdit' => true,
				'canAddNew' => true,
				'canDelete' => true,
				'additionalOneToManyActions' => [],
			],
			'scopes' => [
				'property' => 'scopes',
				'type' => 'oneToMany',
				'label' => 'Scopes',
				'description' => 'Define scopes for the settings',
				'keyThis' => 'id',
				'keyOther' => 'settingId',
				'subObjectType' => 'HooplaScope',
				'structure' => $hooplaScopeStructure,
				'sortable' => false,
				'storeDb' => true,
				'allowEdit' => true,
				'canEdit' => true,
				'canAddNew' => true,
				'canDelete' => true,
				'additionalOneToManyActions' => [],
			],
		];

		self::$_objectStructure[$context] = $structure;
		return self::$_objectStructure[$context];
	}

	public function __toString() {
		$libraries = $this->getLibraryNames();
		$libraryText = empty($libraries) ? 'No libraries' : implode(', ', $libraries);
		return $libraryText . ' (' . $this->apiUsername . ')';
	}

	public function getLibraryNames(): array
	{
		$librarySettings = HooplaLibrarySetting::getLibrariesForSetting($this->id);
		$names = [];
		foreach ($librarySettings as $librarySetting) {
			$names[] = $librarySetting->getLibraryName();
		}
		return $names;
	}

	public function update($context = ''): bool|int
	{
		$ret = parent::update();
		if ($ret !== FALSE) {
			$this->saveScopes();
			$this->saveLibrarySettings();
		}
		return true;
	}

	public function insert($context = ''): bool|int
	{
		$ret = parent::insert();
		if ($ret !== FALSE) {
			if (empty($this->_scopes)) {
				$this->_scopes = [];
				$allScope = new HooplaScope();
				$allScope->settingId = $this->id;
				$allScope->name = "All Records";
				$allScope->includeEAudiobook = true;
				$allScope->maxCostPerCheckoutEAudiobook = 5;
				$allScope->includeEBooks = true;
				$allScope->maxCostPerCheckoutEBooks = 5;
				$allScope->includeEComics = true;
				$allScope->maxCostPerCheckoutEComics = 5;
				$allScope->includeMovies = true;
				$allScope->maxCostPerCheckoutMovies = 5;
				$allScope->includeMusic = true;
				$allScope->maxCostPerCheckoutTelevision = 5;

				$this->_scopes[] = $allScope;
			}
			$this->saveScopes();
			$this->saveLibrarySettings();
		}
		return $ret;
	}

	public function saveScopes() {
		if (isset ($this->_scopes) && is_array($this->_scopes)) {
			$this->saveOneToManyOptions($this->_scopes, 'settingId');
			unset($this->_scopes);
		}
	}

	public function saveLibrarySettings(): void {
		if (isset ($this->_librarySettings) && is_array($this->_librarySettings)) {
			$this->saveOneToManyOptions($this->_librarySettings, 'settingId');
			unset($this->_librarySettings);
		}
	}

	public function __get($name) {
		if ($name == "scopes") {
			if (!isset($this->_scopes) && $this->id) {
				$this->_scopes = [];
				$scope = new HooplaScope();
				$scope->settingId = $this->id;
				$scope->find();
				while ($scope->fetch()) {
					$this->_scopes[$scope->id] = clone($scope);
				}
			}
			return $this->_scopes;
		} elseif ($name == "librarySettings") {
			if (!isset($this->_librarySettings) && $this->id) {
				$this->_librarySettings = [];
				$librarySetting = new HooplaLibrarySetting();
				$librarySetting->settingId = $this->id;
				$librarySetting->find();
				while ($librarySetting->fetch()) {
					$this->_librarySettings[$librarySetting->id] = clone($librarySetting);
				}
			}
			return $this->_librarySettings;
		} else {
			return parent::__get($name);
		}
	}

	public function __set($name, $value) {
		if ($name == "scopes") {
			$this->_scopes = $value;
		} elseif ($name == "librarySettings") {
			$this->_librarySettings = $value;
		} else {
			parent::__set($name, $value);
		}
	}
}