<?php

require_once ROOT_DIR . '/Drivers/AbstractEContentDriver.php';

class HooplaDriver extends AbstractEContentDriver {
	const memCacheKey = 'hoopla_api_access_token';
	/** @var HooplaSetting|null */
	private $hooplaSettings = null;
	public $hooplaAPIBaseURL = 'hoopla-api-dev.hoopladigital.com';
	private $accessToken;
	private $hooplaEnabled = false;

	private $hooplaInstantEnabled = false;
	private $hooplaFlexEnabled = false;

	public function __construct() {
		require_once ROOT_DIR . '/sys/Hoopla/HooplaSetting.php';
		try {
			$hooplaSettings = new HooplaSetting();
			if ($hooplaSettings->find(true)) {
				$this->hooplaEnabled = true;
				$this->hooplaInstantEnabled = $hooplaSettings->hooplaInstantEnabled;
				$this->hooplaFlexEnabled = $hooplaSettings->hooplaFlexEnabled;

				$this->hooplaAPIBaseURL = $hooplaSettings->apiUrl;
				$this->hooplaSettings = $hooplaSettings;
				$this->getAccessToken();
			}
		} catch (Exception $e) {
			global $logger;
			$logger->log("Could not load Hoopla settings", Logger::LOG_ALERT);
		}
	}

	/**
	 * Clean an assumed Hoopla RecordID to Hoopla ID number
	 * @param $hooplaRecordId
	 * @return string
	 */
	public static function recordIDtoHooplaID($hooplaRecordId) {
		if (strpos($hooplaRecordId, ':') !== false) {
			[
				,
				$hooplaRecordId,
			] = explode(':', $hooplaRecordId, 2);
		}
		return preg_replace('/^MWT/', '', $hooplaRecordId);
	}


	// $customRequest is for curl, can be 'PUT', 'DELETE', 'POST'
	private function getAPIResponse($requestType, $url, $params = null, $customRequest = null, $additionalHeaders = null, $dataToSanitize = []) {
		global $logger;
		$logger->log('Hoopla API URL :' . $url, Logger::LOG_NOTICE);
		$ch = curl_init();
		curl_setopt($ch, CURLOPT_URL, $url);
		$headers = [
			'Accept: application/json',
			'Content-Type: application/json',
			'Authorization: Bearer ' . $this->accessToken,
			'Originating-App-Id: Aspen Discovery',
		];
		if (!empty($additionalHeaders) && is_array($additionalHeaders)) {
			$headers = array_merge($headers, $additionalHeaders);
		}
		if (empty($customRequest)) {
			$customRequest = 'GET';
			curl_setopt($ch, CURLOPT_HTTPGET, true);
		} elseif ($customRequest == 'POST') {
			curl_setopt($ch, CURLOPT_POST, true);
		} else {
			curl_setopt($ch, CURLOPT_CUSTOMREQUEST, $customRequest);
		}

		curl_setopt($ch, CURLOPT_CONNECTTIMEOUT, 1);
		curl_setopt($ch, CURLOPT_HTTPHEADER, $headers);
		curl_setopt($ch, CURLOPT_HEADER, false);
		curl_setopt($ch, CURLOPT_RETURNTRANSFER, true);
		global $instanceName;
		if (stripos($instanceName, 'localhost') !== false) {
			// For local debugging only
			curl_setopt($ch, CURLOPT_SSL_VERIFYPEER, false);
			curl_setopt($ch, CURLINFO_HEADER_OUT, true);

		}
		if ($params !== null) {
			curl_setopt($ch, CURLOPT_POSTFIELDS, json_encode($params));
		}
		$json = curl_exec($ch);

		ExternalRequestLogEntry::logRequest($requestType, $customRequest, $url, $headers, '', curl_getinfo($ch, CURLINFO_HTTP_CODE), $json, $dataToSanitize);

		if (!$json && curl_getinfo($ch, CURLINFO_HTTP_CODE) == 401) {
			$logger->log('401 Response in getAPIResponse. Attempting to renew access token', Logger::LOG_WARNING);
			$this->renewAccessToken();
			return false;
		}

		$logger->log("Hoopla API response\r\n$json", Logger::LOG_DEBUG);
		curl_close($ch);

		if ($json !== false && $json !== 'false') {
			return json_decode($json);
		} else {
			$logger->log('Curl problem in getAPIResponse', Logger::LOG_WARNING);
			return false;
		}
	}

