<?php
require_once __DIR__ . '/../bootstrap.php';
require_once ROOT_DIR . '/sys/CronLogEntry.php';

$cronLogEntry = new CronLogEntry();
$cronLogEntry->startTime = time();
$cronLogEntry->name = 'Update Suggesters';
$cronLogEntry->insert();

global $configArray;
$solrBaseUrl = $configArray['Index']['url'];

$opts = [
	'http' => [
		'timeout' => 1200,
	],
];
$context = stream_context_create($opts);
set_time_limit(0);

$suggestEndpoint = $solrBaseUrl . '/suggest/suggest?suggest.build=true';
$cronLogEntry->notes = "Building suggester at $suggestEndpoint";
$cronLogEntry->update();

$result = @file_get_contents($suggestEndpoint, false, $context);
if ($result === false) {
	$cronLogEntry->notes .= "<br/>Could not rebuild suggester at $suggestEndpoint.";
	$cronLogEntry->numErrors++;
} else {
	$cronLogEntry->notes .= "<br/>Successfully triggered suggester rebuild at $suggestEndpoint.";
}

$cronLogEntry->endTime = time();
$cronLogEntry->update();

die();