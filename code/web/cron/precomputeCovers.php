<?php
require_once __DIR__ . '/../bootstrap.php';
require_once __DIR__ . '/../bootstrap_aspen.php';
require_once ROOT_DIR . '/sys/Covers/BookCoverProcessor.php';
require_once ROOT_DIR . '/sys/Covers/BookCoverInfo.php';
require_once ROOT_DIR . '/sys/Grouping/GroupedWork.php';
require_once ROOT_DIR . '/sys/UserLists/UserList.php';
require_once ROOT_DIR . '/sys/CourseReserves/CourseReserve.php';
require_once ROOT_DIR . '/sys/OpenArchives/OpenArchivesRecord.php';
require_once ROOT_DIR . '/RecordDrivers/GroupedWorkDriver.php';

// ALTER TABLE bookcover_info
// ADD COLUMN image_url VARCHAR(255) DEFAULT NULL AFTER imageSource,
// ADD COLUMN last_processed TIMESTAMP DEFAULT NULL AFTER image_url,
// ADD COLUMN reload_cover TINYINT(1) DEFAULT 0 AFTER last_processed;

// sudo runuser -uaspen -- php /usr/local/aspen-discovery/code/web/cron/precomputeCovers.php aspen.test

global $configArray, $serverName, $aspen_db, $logger;

$processor = new BookCoverProcessor();

// Get all records that need covers processed
$recordsToProcess = getRecordsWithCovers();
$total = count($recordsToProcess);
$current = 0;

foreach ($recordsToProcess as $record) {
	$current++;
	try {
		console_log("Processing {$current}/{$total}: {$record['type']} {$record['id']}");

		$coverInfo = new BookCoverInfo();
		$coverInfo->recordType = $record['type'];
		$coverInfo->recordId = $record['id'];

		if ($coverInfo->find(true)) {
			console_log("Record {$current}/{$total} ({$record['id']}) found.");
			//$coverInfo->reload_cover = needsReprocessing($coverInfo);
			$coverInfo->reload_cover = true;
		} else {
			$coverInfo->insert();
			console_log("Record {$current}/{$total} ({$record['id']}) inserted!");
		}

		if ($coverInfo->reload_cover) {
			console_log("Let's load in the cover!");
			$processor->processCoverForRecord($coverInfo, $record['type'], $record['id']);
		}

	} catch (Exception $e) {
		console_log("Error processing {$record['type']} {$record['id']}: " . $e->getMessage());
	}
}

$aspen_db = null;
$configArray = null;
die();

/////// END OF PROCESS ///////

function getRecordsWithCovers(): array
{
	console_log("In getRecordsWithCovers()");
	$records = [];

	// Grouped Works
//	$gw = new GroupedWork();
//	$gw->find();
//	while ($gw->fetch()) {
//		if (substr_compare($gw->permanent_id, '-eng', -4) === 0) {
//			$baseId = substr($gw->permanent_id, 0, -4);
//			$records[] = [
//				'type' => 'grouped_work',
//				'id' => $baseId
//			];
//			console_log("Processing $baseId");
//		}
//	}

	$records[] = ['type' => 'grouped_work', 'id' => '5d4439d6-0335-3615-a606-073536ecc3dd-eng'];
//
//	// Lists (UserList)
//	$lists = new UserList();
//	$lists->find();
//	while ($lists->fetch()) {
//		$records[] = ['type' => 'list', 'id' => $lists->id];
//	}
//
//	// Course Reserves
//	$cr = new CourseReserve();
//	$cr->find();
//	while ($cr->fetch()) {
//		$records[] = ['type' => 'course_reserves', 'id' => $cr->id];
//	}
//
//	// Open Archives
//	$oa = new OpenArchivesRecord();
//	$oa->find();
//	while ($oa->fetch()) {
//		$records[] = ['type' => 'open_archives', 'id' => $oa->id];
//	}

//	// Add specific provider types from BookCoverProcessor.php lines 104-146
//	$providers = [
//		'overdrive' => 'OverDrive',
//		'hoopla' => 'Hoopla',
//		'cloud_library' => 'CloudLibrary',
//		'palace_project' => 'PalaceProject',
//		'Classroom Video on Demand' => 'ClassroomVideo',
//		'films on demand' => 'FilmsOnDemand',
//		'ebrary' => 'Ebrary',
//		'zinio' => 'Zinio',
//		'ebsco_eds' => 'EbscoEds'
//	];
//
//	foreach ($providers as $type => $className) {
//		$classFile = ROOT_DIR . "/sys/{$className}.php";
//		if (file_exists($classFile)) {
//			require_once $classFile;
//			$ormClass = $className;
//			if (class_exists($ormClass)) {
//				$orm = new $ormClass();
//				$orm->find();
//				while ($orm->fetch()) {
//					$records[] = ['type' => $type, 'id' => $orm->id];
//				}
//			}
//		}
//	}

//	// Colorado State Government Documents (special case)
//	$coloradoGovDocs = new ColoradoGovDoc();
//	$coloradoGovDocs->find();
//	while ($coloradoGovDocs->fetch()) {
//		$records[] = [
//			'type' => 'Colorado State Government Documents',
//			'id' => $coloradoGovDocs->id
//		];
//	}
//
//	// ProQuest (special handling)
//	$proquestRecords = new ProQuestRecord();
//	$proquestRecords->find();
//	while ($proquestRecords->fetch()) {
//		$records[] = [
//			'type' => 'proquest',
//			'id' => $proquestRecords->id
//		];
//	}

	return $records;
}

function needsReprocessing(BookCoverInfo $coverInfo): bool {
	// Reprocess if never processed, or last processed > 30 days ago
	return $coverInfo->lastProcessed === null ||
		(time() - $coverInfo->lastProcessed) > 2592000;
}

function getSideLoadedTypes(): array {
	// Implementation from BookCoverProcessor.php line 146
	global $sideLoadSettings;
	require_once ROOT_DIR . '/sys/ILS/SideLoads.php';
	$sideLoads = new SideLoads();
	$sideLoads->find();
	while ($sideLoads->fetch()) {
		$sideLoadSettings[$sideLoads->name] = [
			'driver' => $sideLoads->driver,
			'id' => $sideLoads->id
		];
	}
	return array_keys($sideLoadSettings);
}

function getRecordsForType(string $type): array {
	$records = [];
	$class = $type . 'Record';
	$orm = new $class();
	$orm->find();
	while ($orm->fetch()) {
		$records[] = ['type' => $type, 'id' => $orm->id];
	}
	return $records;
}

function console_log($message, $prefix = ''): void
{
	$STDERR = fopen("php://stderr", "w");
	fwrite($STDERR, $prefix.$message."\n");
	fclose($STDERR);
}