	/**
	 * Simplified CURL call for returning a title. Success is determined by receiving a http status code of 204
	 * @param $url
	 * @return bool
	 */
	private function getAPIResponseReturnHooplaTitle($url) {
		$ch = curl_init();
		$headers = [
			'Authorization: Bearer ' . $this->accessToken,
			'Originating-App-Id: Aspen Discovery',
		];

		curl_setopt($ch, CURLOPT_URL, $url);
		curl_setopt($ch, CURLOPT_CUSTOMREQUEST, 'DELETE');
		curl_setopt($ch, CURLOPT_HTTPHEADER, $headers);
		curl_setopt($ch, CURLOPT_RETURNTRANSFER, true);
		global $instanceName;
		if (stripos($instanceName, 'localhost') !== false) {
			// For local debugging only
			curl_setopt($ch, CURLOPT_SSL_VERIFYPEER, false);
			curl_setopt($ch, CURLINFO_HEADER_OUT, true);
		}

		$response = curl_exec($ch);
		$http_code = curl_getinfo($ch, CURLINFO_HTTP_CODE);

		ExternalRequestLogEntry::logRequest('hoopla.returnCheckout', 'DELETE', $url, $headers, '', $http_code, $response, []);

		curl_close($ch);
		return $http_code == 204;
	}


	private static $hooplaLibraryIdsForUser;

	/**
	 * @param User $user
	 *
	 * @return false|int
	 */
	public function getHooplaLibraryID($user) {
		if ($this->hooplaEnabled) {
			if (isset(self::$hooplaLibraryIdsForUser[$user->id])) {
				return self::$hooplaLibraryIdsForUser[$user->id]['libraryId'];
			} else {
				$library = $user->getHomeLibrary();
				if ($library !== null) {
					$hooplaID = $library->hooplaLibraryID;
					self::$hooplaLibraryIdsForUser[$user->id]['libraryId'] = $hooplaID;
					return $hooplaID;
				} else {
					// User's home library couldn't be determined
					global $logger;
					$logger->log('Could not determine home library for user ' . $user->id . ' while getting Hoopla library ID', Logger::LOG_ERROR);
					return false;
				}
			}
		}
		return false;
	}

	/**
	 * @param User $user
	 *
	 * @return null|string
	 */
	private function getHooplaBasePatronURL($user) {
		$url = null;
		if ($this->hooplaEnabled) {
			$hooplaLibraryID = $this->getHooplaLibraryID($user);
			$barcode = $user->getBarcode();
			if (!empty($hooplaLibraryID) && !empty($barcode)) {
				$url = $this->hooplaAPIBaseURL . '/api/v1/libraries/' . $hooplaLibraryID . '/patrons/' . $barcode;
			} else {
				global $logger;
				if (empty($hooplaLibraryID)) {
					$logger->log('No Hoopla library ID found for user ' . $user->id, Logger::LOG_ERROR);
				}
				if (empty($barcode)) {
					$logger->log('No barcode found for user ' . $user->id, Logger::LOG_ERROR);
				}
			}
		}
		return $url;
	}

	private $hooplaPatronStatuses = [];

