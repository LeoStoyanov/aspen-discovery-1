<?php
/** @noinspection SqlDialectInspection */

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

fwrite($file, "isbn\titemid\tbibrecordcallno\tbibrecordid\ttitle\n");

echo "Starting NoveList export using optimized Solr approach...\n";
flush(); // Occasionally flush for echo output.

try {
    echo "Querying Solr directly for all records with ISBNs...\n";
    flush();

    $solrUrl = $configArray['Index']['url'] . '/grouped_works_v2/select';
    $solrChunkSize = 5000;
    $solrBaseParams = [
        'q' => '*:*',
        'fl' => 'id,primary_isbn,isbn',
        'rows' => $solrChunkSize,
        'wt' => 'json',
        'sort' => 'id asc',
    ];

    $initialSolrUrl = $solrUrl . '?' . http_build_query($solrBaseParams + ['cursorMark' => '*']);
    // Use Solr cursorMark paging to stream results without materializing the full result set in PHP.
    echo "Solr URL (cursor-based): " . $initialSolrUrl . "\n";
    flush();

    $cursorMark = '*';
    $totalRecordsChecked = 0;
    $recordsWithIsbns = 0;
    $totalSolrRecords = 0;
    $chunksProcessed = 0;
    $totalMatchesInIndex = null;
    $uniqueWorksWithIsbns = 0;
    $processedRecords = 0;
    $itemsProcessed = 0;

    do {
        $solrParams = $solrBaseParams;
        $solrParams['cursorMark'] = $cursorMark;

        $fullSolrUrl = $solrUrl . '?' . http_build_query($solrParams);
        $response = file_get_contents($fullSolrUrl);
        if ($response === false) {
            throw new Exception("Failed to query Solr at: " . $fullSolrUrl);
        }

        $solrResult = json_decode($response, true);
        if (!$solrResult || !isset($solrResult['response']['docs'])) {
            throw new Exception("Invalid Solr response or no documents returned");
        }

        if ($chunksProcessed === 0) {
            $totalMatchesInIndex = $solrResult['response']['numFound'] ?? null;
            if ($totalMatchesInIndex !== null) {
                echo "Total matches in index: " . $totalMatchesInIndex . "\n";
            }
        }

        $docs = $solrResult['response']['docs'];
        $docCount = count($docs);
        if ($docCount === 0) {
            break;
        }

        $totalSolrRecords += $docCount;
        $chunksProcessed++;

        $workIsbns = [];
        foreach ($docs as $doc) {
            $totalRecordsChecked++;
            $workId = $doc['id'] ?? '';
            if ($workId === '') {
                continue;
            }

            $isbns = [];

            if (!empty($doc['primary_isbn'])) {
                $isbns[] = $doc['primary_isbn'];
            }

            if (!empty($doc['isbn'])) {
                if (is_array($doc['isbn'])) {
                    $isbns = array_merge($isbns, $doc['isbn']);
                } else {
                    $isbns[] = $doc['isbn'];
                }
            }

            $isbns = array_unique($isbns);
            if (!empty($isbns)) {
                $workIsbns[$workId] = $isbns;
                $recordsWithIsbns++;
            }

            if ($totalRecordsChecked % 50000 == 0) {
                echo "Checked $totalRecordsChecked records, found $recordsWithIsbns with ISBNs...\n";
                flush();
            }
        }

        $uniqueWorksWithIsbns += count($workIsbns);

        if (!empty($workIsbns)) {
            $placeholders = implode(',', array_fill(0, count($workIsbns), '?'));
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
                    AND gwr.recordIdentifier IS NOT NULL
                INNER JOIN grouped_work_record_items gwri ON gwr.id = gwri.groupedWorkRecordId
                    AND gwri.itemId IS NOT NULL
                    AND gwri.itemId != ''
                LEFT JOIN indexed_call_number cn ON gwri.callNumberId = cn.id
                WHERE gw.permanent_id IN ($placeholders)
            ";

            $chunkStmt = $aspen_db->prepare($query);
            $chunkStmt->execute(array_keys($workIsbns));

            while ($row = $chunkStmt->fetch(PDO::FETCH_ASSOC)) {
                $itemsProcessed++;

                $workId = $row['permanent_id'];
                if (!isset($workIsbns[$workId])) {
                    continue;
                }

                $itemId = $row['itemId'];
                $callNumber = $row['callNumber'] ?: '';
                $bibRecordId = $row['recordIdentifier'];
                $title = $row['full_title'];

                foreach ($workIsbns[$workId] as $isbn) {
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
                            $title,
                        ]) . "\n";

                        fwrite($file, $line);
                        $processedRecords++;
                    }
                }

                if ($itemsProcessed % 50000 == 0) {
                    echo "Processed $itemsProcessed items, wrote $processedRecords ISBN records...\n";
                    flush();
                }
            }
			// Explicitly free MySQL cursor before the next Solr batch so the prepared statement can reuse server resources.
            $chunkStmt->closeCursor();
        }

        $nextCursorMark = $solrResult['nextCursorMark'] ?? null;
        if ($nextCursorMark === null || $nextCursorMark === $cursorMark) {
            break;
        }

        $cursorMark = $nextCursorMark;
        unset($workIsbns);
    } while (true);

    fclose($file);

    echo "Retrieved " . $totalSolrRecords . " total records from Solr\n";
    if ($totalMatchesInIndex !== null) {
        echo "Total matches in index: " . $totalMatchesInIndex . "\n";
    }
    echo "Checked $totalRecordsChecked total records, found $recordsWithIsbns with ISBNs\n";
    echo "Processed $chunksProcessed Solr chunks\n";
    echo "NoveList export completed successfully!\n";
    echo "File: $filepath\n";
    echo "Total items processed: $itemsProcessed\n";
    echo "ISBN records exported: $processedRecords\n";
    echo "Unique works with ISBNs: " . $uniqueWorksWithIsbns . "\n";

} catch (Exception $e) {
    if (isset($file) && $file) {
        fclose($file);
    }
    echo "Error: " . $e->getMessage() . "\n";
}

/**
 * Convert 10-digit ISBN to 13-digit ISBN.
 *
 * @param string $isbn10 The 10-digit ISBN.
 * @return string The 13-digit ISBN.
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
