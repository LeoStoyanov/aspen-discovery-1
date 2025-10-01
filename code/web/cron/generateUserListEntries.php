<?php
/**
 * Generates test user list entries for test users.
 *
 * Parameters (positional)
 * 1) server name
 * 2) generation type (1 = Test users with no user lists, 2 = All test users, 3 = Specified patron)
 * 3) Number of entries to generate per user
 * 4) Clear existing lists (1 = true, 0 = false)
 * 5) Patron barcode (only required if generation type is 3)
 */

require_once __DIR__ . '/../bootstrap.php';
require_once __DIR__ . '/../bootstrap_aspen.php';

set_time_limit(0);

if ($argc < 4) {
	echo "Usage: php generateUserListEntries.php <server_name> <num_entries> <clear_existing> [patron_barcode]\n";
	echo "  num_entries: Number of entries to generate per user\n";
	echo "  clear_existing: 1 = clear existing lists, 0 = keep existing\n";
	echo "  patron_barcode: Required only if generation_type is 3\n";
	die();
}

$numEntries = (int)$argv[2];
$clearExisting = (bool)$argv[3];
$patronBarcode = '';

if ($argc > 4) {
	$patronBarcode = $argv[4];
} else {
	echo "No patron barcode was supplied.\n";
	die();
}

if ($numEntries < 1) {
	echo "Number of entries must be at least 1\n";
	die();
}

echo "Starting user list generation...\n";
//Specified user
if (empty($patronBarcode)) {
	echo "No patron barcode was supplied\n";
	die();
} else {
	$user = new User();
	$user->ils_barcode = $patronBarcode;
	if ($user->find(true)) {
		$userIdsToProcess[] = $user->id;
	} else {
		echo "Could not find user with barcode: $patronBarcode\n";
		die();
	}
}

echo "Processing " . count($userIdsToProcess) . " users\n";

// Define available sources and their weights (higher = more likely to be selected)
$sources = [
	'GroupedWork' => 40,  // Highest weight for main catalog items
	'Events' => 20,       // Medium weight for events
	'OpenArchives' => 15, // Medium weight for archives
	'Lists' => 10,        // Lower weight for other lists
];

function getRandomSource($sources): int|string {
	$totalWeight = array_sum($sources);
	$random = rand(1, $totalWeight);
	$currentWeight = 0;

	foreach ($sources as $source => $weight) {
		$currentWeight += $weight;
		if ($random <= $currentWeight) {
			return $source;
		}
	}
	return 'GroupedWork'; // Fallback
}