	/**
	 * @param $user User
	 *
	 * @return AccountSummary
	 */
	public function getAccountSummary(User $user): AccountSummary {
		[
			$existingId,
			$summary,
		] = $user->getCachedAccountSummary('hoopla');

		if ($summary === null || isset($_REQUEST['reload'])) {
			require_once ROOT_DIR . '/sys/User/AccountSummary.php';
			$summary = new AccountSummary();
			$summary->userId = $user->id;
			$summary->source = 'hoopla';
			$summary->resetCounters();
			$patronURL = $this->getHooplaBasePatronURL($user);
			if (!empty($patronURL)) {
				// Get Patron Status (checkouts)
				$getPatronStatusURL = $patronURL . '/status';
				$hooplaPatronStatusResponse = $this->getAPIResponse('hoopla.getAccountSummary', $getPatronStatusURL);
				if (!empty($hooplaPatronStatusResponse) && !isset($hooplaPatronStatusResponse->message)) {
					$this->hooplaPatronStatuses[$user->id] = $hooplaPatronStatusResponse;

					$summary->numCheckedOut = $hooplaPatronStatusResponse->currentlyBorrowed;
					$summary->numCheckoutsRemaining = $hooplaPatronStatusResponse->borrowsRemaining;
				} else {
					global $logger;
					$hooplaErrorMessage = empty($hooplaPatronStatusResponse->message) ? '' : ' Hoopla Message :' . $hooplaPatronStatusResponse->message;
					$logger->log('Error retrieving patron status from Hoopla. User ID : ' . $user->id . $hooplaErrorMessage, Logger::LOG_NOTICE);
					$this->hooplaPatronStatuses[$user->id] = false; // Don't do status call again for this user
				}
				// Get Holds status
				$holdsUrl = $patronURL . '/holds/current';
				$holdsResponse = $this->getAPIResponse('hoopla.getHolds', $holdsUrl);
				if (!empty($holdsResponse) && is_array($holdsResponse)) {
					$availableHolds = 0;
					$unavailableHolds = 0;

					foreach ($holdsResponse as $hold) {
						if ($hold->status === 'RESERVED') {
							$availableHolds++;
						} else if ($hold->status === 'WAITING') {
							$unavailableHolds++;
						}
					}

					$summary->numHolds = $availableHolds + $unavailableHolds;
					$summary->numAvailableHolds = $availableHolds;
					$summary->numUnavailableHolds = $unavailableHolds;
				} else {
					global $logger;
					$errorMessage = empty($holdsResponse->message) ? '' : ' Hoopla Message: ' . $holdsResponse->message;
					$logger->log('Error retrieving holds from Hoopla. User ID: ' . $user->id . $errorMessage, Logger::LOG_NOTICE);
				}

			}

			$summary->lastLoaded = time();
			if ($existingId != null) {
				$summary->id = $existingId;
				$summary->update();
			} else {
				$summary->insert();
			}
		}
		return $summary;
	}

	/**
	 * @param $patron User
	 * @return Checkout[]
	 */
	public function getCheckouts(User $patron): array {
		require_once ROOT_DIR . '/sys/User/Checkout.php';
		$checkedOutItems = [];
		if ($this->hooplaEnabled) {
			$hooplaCheckedOutTitlesURL = $this->getHooplaBasePatronURL($patron);
			if (!empty($hooplaCheckedOutTitlesURL)) {
				$hooplaCheckedOutTitlesURL .= '/checkouts/current';
				$checkOutsResponse = $this->getAPIResponse('hoopla.getCheckouts', $hooplaCheckedOutTitlesURL);
				if (is_array($checkOutsResponse)) {
					$hooplaPatronStatus = null;
					foreach ($checkOutsResponse as $checkOut) {
						$hooplaRecordID = $checkOut->contentId;
						$currentTitle = new Checkout();
						$currentTitle->type = 'hoopla';
						$currentTitle->source = 'hoopla';
						$currentTitle->userId = $patron->id;
						$currentTitle->sourceId = $checkOut->contentId;
						$currentTitle->recordId = $checkOut->contentId;
						$currentTitle->title = $checkOut->title;
						if (isset($checkOut->author)) {
							$currentTitle->author = $checkOut->author;
						}
						$currentTitle->format = $checkOut->kind;
						$currentTitle->checkoutDate = $checkOut->borrowed;
						$currentTitle->dueDate = $checkOut->due;
						$currentTitle->accessOnlineUrl = $checkOut->url;

						require_once ROOT_DIR . '/RecordDrivers/HooplaRecordDriver.php';
						$hooplaRecordDriver = new HooplaRecordDriver($hooplaRecordID);
						if ($hooplaRecordDriver->isValid()) {
							// Get Record For other details
							$currentTitle->groupedWorkId = $hooplaRecordDriver->getGroupedWorkId();
							$currentTitle->author = $hooplaRecordDriver->getPrimaryAuthor();
							$currentTitle->format = $hooplaRecordDriver->getPrimaryFormat();
						}
						$key = $currentTitle->source . $currentTitle->sourceId . $currentTitle->userId; // This matches the key naming scheme in the Overdrive Driver
						$checkedOutItems[$key] = $currentTitle;
					}
				} else {
					global $logger;
					$logger->log('Error retrieving checkouts from Hoopla.', Logger::LOG_ERROR);
				}
			}
		}
		return $checkedOutItems;
	}

	/**
	 * @return string
	 */
	private function getAccessToken() {
		if (empty($this->accessToken)) {
			/** @var Memcache $memCache */ global $memCache;
			$accessToken = $memCache->get(self::memCacheKey);
			if (empty($accessToken)) {
				$this->renewAccessToken();
			} else {
				$this->accessToken = $accessToken;
			}

		}
		return $this->accessToken;
	}

