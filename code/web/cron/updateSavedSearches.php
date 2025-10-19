<?php
require_once __DIR__ . '/../bootstrap.php';
require_once __DIR__ . '/../bootstrap_aspen.php';

require_once ROOT_DIR . '/sys/SearchEntry.php';
require_once ROOT_DIR . '/sys/SearchUpdateLogEntry.php';

require_once ROOT_DIR . '/sys/Account/UserNotificationToken.php';
require_once ROOT_DIR . '/sys/Notifications/ExpoNotification.php';
require_once ROOT_DIR . '/sys/CronLogEntry.php';
$cronLogEntry = new CronLogEntry();
$cronLogEntry->startTime = time();
$cronLogEntry->name = 'Updating Saved Searches';
$cronLogEntry->insert();

//Create a log entry
$searchUpdateLogEntry = new SearchUpdateLogEntry();
$searchUpdateLogEntry->startTime = time();
$searchUpdateLogEntry->insert();

set_time_limit(0);

//Get a list of all saved searches
$search = new SearchEntry();
$search->saved = 1;
$search->searchSource = 'local';
$search->find();

global $library;
global $solrScope;
global $configArray;

$defaultSolrScope = $solrScope;
if ($search->getNumResults() > 0) {
	$searchUpdateLogEntry->numSearches = $search->getNumResults();
	$searchUpdateLogEntry->update();
	$allSearches = $search->fetchAll('id');
	$numProcessed = 0;
	foreach ($allSearches as $searchId) {
		$searchEntry = new SearchEntry();
		$searchEntry->id = $searchId;
		if ($searchEntry->find(true)) {
			//Get the home library of the user
			$userForSearch = new User();
			$userForSearch->id = $searchEntry->user_id;

			if ($userForSearch->find(true)) {
				$homeLibrary = $userForSearch->getHomeLibrary();
				if ($homeLibrary == null) {
					$solrScope = $defaultSolrScope;
				} else {
					$solrScope = $homeLibrary->subdomain;
				}
			} else {
				continue;
			}

			$searchObject = SearchObjectFactory::initSearchObject();
			$size = strlen($searchEntry->search_object);
			$minSO = unserialize($searchEntry->search_object);
			$searchObject = SearchObjectFactory::deminify($minSO);

			$searchObject->removeFilterByPrefix('time_since_added');
			$searchObject->addFilter('time_since_added:Week');
			$searchObject->setFieldsToReturn('id');
			$searchObject->setLimit(10);

			$searchResult = $searchObject->processSearch();
			if (!$searchResult instanceof AspenError && empty($searchResult['error'])) {
				$numResults = $searchObject->getResultTotal();
				$hasNewResults = $numResults > 0;
				$searchEntry->hasNewResults = $hasNewResults;
				if (!empty($searchEntry->lastUpdated)) {
					$lastUpdated = strtotime($searchEntry->lastUpdated);
					$oneWeekLater = strtotime("+7 day", $lastUpdated);
					$oneWeekLater = date("Y-m-d", $oneWeekLater);
					$today = date("Y-m-d");
					if ($oneWeekLater == $today) {
						$searchEntry->lastUpdated = $today;
					} else {
						$searchEntry->hasNewResults = 0;
					}
				} else {
					$searchEntry->lastUpdated = date("Y-m-d");
				}
				if ($searchEntry->update() > 0) {
					$searchUpdateLogEntry->numUpdated++;
					if ($searchEntry->hasNewResults && $userForSearch->canReceiveNotifications('notifySavedSearch')) {
						global $logger;
						$logger->log("New results in search " . $searchEntry->title . " for user " . $userForSearch->id, Logger::LOG_ERROR);
						$appScheme = 'aspen-lida';
						require_once ROOT_DIR . '/sys/SystemVariables.php';
						$systemVariables = SystemVariables::getSystemVariables();
						if ($systemVariables && !empty($systemVariables->appScheme)) {
							$appScheme = $systemVariables->appScheme;
						}
						$notificationToken = new UserNotificationToken();
						$notificationToken->userId = $userForSearch->id;
						$notificationToken->notifySavedSearch = 1;
						$notificationToken->find();
						while ($notificationToken->fetch()) {
							$logger->log("Found notification push token for user " . $userForSearch->id, Logger::LOG_ERROR);
							$body = [
								'to' => $notificationToken->pushToken,
								'title' => 'New Titles',
								'body' => 'New titles have been added to your saved search "' . $searchEntry->title . '" at the library. Check them out!',
								'categoryId' => 'savedSearch',
								'channelId' => 'savedSearch',
								'data' => ['url' => urlencode($appScheme . '://user/saved_search?search=' . $searchEntry->id . "&name=" . $searchEntry->title)],
							];
							$expoNotification = new ExpoNotification();
							$expoNotification->sendExpoPushNotification($body, $notificationToken->pushToken, $searchEntry->user_id, "saved_search");
							$expoNotification = null;
						}
						$notificationToken->__destruct();
						$notificationToken = null;

						if ($userForSearch->notifySavedSearchViaEmail == 1 && !empty($userForSearch->email)) {
							require_once ROOT_DIR . '/sys/Email/Mailer.php';
							require_once ROOT_DIR . '/sys/Email/EmailTemplate.php';

							$emailTemplate = new EmailTemplate();
							$emailTemplate->templateType = 'savedSearchUpdate';
							$emailTemplate->languageCode = $userForSearch->interfaceLanguage ?? 'en';
							if (!$emailTemplate->find(true)) {
								// Fall back to default language.
								$emailTemplate->languageCode = 'en';
								if (!$emailTemplate->find(true)) {
									$logger->log("No email template found for saved search notifications.", Logger::LOG_ERROR);
									$emailTemplate = null;
								}
							}

							if ($emailTemplate != null) {
								$searchUrl = $configArray['Site']['url'] . '/Search/Results?saved=' . $searchEntry->id;
								$libraryName = $homeLibrary->displayName ?? 'Library';
								$variables = [
									'{searchTitle}' => $searchEntry->title,
									'{numNewResults}' => $numResults,
									'{searchUrl}' => $searchUrl,
									'{libraryName}' => $libraryName
								];

								$subject = str_replace(array_keys($variables), array_values($variables), $emailTemplate->subject);
								$body = str_replace(array_keys($variables), array_values($variables), $emailTemplate->plainTextBody);

								$mailer = new Mailer();
								$result = $mailer->send($userForSearch->email, $subject, $body);
								if ($result) {
									$logger->log("Sent saved search email notification to user " . $userForSearch->id, Logger::LOG_DEBUG);
								} else {
									$logger->log("Failed to send saved search email notification to user " . $userForSearch->id, Logger::LOG_ERROR);
								}
							}
						}

						if ($userForSearch->notifySavedSearchViaSMS == 1 && !empty($userForSearch->phone)) {
							require_once ROOT_DIR . '/sys/SMS/TwilioSetting.php';

							$twilioSettings = new TwilioSetting();
							if (isset($homeLibrary->twilioSettingId) && $homeLibrary->twilioSettingId > 0) {
								$twilioSettings->id = $homeLibrary->twilioSettingId;
								if ($twilioSettings->find(true)) {
									$searchUrl = $configArray['Site']['url'] . '/Search/Results?saved=' . $searchEntry->id;
									$libraryName = $homeLibrary->displayName ?? 'Library';
									$message = "{$libraryName}: {$numResults} new title(s) in \"{$searchEntry->title}\". View: {$searchUrl}";

									// Truncate if needed (SMS limit ~160 chars).
									if (strlen($message) > 160) {
										$message = substr($message, 0, 157) . '...';
									}

									$result = $twilioSettings->sendMessage($message, $userForSearch->phone);
									if (isset($result['success']) && $result['success']) {
										$logger->log("Sent saved search SMS notification to user " . $userForSearch->id, Logger::LOG_DEBUG);
									} else {
										$errorMsg = $result['message'] ?? 'Unknown error';
										$logger->log("Failed to send saved search SMS notification to user " . $userForSearch->id . ": " . $errorMsg, Logger::LOG_ERROR);
									}
								}
							}
						}
					}
				}
			} else {
				if ($searchEntry->hasNewResults) {
					$searchEntry->hasNewResults = false;
					$searchEntry->update();
				}
			}
			$userForSearch = null;
		}
		$numProcessed++;
		if ($numProcessed % 100 == 0) {
			$searchUpdateLogEntry->update();
		}
		$searchEntry->__destruct();
		$searchEntry = null;
	}
}
$searchUpdateLogEntry->update();

$searchUpdateLogEntry->addNote("Finished updating saved searches");
$searchUpdateLogEntry->endTime = time();
$searchUpdateLogEntry->update();

$cronLogEntry->notes .= "<br/>Imported a total of " . $searchUpdateLogEntry->numUpdated. " searches";
$cronLogEntry->endTime = time();
$cronLogEntry->update();

$search->__destruct();
$search = null;

global $aspen_db;
$aspen_db = null;

die();