$numProcessed = 0;
foreach ($userIdsToProcess as $userId) {
	$user = new User();
	$user->id = $userId;
	if ($user->find(true)) {
		echo "Processing user: {$user->getDisplayName()}\n";

		if ($clearExisting) {
			// Remove existing lists for this user
			require_once ROOT_DIR . '/sys/UserLists/UserList.php';
			$existingLists = new UserList();
			$existingLists->user_id = $user->id;
			$numDeleted = 0;
			$existingLists->find();
			while ($existingLists->fetch()) {
				$listCopy = clone $existingLists;
				$listCopy->delete();
				$numDeleted++;
			}
			echo "  Removed $numDeleted existing lists\n";
		}

		// Create a new list for this user
		require_once ROOT_DIR . '/sys/UserLists/UserList.php';
		$userList = new UserList();
		$userList->user_id = $user->id;
		$userList->title = "Test List for " . $user->getDisplayName() ." LONG (BOOK)";
		$userList->description = "Automatically generated test list";
		$userList->created = time();
		$userList->dateUpdated = time();
		$userList->public = 1;
		$userList->searchable = $userList->public;
		$userList->displayListAuthor = $userList->public;
		$userList->deleted = 0;
		$userList->defaultSort = 'dateAdded';
		$userList->insert();

		echo "  Created list: {$userList->title} (ID: {$userList->id})\n";

		// Generate entries for the list using batch validation
		$entriesGenerated = 0;
		$weight = 1;

		// Pre-fetch valid IDs for each source type to avoid repeated queries
		$sourceCounts = [];
		$sourceIds = [];

		// Count how many entries we need for each source
		for ($i = 0; $i < $numEntries; $i++) {
			$source = getRandomSource($sources);
			$sourceCounts[$source] = ($sourceCounts[$source] ?? 0) + 1;
		}

		echo "  Pre-fetching valid IDs: " . json_encode($sourceCounts) . "\n";

		// Batch fetch valid IDs for each source
		foreach ($sourceCounts as $source => $count) {
			switch ($source) {
				case 'GroupedWork':
					$sourceIds[$source] = getValidGroupedWorkIds($count);
					break;
				case 'Events':
					$sourceIds[$source] = getValidEventIds($count);
					break;
				case 'OpenArchives':
					$sourceIds[$source] = getValidOpenArchiveIds($count);
					break;
				case 'Lists':
					$sourceIds[$source] = getValidListIds($count);
					break;
			}
			echo "  Got " . count($sourceIds[$source] ?? []) . " valid $source IDs\n";
		}

		// Create entries using the pre-validated IDs
		for ($i = 0; $i < $numEntries; $i++) {
			$source = getRandomSource($sources);

			if (!empty($sourceIds[$source])) {
				$sourceId = array_shift($sourceIds[$source]); // Take the first available ID
				$title = '';

				// Get title for the entry
				switch ($source) {
					case 'GroupedWork':
						try {
							require_once ROOT_DIR . '/RecordDrivers/GroupedWorkDriver.php';
							$recordDriver = new GroupedWorkDriver($sourceId);
							if ($recordDriver->isValid()) {
								$title = $recordDriver->getTitle();
							}
						} catch (Exception $e) {
							echo "    Error getting GroupedWork title: " . $e->getMessage() . "\n";
							$title = "Unknown GroupedWork";
						}
						break;

					case 'Events':
						try {
							$searchObject = SearchObjectFactory::initSearchObject('Events');
							$records = $searchObject->getRecords([$sourceId]);
							if (isset($records[$sourceId])) {
								$title = $records[$sourceId]->getTitle();
							}
						} catch (Exception $e) {
							echo "    Error getting Event title: " . $e->getMessage() . "\n";
							$title = "Unknown Event";
						}
						break;

					case 'OpenArchives':
						try {
							$searchObject = SearchObjectFactory::initSearchObject('OpenArchives');
							$records = $searchObject->getRecords([$sourceId]);
							if (isset($records[$sourceId])) {
								$title = $records[$sourceId]->getTitle();
							}
						} catch (Exception $e) {
							echo "    Error getting OpenArchive title: " . $e->getMessage() . "\n";
							$title = "Unknown Archive";
						}
						break;

					case 'Lists':
						try {
							require_once ROOT_DIR . '/sys/UserLists/UserList.php';
							$list = new UserList();
							$list->id = $sourceId;
							if ($list->find(true)) {
								$title = $list->title;
							}
						} catch (Exception $e) {
							echo "    Error getting List title: " . $e->getMessage() . "\n";
							$title = "Unknown List";
						}
						break;
				}

				// Check if this entry already exists to avoid duplicates
				require_once ROOT_DIR . '/sys/UserLists/UserListEntry.php';
				$existingEntry = new UserListEntry();
				$existingEntry->listId = $userList->id;
				$existingEntry->source = $source;
				$existingEntry->sourceId = $sourceId;
				if (!$existingEntry->find(true)) {
					$listEntry = new UserListEntry();
					$listEntry->listId = $userList->id;
					$listEntry->source = $source;
					$listEntry->sourceId = $sourceId;
					$listEntry->title = mb_substr($title ?: "Unknown Title", 0, 50);
					$listEntry->dateAdded = time() - rand(0, 86400 * 30); // Random date within last 30 days
					$listEntry->weight = $weight++;
					$listEntry->notes = '';
					$listEntry->insert();

					$entriesGenerated++;
					echo "    Added {$source}: {$sourceId} - {$title}\n";
				} else {
					echo "    Skipped duplicate {$source}: {$sourceId} - {$title}\n";
				}
			} else {
				echo "    No valid $source IDs available\n";
			}
		}

		echo "  Generated $entriesGenerated list entries\n";
		$numProcessed++;
	} else {
		echo "Could not find user $userId\n";
	}
}