	private function renewAccessToken() {
		if ($this->hooplaEnabled) {
			$url = 'https://' . str_replace([
					'http://',
					'https://',
				], '', $this->hooplaAPIBaseURL) . '/v2/token';
			// Ensure https is used

			$username = $this->hooplaSettings->apiUsername;
			$password = $this->hooplaSettings->apiPassword;

			$curl = curl_init($url);
			curl_setopt($curl, CURLOPT_CONNECTTIMEOUT, 1);
			curl_setopt($curl, CURLOPT_HTTPAUTH, CURLAUTH_BASIC);
			curl_setopt($curl, CURLOPT_USERPWD, "$username:$password");
			curl_setopt($curl, CURLOPT_POST, true);
			curl_setopt($curl, CURLOPT_RETURNTRANSFER, true);
			curl_setopt($curl, CURLOPT_POSTFIELDS, []);

			global $instanceName;
			if (stripos($instanceName, 'localhost') !== false) {
				// For local debugging only
				curl_setopt($curl, CURLOPT_SSL_VERIFYPEER, false);
				curl_setopt($curl, CURLINFO_HEADER_OUT, true);
			}
			$response = curl_exec($curl);
			ExternalRequestLogEntry::logRequest('hoopla.renewAccessToken', 'POST', $url, [], '', curl_getinfo($curl, CURLINFO_HTTP_CODE), $response, []);

			curl_close($curl);

			if ($response) {
				$json = json_decode($response);
				if (!empty($json->access_token)) {
					$this->accessToken = $json->access_token;

					/** @var Memcache $memCache */ global $memCache;
					global $configArray;
					$memCache->set(self::memCacheKey, $this->accessToken, $configArray['Caching']['hoopla_api_access_token']);

					return true;

				} else {
					global $logger;
					$logger->log('Hoopla API retrieve access token call did not contain an access token', Logger::LOG_ERROR);
				}
			} else {
				global $logger;
				$logger->log('Curl Error in Hoopla API call to retrieve access token', Logger::LOG_ERROR);
			}
		} else {
			global $logger;
			$logger->log('Hoopla API user and/or password not set. Can not retrieve access token', Logger::LOG_ERROR);
		}
		return false;
	}

	/**
	 * Get the type (INSTANT or FLEX) of a Hoopla title
	 * @param string $titleId
	 * @return string 'INSTANT' or 'FLEX'
	 */
	private function getHooplaType($titleId) {
		require_once ROOT_DIR . '/sys/Hoopla/HooplaExtract.php';
		$hooplaItem = new HooplaExtract();
		$hooplaItem->hooplaId = $titleId;
		if ($hooplaItem->find(true)) {
			return $hooplaItem->type ?? 'INSTANT';
		}
		return 'INSTANT';
	}

