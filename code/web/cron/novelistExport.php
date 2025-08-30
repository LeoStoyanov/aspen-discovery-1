<?php

require_once __DIR__ . '/../bootstrap.php';
require_once __DIR__ . '/../bootstrap_aspen.php';

require_once ROOT_DIR . '/sys/CronLogEntry.php';
require_once ROOT_DIR . '/sys/SearchObject/SearchObjectFactory.php';

set_time_limit(0);
ini_set('memory_limit', '2G');
ini_set('output_buffering', 'off');
if (ob_get_level()) ob_end_flush();

global $configArray;
global $aspen_db;
global $serverName;

$serverName = preg_replace('/[^a-zA-Z0-9_-]/', '_', $serverName);
$timestamp = date('Y-m-d_H-i-s');
$filename = $serverName . '_novelist_export_' . $timestamp . '.csv';
$filepath = '/tmp/' . $filename;
$file = fopen($filepath, 'w');
if (!$file) {;
    die("Error: Could not create export file\n");
}

// Write header
fwrite($file, "isbn\titemid\tbibrecordcallno\tbibrecordid\ttitle\n");

echo "Starting NoveList export using optimized Solr approach...\n";
flush();

try {
    echo "Querying Solr directly for all records with ISBNs...\n";
    flush();

    $solrUrl = $configArray['Index']['url'] . '/grouped_works_v2/select';
	$solrParams = [
		'q' => '*:*',
		'fl' => 'id,primary_isbn,isbn',
		'rows' => 9999999, // Get all records by using very large number.
		'wt' => 'json'
	];

	$fullSolrUrl = $solrUrl . '?' . http_build_query($solrParams);
    echo "Solr URL: " . $fullSolrUrl . "\n";
    flush();

    $response = file_get_contents($fullSolrUrl);
    if ($response === false) {
        throw new Exception("Failed to query Solr at: " . $fullSolrUrl);
    }
    
    $solrResult = json_decode($response, true);
    if (!$solrResult || !isset($solrResult['response']['docs'])) {
        throw new Exception("Invalid Solr response or no documents returned");
    }
    
    $solrRecords = $solrResult['response']['docs'];
    $totalSolrRecords = count($solrRecords);
    
    echo "Retrieved " . $totalSolrRecords . " total records from Solr\n";
    echo "Total matches in index: " . ($solrResult['response']['numFound'] ?? 'unknown') . "\n";
    flush();
    
    echo "Found $totalSolrRecords records with ISBNs in Solr\n";
    flush();
    
    // Create lookup table of grouped work IDs to ISBNs; filter for records with ISBNs.
    echo "Building ISBN lookup table from Solr results...\n";
    flush();
    
    $recordIsbns = [];
    $totalRecordsChecked = 0;
    $recordsWithIsbns = 0;
    
    foreach ($solrRecords as $doc) {
        $totalRecordsChecked++;
        $workId = $doc['id'] ?? '';
        
        $isbns = [];
        
        // Get primary ISBN.
        if (!empty($doc['primary_isbn'])) {
            $isbns[] = $doc['primary_isbn'];
        }
        
        // Get additional ISBNs.
        if (!empty($doc['isbn'])) {
            if (is_array($doc['isbn'])) {
                $isbns = array_merge($isbns, $doc['isbn']);
            } else {
                $isbns[] = $doc['isbn'];
            }
        }

        // Remove duplicates and store only if there are ISBNs.
        $isbns = array_unique($isbns);
        if (!empty($isbns)) {
            $recordIsbns[$workId] = [
                'isbns' => $isbns
            ];
            $recordsWithIsbns++;
        }

        if ($totalRecordsChecked % 50000 == 0) {
            echo "Checked $totalRecordsChecked records, found $recordsWithIsbns with ISBNs...\n";
            flush();
        }
    }
    
    echo "Checked $totalRecordsChecked total records, found $recordsWithIsbns with ISBNs\n";
    echo "Built ISBN lookup table for " . count($recordIsbns) . " works\n";
    flush();
    echo "Querying database for all items...\n";
    flush();
    
    $query = "
        SELECT DISTINCT 
            gw.permanent_id,
            gw.full_title,
            gwr.recordIdentifier,
            gwri.itemId,
            gwri.callNumberId,
            cn.callNumber
        FROM grouped_work gw
        INNER JOIN grouped_work_records gwr ON gw.id = gwr.groupedWorkId
        INNER JOIN grouped_work_record_items gwri ON gwr.id = gwri.groupedWorkRecordId
        LEFT JOIN (
            SELECT id, callNumber FROM indexed_call_number
        ) cn ON gwri.callNumberId = cn.id
        WHERE gwri.itemId IS NOT NULL 
            AND gwri.itemId != ''
            AND gwr.recordIdentifier IS NOT NULL
    ";
    
    $result = $aspen_db->prepare($query);
    $result->execute();
    
    if (!$result) {
        throw new Exception("Database query failed");
    }
    
    echo "Processing database results and writing output...\n";
    flush();
    
    $processedRecords = 0;
    $itemsProcessed = 0;
    
    while ($row = $result->fetch(PDO::FETCH_ASSOC)) {
        $workId = $row['permanent_id'];
        $itemId = $row['itemId'];
        $callNumber = $row['callNumber'] ?: '';
        $bibRecordId = $row['recordIdentifier'];
        $title = $row['full_title'];
        
        $itemsProcessed++;
        
        // Check if this work has ISBNs in the lookup table.
        if (isset($recordIsbns[$workId])) {
            $isbns = $recordIsbns[$workId]['isbns'];
            
            // Write one line per ISBN/Item ID combination.
            foreach ($isbns as $isbn) {
                // Ensure ISBN is clean (should already be from Solr, but double-check).
                $cleanISBN = preg_replace('/[^0-9X]/', '', $isbn);
                if (strlen($cleanISBN) == 10) {
                    $cleanISBN = convertISBN10to13($cleanISBN);
                }
                
                if (strlen($cleanISBN) == 13 || (strlen($cleanISBN) == 10 && str_contains($cleanISBN, 'X'))) {
                    $line = implode("\t", [
                        $cleanISBN,
                        $itemId,
                        $callNumber,
                        $bibRecordId,
                        $title
                    ]) . "\n";
                    
                    fwrite($file, $line);
                    $processedRecords++;
                }
            }
        }

        if ($itemsProcessed % 50000 == 0) {
            echo "Processed $itemsProcessed items, wrote $processedRecords ISBN records...\n";
            flush();
        }
    }
    
    fclose($file);

    echo "NoveList export completed successfully!\n";
    echo "File: $filepath\n";
    echo "Total items processed: $itemsProcessed\n";
    echo "ISBN records exported: $processedRecords\n";
    echo "Unique works with ISBNs: " . count($recordIsbns) . "\n";

} catch (Exception $e) {
    if (isset($file) && $file) {
        fclose($file);
    }
    echo "Error: " . $e->getMessage() . "\n";
}

/**
 * Convert 10-digit ISBN to 13-digit ISBN
 * @param string $isbn10 The 10-digit ISBN
 * @return string The 13-digit ISBN
 */
function convertISBN10to13(string $isbn10): string {
    if (strlen($isbn10) != 10) {
        return $isbn10;
    }
    
    // Remove check digit and add 978 prefix.
    $isbn13 = '978' . substr($isbn10, 0, 9);
    
    // Calculate check digit.
    $sum = 0;
    for ($i = 0; $i < 12; $i++) {
        $sum += ($i % 2 == 0) ? intval($isbn13[$i]) : intval($isbn13[$i]) * 3;
    }
    
    $checkDigit = (10 - ($sum % 10)) % 10;
    $isbn13 .= $checkDigit;
    
    return $isbn13;
}