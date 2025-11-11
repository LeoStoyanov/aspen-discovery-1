<?php
/** @noinspection PhpMissingFieldTypeInspection */

class APIUsageLog extends DataObject {
	public $__table = 'api_usage_log';
	public $id;
	public $timestamp;
	public $module;
	public $method;
	public $userId;

	/**
	 * Log an individual API call with user information.
	 *
	 * @param string $module The API module (e.g., 'UserAPI').
	 * @param string $method The API method being called.
	 * @param User|null $user The authenticated user making the call.
	 * @return void
	 */
	static function logCall(string $module, string $method, User $user = null) : void {
		try {
			$apiUsageLog = new APIUsageLog();
			$apiUsageLog->timestamp = time();
			$apiUsageLog->module = $module;
			$apiUsageLog->method = $method;
			if ($user instanceof User) {
				$apiUsageLog->userId = $user->id;
			}

			$apiUsageLog->insert();

			// Periodically clean up old logs (every 100 calls, approximately).
			if (rand(1, 100) === 1) {
				self::cleanupOldLogs();
			}
		} catch (PDOException) {
			// This happens if the table has not been created, ignore it.
		}
	}

	/**
	 * Clean up old API usage logs older than 90 days.
	 *
	 * @return void
	 */
	static function cleanupOldLogs() : void {
		try {
			$apiUsageLog = new APIUsageLog();
			$cutoffTimestamp = time() - (30 * 24 * 60 * 60); // 30 days ago
			$apiUsageLog->whereAdd("timestamp < $cutoffTimestamp");
			$apiUsageLog->delete(true);
		} catch (PDOException) {
			// Ignore errors during cleanup
		}
	}
}