	/**
	 * @param User $patron
	 * @param string $titleId
	 *
	 * @return array
	 */
	public function checkOutTitle($patron, $titleId) {
		if ($this->hooplaEnabled) {
			$checkoutURL = $this->getHooplaBasePatronURL($patron);
			if (!empty($checkoutURL)) {

				$titleId = self::recordIDtoHooplaID($titleId);
				$titleType = $this->getHooplaType($titleId);
				if ($titleType == 'FLEX' && !$this->hooplaFlexEnabled) {
					return [
						'success' => false,
						'message' => [
							'text' => 'Hoopla Flex is not enabled for this library.',
							'isPublicFacing' => true,
						],
						'api' => [
							'title' => "Unable to checkout title",
							'message' => "Hoopla Flex is not enabled for this library.",
						],
					];
				}
				$checkoutURL .= '/' . $titleId;
				$checkoutResponse = $this->getAPIResponse('hoopla.checkoutTitle', $checkoutURL, [], 'POST');

				if ($checkoutResponse) {
					if (!empty($checkoutResponse->contentId)) {
						$this->trackUserUsageOfHoopla($patron);
						$this->trackRecordCheckout($titleId);
						$patron->clearCachedAccountSummaryForSource('hoopla');
						$patron->forceReloadOfCheckouts();

						// Result for API or app use
						$apiResult = [];
						$apiResult['title'] = translate([
							'text' => 'Checked out title',
							'isPublicFacing' => true,
						]);
						$apiResult['message'] = strip_tags($checkoutResponse->message);
						$apiResult['action'] = translate([
							'text' => 'Go to Checkouts',
							'isPublicFacing' => true,
						]);

						//Prepare message for translation
						$checkoutResponseMessage = $checkoutResponse->message;
						$checkoutResponseMessage = str_replace($patron->getBarcode(), '%1%', $checkoutResponseMessage);
						return [
							'success' => true,
							'message' => translate([
								'text' => $checkoutResponseMessage,
								1 => $patron->getBarcode(),
								'isPublicFacing' => true,
							]),
							'title' => translate([
								'text' => $checkoutResponse->title,
								'isPublicFacing' => true,
							]),
							'HooplaURL' => $checkoutResponse->url,
							'due' => $checkoutResponse->due,
							'api' => $apiResult,
						];
					} else {
						// Result for API or app use
						$apiResult = [];
						$apiResult['title'] = translate([
							'text' => 'Unable to checkout title',
							'isPublicFacing' => true,
						]);

						if ($titleType == 'FLEX'){

							return [
								'success' => false,
								'noCopies' => true,
								'title' => translate([
									'text' => 'Title Not Available',
									'isPublicFacing' => true,
								]),
								'message' => translate([
									'text' => 'No copies available right now, would you like to place a hold instead?',
									'isPublicFacing' => true,
								]),
								'api' => [
									'title' => translate([
										'text' => 'Unable to checkout title',
										'isPublicFacing' => true,
									]),
									'message' => translate([
										'text' => 'Title currently unavailable, would you like to place a hold instead?',
										'isPublicFacing' => true,
									]),
									'action' => translate([
										'text' => 'Place Hold',
										'isPublicFacing' => true,
									])
								]
							];
						}

						$apiResult['message'] = isset($checkoutResponse->message) ? strip_tags($checkoutResponse->message) : 'An error occurred checking out the Hoopla title.';

						$checkoutResponseMessage = isset($checkoutResponse->message) ? strip_tags($checkoutResponse->message) : 'An error occurred checking out the Hoopla title.';
						$checkoutResponseMessage = str_replace($patron->getBarcode(), '%1%', $checkoutResponseMessage);
						return [
							'success' => false,
							'message' => translate([
								'text' => $checkoutResponseMessage,
								1 => $patron->getBarcode(),
								'isPublicFacing' => true,
							]),
							'api' => $apiResult,
						];
					}

				} else {
					// Result for API or app use
					$apiResult = [];
					$apiResult['title'] = translate([
						'text' => 'Unable to checkout title',
						'isPublicFacing' => true,
					]);
					$apiResult['message'] = translate([
						'text' => 'An error occurred checking out the Hoopla title.',
						'isPublicFacing' => true,
					]);

					return [
						'success' => false,
						'message' => 'An error occurred checking out the Hoopla title.',
						'api' => $apiResult,
					];
				}
			} elseif (!$this->getHooplaLibraryID($patron)) {
				// Result for API or app use
				$apiResult = [];
				$apiResult['title'] = translate([
					'text' => 'Unable to checkout title',
					'isPublicFacing' => true,
				]);
				$apiResult['message'] = translate([
					'text' => 'Your library does not have Hoopla integration enabled.',
					'isPublicFacing' => true,
				]);

				return [
					'success' => false,
					'message' => 'Your library does not have Hoopla integration enabled.',
					'api' => $apiResult,
				];
			} else {
				// Result for API or app use
				$apiResult = [];
				$apiResult['title'] = translate([
					'text' => 'Unable to checkout title',
					'isPublicFacing' => true,
				]);
				$apiResult['message'] = translate([
					'text' => 'There was an error retrieving your library card number.',
					'isPublicFacing' => true,
				]);

				return [
					'success' => false,
					'message' => 'There was an error retrieving your library card number.',
					'api' => $apiResult,
				];
			}
		} else {
			// Result for API or app use
			$apiResult = [];
			$apiResult['title'] = translate([
				'text' => 'Unable to checkout title',
				'isPublicFacing' => true,
			]);
			$apiResult['message'] = translate([
				'text' => 'Hoopla integration is not enabled.',
				'isPublicFacing' => true,
			]);

			return [
				'success' => false,
				'message' => 'Hoopla integration is not enabled.',
				'api' => $apiResult,
			];
		}
	}