echo "Processed $numProcessed users.\n";
echo "User list generation complete.\n";

function getValidGroupedWorkIds($count): array {
	$validIds = [];
	$maxAttempts = 5; // Reduce attempts since we're doing batch validation

	// First, try to get books (prioritized format)
	$booksNeeded = $count;
	$bookAttempts = min(3, $maxAttempts); // Use fewer attempts for books initially

	for ($attempt = 1; $attempt <= $bookAttempts && count($validIds) < $booksNeeded; $attempt++) {
		echo "    Attempt $attempt: Getting batch of Book format GroupedWork IDs...\n";

		try {
			// Get a larger batch to account for invalid records
			$batchSize = max(100, $booksNeeded * 2);
			$searchObject = SearchObjectFactory::initSearchObject('GroupedWork');
			$searchObject->init();
			$searchObject->addFilter('format:"Book"');
			$searchObject->setSort('random');
			$searchObject->setLimit($batchSize);
			$response = $searchObject->processSearch(false, false);

			if (isset($response['response']['docs']) && is_array($response['response']['docs'])) {
				$candidateIds = [];
				foreach ($response['response']['docs'] as $doc) {
					if (isset($doc['id'])) {
						$candidateIds[] = $doc['id'];
					}
				}

				if (!empty($candidateIds)) {
					echo "    Got " . count($candidateIds) . " candidate Book IDs, validating...\n";

					// Validate in smaller batches to avoid overwhelming Solr
					$batchesToValidate = array_chunk($candidateIds, 25);

					foreach ($batchesToValidate as $batch) {
						try {
							$validationSearchObject = SearchObjectFactory::initSearchObject();
							$records = $validationSearchObject->getRecords($batch);

							// Only add IDs that were actually returned and are valid
							foreach ($batch as $id) {
								if (isset($records[$id]) && count($validIds) < $booksNeeded) {
									require_once ROOT_DIR . '/RecordDrivers/GroupedWorkDriver.php';
									$recordDriver = new GroupedWorkDriver($id);
									if ($recordDriver->isValid()) {
										$validIds[] = $id;
									}
								}
							}
						} catch (Exception $e) {
							echo "    Error validating batch: " . $e->getMessage() . "\n";
							continue;
						}
					}
				}
			}

			echo "    Attempt $attempt completed. Valid Book IDs found: " . count($validIds) . "\n";

			if (count($validIds) >= $booksNeeded) {
				break;
			}

		} catch (Exception $e) {
			echo "    Error in attempt $attempt: " . $e->getMessage() . "\n";
		}
	}

	// If we still need more IDs, get from any format
	$remainingNeeded = $count - count($validIds);
	if ($remainingNeeded > 0) {
		echo "    Need $remainingNeeded more IDs, getting from any format...\n";

		for ($attempt = 1; $attempt <= $maxAttempts && count($validIds) < $count; $attempt++) {
			echo "    Attempt $attempt: Getting batch of any format GroupedWork IDs...\n";

			try {
				// Get a larger batch to account for invalid records
				$batchSize = max(100, $remainingNeeded * 2);
				$searchObject = SearchObjectFactory::initSearchObject('GroupedWork');
				$searchObject->init();
				$searchObject->setSort('random');
				$searchObject->setLimit($batchSize);
				$response = $searchObject->processSearch(false, false);

				if (isset($response['response']['docs']) && is_array($response['response']['docs'])) {
					$candidateIds = [];
					foreach ($response['response']['docs'] as $doc) {
						if (isset($doc['id'])) {
							$candidateIds[] = $doc['id'];
						}
					}

					if (!empty($candidateIds)) {
						echo "    Got " . count($candidateIds) . " candidate IDs, validating...\n";

						// Validate in smaller batches to avoid overwhelming Solr
						$batchesToValidate = array_chunk($candidateIds, 25);

						foreach ($batchesToValidate as $batch) {
							try {
								$validationSearchObject = SearchObjectFactory::initSearchObject('GroupedWork');
								$records = $validationSearchObject->getRecords($batch);

								// Only add IDs that were actually returned and are valid
								foreach ($batch as $id) {
									if (isset($records[$id]) && count($validIds) < $count) {
										require_once ROOT_DIR . '/RecordDrivers/GroupedWorkDriver.php';
										$recordDriver = new GroupedWorkDriver($id);
										if ($recordDriver->isValid()) {
											$validIds[] = $id;
										}
									}
								}
							} catch (Exception $e) {
								echo "    Error validating batch: " . $e->getMessage() . "\n";
								continue;
							}
						}
					}
				}

				echo "    Attempt $attempt completed. Total valid IDs found: " . count($validIds) . "\n";

				if (count($validIds) >= $count) {
					break;
				}

			} catch (Exception $e) {
				echo "    Error in attempt $attempt: " . $e->getMessage() . "\n";
			}
		}
	}

	if (count($validIds) < $count) {
		echo "    Warning: Only found " . count($validIds) . " valid IDs out of requested $count\n";
	}

	// Return exactly the number requested
	return array_slice($validIds, 0, $count);
}

