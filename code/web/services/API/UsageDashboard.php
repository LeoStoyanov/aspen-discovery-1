<?php
require_once ROOT_DIR . '/services/Admin/Dashboard.php';
require_once ROOT_DIR . '/sys/SystemLogging/APIUsage.php';

class API_UsageDashboard extends Admin_Dashboard {
	function launch(): void {
		global $interface;

		$instanceName = $this->loadInstanceInformation('APIUsage');
		$this->loadDates();

		//Load stats by module.
		//moduleName => [
		//		method => [
		//			usageThisMonth = number
		//			usageLastMonth = number
		//			usageThisYear = number
		//			usageAllTime = number
		$statsByModule = [];
		$this->getStats($instanceName, $this->thisMonth, $this->thisYear, $statsByModule, 'usageThisMonth');
		$this->getStats($instanceName, $this->lastMonth, $this->lastMonthYear, $statsByModule, 'usageLastMonth');
		$this->getStats($instanceName, null, $this->thisYear, $statsByModule, 'usageThisYear');
		$this->getStats($instanceName, null, null, $statsByModule, 'usageAllTime');

		$interface->assign('statsByModule', $statsByModule);
		$this->getUserAPILogs();

		$this->display('dashboard.tpl', 'Aspen Usage Dashboard');
	}

	/**
	 * @param string|null $instanceName
	 * @param string|null $month
	 * @param string|null $year
	 * @param array $statsByModule The stats being loaded
	 * @param string $statsPeriodName The period of stats being loaded
	 * @return void
	 */
	function getStats(?string $instanceName, ?string $month, ?string $year, array &$statsByModule, string $statsPeriodName): void {
		$usage = new APIUsage();
		if (!empty($instanceName)) {
			$usage->instance = $instanceName;
		}
		if ($month != null) {
			$usage->month = $month;
		}
		if ($year != null) {
			$usage->year = $year;
		}
		$usage->selectAdd();
		$usage->selectAdd('module');
		$usage->selectAdd('method');
		$usage->selectAdd('SUM(numCalls) as numCalls');
		$usage->orderBy('module, method');
		$usage->groupBy('module, method');

		$usage->find();

		while ($usage->fetch()) {
			if (!array_key_exists($usage->module, $statsByModule)) {
				$statsByModule[$usage->module] = [];
			}
			if (!array_key_exists($usage->method, $statsByModule[$usage->module])) {
				$statsByModule[$usage->module][$usage->method] = [
					'usageThisMonth' => 0,
					'usageLastMonth' => 0,
					'usageThisYear' => 0,
					'usageAllTime' => 0,
				];
			}
			$statsByModule[$usage->module][$usage->method][$statsPeriodName] = $usage->numCalls;
		}
	}

	function getBreadcrumbs(): array {
		$breadcrumbs = [];
		$breadcrumbs[] = new Breadcrumb('/Admin/Home', 'Administration Home');
		$breadcrumbs[] = new Breadcrumb('/Admin/Home#system_reports', 'System Reports');
		$breadcrumbs[] = new Breadcrumb('', 'Usage Dashboard');
		return $breadcrumbs;
	}

	function getActiveAdminSection(): string {
		return 'system_reports';
	}

	function canView(): bool {
		return UserAccount::userHasPermission([
			'View Dashboards',
			'View System Reports',
		]);
	}

	/**
	 * Load detailed UserAPI logs with user information.
	 *
	 * @return void
	 */
	function getUserAPILogs(): void {
		global $interface;

		require_once ROOT_DIR . '/sys/SystemLogging/APIUsageLog.php';
		require_once ROOT_DIR . '/sys/Account/User.php';

		$method = $_REQUEST['logMethod'] ?? '';
		$limit = isset($_REQUEST['logLimit']) ? (int)$_REQUEST['logLimit'] : 100;
		$startDate = $_REQUEST['logStartDate'] ?? date('Y-m-d', strtotime('-7 days'));
		$endDate = $_REQUEST['logEndDate'] ?? date('Y-m-d');
		$startTimestamp = strtotime($startDate . ' 00:00:00');
		$endTimestamp = strtotime($endDate . ' 23:59:59');

		// Get recent API calls.
		$apiUsageLog = new APIUsageLog();
		$apiUsageLog->module = 'UserAPI';
		if (!empty($method)) {
			$apiUsageLog->method = $method;
		}
		$apiUsageLog->whereAdd("timestamp >= $startTimestamp AND timestamp <= $endTimestamp");
		$apiUsageLog->orderBy('timestamp DESC');
		$apiUsageLog->limit(0, $limit);

		$logs = [];
		$apiUsageLog->find();
		while ($apiUsageLog->fetch()) {
			$displayName = 'N/A';
			$userBarcode = 'N/A';

			if ($apiUsageLog->userId) {
				$user = new User();
				$user->id = $apiUsageLog->userId;
				if ($user->find(true)) {
					$displayName = $user->getDisplayName();
					$userBarcode = $user->getBarcode();
				}
			}

			$logs[] = [
				'timestamp' => date('Y-m-d H:i:s', $apiUsageLog->timestamp),
				'userId' => $apiUsageLog->userId,
				'displayName' => $displayName,
				'userBarcode' => $userBarcode,
				'method' => $apiUsageLog->method,
			];
		}

		// Get summary statistics by user.
		$apiUsageLog = new APIUsageLog();
		$apiUsageLog->module = 'UserAPI';
		if (!empty($method)) {
			$apiUsageLog->method = $method;
		}
		$apiUsageLog->whereAdd("timestamp >= $startTimestamp AND timestamp <= $endTimestamp");
		$apiUsageLog->whereAdd("userId IS NOT NULL");
		$apiUsageLog->selectAdd();
		$apiUsageLog->selectAdd('userId, COUNT(*) as callCount');
		$apiUsageLog->groupBy('userId');
		$apiUsageLog->orderBy('callCount DESC');
		$apiUsageLog->limit(0, 50);

		$userStats = [];
		$apiUsageLog->find();
		while ($apiUsageLog->fetch()) {
			$callCount = $apiUsageLog->__get('callCount');

			$displayName = 'N/A';
			$userBarcode = 'N/A';

			if ($apiUsageLog->userId) {
				$user = new User();
				$user->id = $apiUsageLog->userId;
				if ($user->find(true)) {
					$displayName = $user->getDisplayName();
					$userBarcode = $user->getBarcode();
				}
			}

			$userStats[] = [
				'userId' => $apiUsageLog->userId,
				'displayName' => $displayName,
				'userBarcode' => $userBarcode,
				'callCount' => $callCount,
			];
		}

		// Get list of all unique methods for the dropdown.
		$apiUsageLogMethods = new APIUsageLog();
		$apiUsageLogMethods->module = 'UserAPI';
		$apiUsageLogMethods->selectAdd();
		$apiUsageLogMethods->selectAdd('DISTINCT method');
		$apiUsageLogMethods->orderBy('method');

		$availableMethods = [];
		$apiUsageLogMethods->find();
		while ($apiUsageLogMethods->fetch()) {
			$availableMethods[] = $apiUsageLogMethods->method;
		}

		$interface->assign('detailedLogs', $logs);
		$interface->assign('userStats', $userStats);
		$interface->assign('availableMethods', $availableMethods);
		$interface->assign('logMethod', $method);
		$interface->assign('logLimit', $limit);
		$interface->assign('logStartDate', $startDate);
		$interface->assign('logEndDate', $endDate);
	}
}