	/**
	 * @param string $hooplaId
	 * @param User $patron
	 *
	 * @return array
	 */
	public function returnCheckout($patron, $hooplaId) {
		$apiResult = [];
		if ($this->hooplaEnabled) {
			$returnCheckoutURL = $this->getHooplaBasePatronURL($patron);
			if (!empty($returnCheckoutURL)) {
				$itemId = self::recordIDtoHooplaID($hooplaId);
				$returnCheckoutURL .= "/$itemId";
				$result = $this->getAPIResponseReturnHooplaTitle($returnCheckoutURL);
				if ($result) {
					$patron->clearCachedAccountSummaryForSource('hoopla');
					$patron->forceReloadOfCheckouts();

					// Result for API or app use
					$apiResult['title'] = translate([
						'text' => 'Title returned',
						'isPublicFacing' => true,
					]);
					$apiResult['message'] = translate([
						'text' => 'The title was successfully returned.',
						'isPublicFacing' => true,
					]);

					return [
						'success' => true,
						'message' => 'The title was successfully returned.',
						'api' => $apiResult,
					];
				} else {
					// Result for API or app use
					$apiResult['title'] = translate([
						'text' => 'Unable to return title',
						'isPublicFacing' => true,
					]);
					$apiResult['message'] = translate([
						'text' => ' There was an error returning this title.',
						'isPublicFacing' => true,
					]);

					return [
						'success' => false,
						'message' => 'There was an error returning this title.',
						'api' => $apiResult,
					];
				}

			} elseif (!$this->getHooplaLibraryID($patron)) {
				// Result for API or app use
				$apiResult['title'] = translate([
					'text' => 'Unable to return title',
					'isPublicFacing' => true,
				]);
				$apiResult['message'] = translate([
					'text' => 'Your library does not have Hoopla integration enabled.',
					'isPublicFacing' => true,
				]);

				return [
					'success' => false,
					'message' => 'Your library does not have Hoopla integration enabled.',
					'api' => $apiResult,
				];
			} else {
				// Result for API or app use
				$apiResult['title'] = translate([
					'text' => 'Unable to return title',
					'isPublicFacing' => true,
				]);
				$apiResult['message'] = translate([
					'text' => 'There was an error retrieving your library card number.',
					'isPublicFacing' => true,
				]);

				return [
					'success' => false,
					'message' => 'There was an error retrieving your library card number.',
					'api' => $apiResult,
				];
			}
		} else {
			// Result for API or app use
			$apiResult['title'] = translate([
				'text' => 'Unable to return title',
				'isPublicFacing' => true,
			]);
			$apiResult['message'] = translate([
				'text' => 'Hoopla integration is not enabled.',
				'isPublicFacing' => true,
			]);

			return [
				'success' => false,
				'message' => 'Hoopla integration is not enabled.',
				'api' => $apiResult,
			];
		}
	}

	public function hasNativeReadingHistory(): bool {
		return false;
	}

	/**
	 * @return boolean true if the driver can renew all titles in a single pass
	 */
	public function hasFastRenewAll(): bool {
		return false;
	}

	/**
	 * Renew all titles currently checked out to the user
	 *
	 * @param $patron  User
	 * @return mixed
	 */
	public function renewAll(User $patron) {
		return false;
	}

	/**
	 * Renew a single title currently checked out to the user
	 *
	 * @param $patron     User
	 * @param $recordId   string
	 * @param $itemId     string
	 * @param $itemIndex  string
	 * @return mixed
	 */
	public function renewCheckout($patron, $recordId, $itemId = null, $itemIndex = null) {
		return false;
	}

	/**
	 * Get Patron Holds
	 *
	 * This is responsible for retrieving all holds for a specific patron.
	 *
	 * @param User $patron The user to load transactions for
	 * @param bool $forSummary
	 *
	 * @return array        Array of the patron's holds
	 * @access public
	 */
	public function getHolds($patron, $forSummary = false): array {
		$holds = [
			'available' => [],
			'unavailable' => [],
		];

		if ($this->hooplaFlexEnabled) {
			$holdUrl = $this->getHooplaBasePatronURL($patron);
			if (!empty($holdUrl)) {
				$holdUrl .= '/holds/current';
				$holdResponse = $this->getAPIResponse('hoopla.getHolds', $holdUrl);
				global $logger;
				$logger->log('Hoopla holds response: ' . json_encode($holdResponse), Logger::LOG_DEBUG);

				if ($holdResponse) {
					foreach ($holdResponse as $holdInfo) {
						$this->loadHoldInfo($holdInfo, $holds, $patron, $forSummary);
					}
				}
			}
		}
		return $holds;
	}