function getValidEventIds($count): array {
	$validIds = [];
	$maxAttempts = 3;

	for ($attempt = 1; $attempt <= $maxAttempts && count($validIds) < $count; $attempt++) {
		echo "    Attempt $attempt: Getting Events IDs...\n";

		try {
			$batchSize = max(50, $count * 2);
			$searchObject = SearchObjectFactory::initSearchObject('Events');
			$searchObject->init();
			$searchObject->setSort('random');
			$searchObject->setLimit($batchSize);
			$response = $searchObject->processSearch(false, false);

			if (isset($response['response']['docs']) && is_array($response['response']['docs'])) {
				foreach ($response['response']['docs'] as $doc) {
					if (isset($doc['id']) && count($validIds) < $count) {
						$validIds[] = $doc['id'];
					}
				}
			}

			echo "    Attempt $attempt completed. Valid Event IDs found: " . count($validIds) . "\n";

		} catch (Exception $e) {
			echo "    Error getting Events in attempt $attempt: " . $e->getMessage() . "\n";
		}
	}

	return array_slice($validIds, 0, $count);
}

function getValidOpenArchiveIds($count): array {
	$validIds = [];
	$maxAttempts = 3;

	for ($attempt = 1; $attempt <= $maxAttempts && count($validIds) < $count; $attempt++) {
		echo "    Attempt $attempt: Getting OpenArchives IDs...\n";

		try {
			$batchSize = max(50, $count * 2);
			$searchObject = SearchObjectFactory::initSearchObject('OpenArchives');
			$searchObject->init();
			$searchObject->setSort('random');
			$searchObject->setLimit($batchSize);
			$response = $searchObject->processSearch(false, false);

			if (isset($response['response']['docs']) && is_array($response['response']['docs'])) {
				foreach ($response['response']['docs'] as $doc) {
					if (isset($doc['id']) && count($validIds) < $count) {
						$validIds[] = $doc['id'];
					}
				}
			}

			echo "    Attempt $attempt completed. Valid OpenArchives IDs found: " . count($validIds) . "\n";

		} catch (Exception $e) {
			echo "    Error getting OpenArchives in attempt $attempt: " . $e->getMessage() . "\n";
		}
	}

	return array_slice($validIds, 0, $count);
}

function getValidListIds($count): array {
	$validIds = [];

	try {
		require_once ROOT_DIR . '/sys/UserLists/UserList.php';
		$userList = new UserList();
		$userList->public = 1;
		$userList->deleted = 0;
		$userList->orderBy('RAND()');
		$userList->limit(0, $count * 2); // Get more than needed
		$userList->find();

		while ($userList->fetch() && count($validIds) < $count) {
			$validIds[] = $userList->id;
		}

		echo "    Found " . count($validIds) . " valid List IDs\n";

	} catch (Exception $e) {
		echo "    Error getting Lists: " . $e->getMessage() . "\n";
	}

	return array_slice($validIds, 0, $count);
}