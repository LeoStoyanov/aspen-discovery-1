<?php
/** @noinspection PhpMissingFieldTypeInspection */

require_once ROOT_DIR . '/sys/DB/DataObject.php';

class LibrarySSOSetting extends DataObject {
	public $__table = 'library_sso_settings';
	public $id;
	public $libraryId;
	public $ssoSettingId;
	public $weight;
	public $enabled;

	static function getObjectStructure($context = ''): array {
		require_once ROOT_DIR . '/sys/Authentication/SSOSetting.php';

		$ssoList = [];
		$ssoSetting = new SSOSetting();
		$ssoSetting->orderBy('name');
		$ssoSetting->find();
		while ($ssoSetting->fetch()) {
			$ssoList[$ssoSetting->id] = $ssoSetting->name;
		}

		return [
			'id' => [
				'property' => 'id',
				'type' => 'label',
				'label' => 'Id',
				'description' => 'The unique id',
			],
			'ssoSettingId' => [
				'property' => 'ssoSettingId',
				'type' => 'enum',
				'values' => $ssoList,
				'label' => 'SSO Setting',
				'description' => 'The SSO setting to use',
				'required' => true,
			],
			'weight' => [
				'property' => 'weight',
				'type' => 'integer',
				'label' => 'Weight (Display Order)',
				'description' => 'Lower numbers appear first on the login page',
				'default' => 0,
			],
			'enabled' => [
				'property' => 'enabled',
				'type' => 'checkbox',
				'label' => 'Enabled',
				'description' => 'Whether this SSO setting is enabled for the library',
				'default' => 1,
			],
		];
	}
}
