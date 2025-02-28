<?php

require_once ROOT_DIR . '/Action.php';

class MyAccount_Logout extends Action {

	public function launch() {
		if(UserAccount::isLoggedInViaSSO()) {
			global $library;
			require_once ROOT_DIR . '/sys/Authentication/SSOSetting.php';
			$ssoSettings = new SSOSetting();
			$ssoSettings->id = $library->ssoSettingId;
			if ($ssoSettings->find(true)) {

				// Check if we should only perform a local logout.
				//if (!empty($ssoSettings->localLogout)) {
					// Just do a local logout without redirecting to SSO provider.
					UserAccount::logout();
					session_write_close();

					if (isset($_REQUEST['return'])) {
						header('Location: ' . $_REQUEST['return']);
					} else {
						header('Location: /');
					}
					die();
				//}

				if($ssoSettings->service == 'saml') {
					if ($ssoSettings->ssoSPLogoutUrl) {
						UserAccount::logout();
						session_write_close();
						header('Location: ' . $ssoSettings->ssoSPLogoutUrl);
						die();
					}
				} else {
					if ($ssoSettings->ssoSPLogoutUrl) {
						$_REQUEST['return'] = $ssoSettings->ssoSPLogoutUrl;
					}
				}
			}
		}

		UserAccount::logout();
		session_write_close();

		if(isset($_REQUEST['return'])) {
			header('Location: ' . $_REQUEST['return']);
			die();
		} else {
			header('Location: /');
			die();
		}
	}

	function getBreadcrumbs(): array {
		return [];
	}
}