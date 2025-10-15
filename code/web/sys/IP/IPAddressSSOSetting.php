<?php
/** @noinspection PhpMissingFieldTypeInspection */

require_once ROOT_DIR . '/sys/DB/DataObject.php';

class IPAddressSSOSetting extends DataObject {
	public $__table = 'ip_address_sso_settings';
	public $id;
	public $ipAddressId;
	public $ssoSettingId;

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
				'description' => 'The SSO setting that is allowed from this IP',
				'required' => true,
			],
		];
	}
}