	private function loadHoldInfo($rawHold, array &$holds, User $user, $forSummary): Hold {
		$hold = new Hold();
		$hold->type = 'hoopla';
		$hold->source = 'hoopla';

		$hold->recordId = $rawHold->contentId;
		$hold->title = $rawHold->title;
		$hold->position = $rawHold->holdsQueuePosition;
		$hold->createDate = $rawHold->inserted;;
		$hold->expirationDate = $rawHold->reserveUntil;
		$hold->cancelable = true;
		$hold->kind = $rawHold->kind;
		$hold->userId = $user->id;

		// Add Hoopla-specific info
		if (!empty($rawHold->kind)) {
			$hold->format = 'Hoopla ' . ucfirst(strtolower($rawHold->kind));
		}

		require_once ROOT_DIR . '/RecordDrivers/HooplaRecordDriver.php';
		$hooplaRecord = new HooplaRecordDriver($hold->recordId);
		if ($hooplaRecord->isValid()) {
			$hold->updateFromRecordDriver($hooplaRecord);
		}

		$hold->userId = $user->id;
		$key = $hold->source . $hold->sourceId . $hold->userId;

		// Determine if available
		$isAvailable = $rawHold->status === 'RESERVED';
		if ($isAvailable) {
			$holds['available'][$key] = $hold;
		} else {
			$holds['unavailable'][$key] = $hold;
		}

		return $hold;
	}

	/**
	 * Place Hold
	 *
	 * This is responsible for both placing holds as well as placing recalls.
	 *
	 * @param User $patron The User to place a hold for
	 * @param string $recordId The id of the bib record
	 * @param null $pickupBranch For compatibility
	 * @param null $cancelDate For compatibility
	 * @return  array                 An array with the following keys
	 *                                result - true/false
	 *                                message - the message to display (if item holds are required, this is a form to select the item).
	 *                                needsItemLevelHold - An indicator that item level holds are required
	 *                                title - the title of the record the user is placing a hold on
	 * @access  public
	 */
	function placeHold($patron, $recordId, $pickupBranch = null, $cancelDate = null) {
		$result = [
			'success' => false,
			'message' => translate(['text' => 'Unknown error', 'isPublicFacing' => true])
		];

		if ($this->hooplaEnabled) {
			// Get base URL using existing method
			$holdURL = $this->getHooplaBasePatronURL($patron);
			if (!empty($holdURL)) {
				// Convert and validate record ID
				$titleId = self::recordIDtoHooplaID($recordId);
				$titleType = $this->getHooplaType($titleId);

				// Check if FLEX is enabled
				if ($titleType == 'FLEX' && !$this->hooplaFlexEnabled) {
					return [
						'success' => false,
						'message' => translate([
							'text' => 'Hoopla Flex is not enabled for this library.',
							'isPublicFacing' => true,
						]),
						'api' => [
							'title' => "Unable to place hold",
							'message' => "Hoopla Flex is not enabled for this library.",
						],
					];
				}

				// Construct hold URL
				$holdURL .= '/holds/' . $titleId;

				// Use existing API method
				$holdResponse = $this->getAPIResponse(
					'hoopla.placeHold',  // request type
					$holdURL,            // URL
					null,                // params
					'POST',             // custom request type
					['ws-api: 2.1']     // additional headers
				);

				if ($holdResponse) {
					if (!empty($holdResponse->contentId)) {
						// Hold placed successfully
						$this->trackUserUsageOfHoopla($patron);
						$this->trackRecordHold($titleId);
						$patron->clearCachedAccountSummaryForSource('hoopla');
						$patron->forceReloadOfHolds();

						return [
							'success' => true,
							'title' => translate(['text' => 'Hold Placed Successfully', 'isPublicFacing' => true]),
							'message' => translate([
								'text' => 'Your hold for %1% was placed successfully. You are number %2% in the queue.',
								1 => $holdResponse->title,
								2 => $holdResponse->holdsQueuePosition,
								'isPublicFacing' => true
							]),
							'api' => [
								'title' => translate(['text' => 'Hold Placed Successfully', 'isPublicFacing' => true]),
								'message' => translate(['text' => 'Your hold was placed successfully', 'isPublicFacing' => true]),
								'action' => translate(['text' => 'View My Holds', 'isPublicFacing' => true])
							]
						];
					}
				}
			}
		}

		return $result;

	}

	function trackRecordHold($recordId): void {
		require_once ROOT_DIR . '/sys/Hoopla/HooplaRecordUsage.php';
		require_once ROOT_DIR . '/sys/Hoopla/HooplaExtract.php';
		$recordUsage = new HooplaRecordUsage();
		$product = new HooplaExtract();
		$product->hooplaId = $recordId;
		if ($product->find(true)) {
			global $aspenUsage;
			$recordUsage->instance = $aspenUsage->getInstance();
			$recordUsage->hooplaId = $product->hooplaId;
			$recordUsage->year = date('Y');
			$recordUsage->month = date('n');
			if ($recordUsage->find(true)) {
				$recordUsage->timesHeld++;
				$recordUsage->update();
			} else {
				$recordUsage->timesCheckedOut = 0;
				$recordUsage->timesHeld = 1;
				$recordUsage->insert();
			}
		}
	}

	/**
	 * Cancels a hold for a patron
	 *
	 * @param User $patron The User to cancel the hold for
	 * @param string $recordId The id of the bib record
	 */
	function cancelHold($patron, $recordId, $cancelId = null, $isIll = false): array {
		$result = [
			'success' => false,
			'message' => 'Unknown error canceling hold'
		];

		$patronUrl = $this->getHooplaBasePatronURL($patron);
		if (!empty($patronUrl)) {
			$cancelUrl = $patronUrl . '/holds/' . $recordId;
			$cancelResponse = $this->getAPIResponse('hoopla.cancelHold', $cancelUrl, null, 'DELETE');


			if (is_string($cancelResponse) && strpos($cancelResponse, 'Hold was not found') !== false) {
				// Hold already cancelled or doesn't exist
				$result['success'] = true;
				$result['message'] = translate([
					'text' => 'Your Hoopla hold was cancelled successfully',
					'isPublicFacing' => true,
				]);

				$patron->clearCachedAccountSummaryForSource('hoopla');
				$patron->forceReloadOfHolds();
			} else if (empty($cancelResponse)) {
				// Empty response means success (HTTP 200 with no content)
				$result['success'] = true;
				$result['message'] = translate([
					'text' => 'Your Hoopla hold was cancelled successfully',
					'isPublicFacing' => true,
				]);

				$patron->clearCachedAccountSummaryForSource('hoopla');
				$patron->forceReloadOfHolds();
			} else {
				$result['message'] = translate([
					'text' => 'Failed to cancel hold.',
					'isPublicFacing' => true,
				]);
			}
		}

		return $result;
	}

	/**
	 * @param $user
	 */
	public function trackUserUsageOfHoopla($user): void {
		require_once ROOT_DIR . '/sys/Hoopla/UserHooplaUsage.php';
		global $library;
		$userUsage = new UserHooplaUsage();

		$userHooplaTracking = $user->userCookiePreferenceLocalAnalytics;
		global $aspenUsage;
		$userUsage->instance = $aspenUsage->getInstance();
		$userUsage->userId = $user->id;
		$userUsage->year = date('Y');
		$userUsage->month = date('n');

		if ($userHooplaTracking) {
			if ($userUsage->find(true)) {
				$userUsage->usageCount++;
				$userUsage->update();
			} else {
				$userUsage->usageCount = 1;
				$userUsage->insert();
			}
		}
	}

	/**
	 * @param int $hooplaId
	 */
	public function trackRecordCheckout($hooplaId): void {
		require_once ROOT_DIR . '/sys/Hoopla/HooplaRecordUsage.php';
		$recordUsage = new HooplaRecordUsage();
		require_once ROOT_DIR . '/sys/Hoopla/HooplaExtract.php';
		$product = new HooplaExtract();
		$product->hooplaId = $hooplaId;
		if ($product->find(true)) {
			global $aspenUsage;
			$recordUsage->instance = $aspenUsage->getInstance();
			$recordUsage->hooplaId = $product->id;
			$recordUsage->year = date('Y');
			$recordUsage->month = date('n');
			if ($recordUsage->find(true)) {
				$recordUsage->timesCheckedOut++;
				$recordUsage->update();
			} else {
				$recordUsage->timesCheckedOut = 1;
				$recordUsage->insert();
			}
		}
	}
}