<?php

require_once __DIR__ . '/../bootstrap.php';
require_once __DIR__ . '/../bootstrap_aspen.php';

require_once ROOT_DIR . '/sys/CronLogEntry.php';
require_once ROOT_DIR . '/sys/Grouping/GroupedWork.php';
require_once ROOT_DIR . '/RecordDrivers/RecordDriverFactory.php';

set_time_limit(0);
ini_set('memory_limit', '2G');

global $configArray;
global $aspen_db;
global $serverName;

// Get server name for filename
$serverName = preg_replace('/[^a-zA-Z0-9_-]/', '_', $serverName);

// Create output filename with timestamp
$timestamp = date('Y-m-d_H-i-s');
$filename = $serverName . '_novelist_export_' . $timestamp . '.csv';
$filepath = '/tmp/' . $filename;

// Open file for writing
$file = fopen($filepath, 'w');
if (!$file) {;
    die("Error: Could not create export file\n");
}

// Write header
fwrite($file, "isbn\titemid\tbibrecordcallno\tbibrecordid\ttitle\n");

$totalRecords = 0;
$processedRecords = 0;

try {
    // Query to get all records with items
    $query = "
        SELECT DISTINCT 
            gw.permanent_id,
            gw.full_title,
            gwr.recordIdentifier,
            gwri.itemId,
            gwri.callNumberId,
            gwpi.identifier as isbn,
            gwpi.type as identifier_type,
            cn.callNumber
        FROM grouped_work gw
        INNER JOIN grouped_work_records gwr ON gw.id = gwr.groupedWorkId
        INNER JOIN grouped_work_record_items gwri ON gwr.id = gwri.groupedWorkRecordId
        LEFT JOIN grouped_work_primary_identifiers gwpi ON gw.id = gwpi.grouped_work_id 
            AND gwpi.type IN ('isbn', 'issn', 'upc')
        LEFT JOIN (
            SELECT id, callNumber FROM call_number_to_grouped_work_facet
        ) cn ON gwri.callNumberId = cn.id
        WHERE gwri.itemId IS NOT NULL 
            AND gwri.itemId != ''
            AND gwr.recordIdentifier IS NOT NULL
        ORDER BY gw.id, gwr.recordIdentifier, gwri.itemId
    ";
    
    $result = $aspen_db->prepare($query);
    $result->execute();
    
    if (!$result) {
        throw new Exception("Database query failed");
    }
    
    $rows = $result->fetchAll(PDO::FETCH_ASSOC);
    $totalRecords = count($rows);
    
    $lastRecordId = '';
    $lastItemId = '';
    $recordDriver = null;
    $additionalISBNs = [];
    
    foreach ($rows as $row) {
        $recordId = $row['recordIdentifier'];
        $itemId = $row['itemId'];
        $title = $row['full_title'];
        $callNumber = $row['callNumber'] ?: '';
        $bibRecordId = $recordId;
        
        // Get ISBNs from primary identifiers table
        $isbn = '';
        if (!empty($row['isbn']) && in_array($row['identifier_type'], ['isbn', 'issn', 'upc'])) {
            $isbn = $row['isbn'];
            
            // Convert 10-digit ISBN to 13-digit if needed
            if (strlen($isbn) == 10) {
                $isbn = convertISBN10to13($isbn);
            }
        }
        
        // If we have a different record, load record driver to get additional ISBNs
        if ($recordId != $lastRecordId) {
            $recordDriver = RecordDriverFactory::initRecordDriverById($recordId);
            $additionalISBNs = [];
            
            if ($recordDriver && $recordDriver->isValid()) {
                $recordISBNs = $recordDriver->getISBNs();
                
                // For MARC records, also get cancelled ISBNs (020$z)
                if (method_exists($recordDriver, 'getCancelledIsbns')) {
                    $cancelledISBNs = $recordDriver->getCancelledIsbns();
                    $recordISBNs = array_merge($recordISBNs, $cancelledISBNs);
                }
                
                foreach ($recordISBNs as $recordISBN) {
                    // Clean ISBN (remove parenthetical info, spaces, etc.)
                    $cleanISBN = preg_replace('/\s*\([^)]*\).*$/', '', $recordISBN);
                    $cleanISBN = preg_replace('/[^0-9X]/', '', $cleanISBN);
                    
                    if (!empty($cleanISBN)) {
                        // Convert to 13-digit format if 10-digit
                        if (strlen($cleanISBN) == 10) {
                            $cleanISBN = convertISBN10to13($cleanISBN);
                        }
                        if (strlen($cleanISBN) == 13) {
                            $additionalISBNs[] = $cleanISBN;
                        }
                    }
                }
            }
            $lastRecordId = $recordId;
        }
        
        // Create output records
        $isbnsToExport = [];
        
        // Add ISBN from primary identifiers
        if (!empty($isbn)) {
            $isbnsToExport[] = $isbn;
        }
        
        // Add ISBNs from record driver
        $isbnsToExport = array_merge($isbnsToExport, $additionalISBNs);
        
        // Remove duplicates
        $isbnsToExport = array_unique($isbnsToExport);
        
        // If no ISBNs found, skip this item (per requirements)
        if (empty($isbnsToExport)) {
            continue;
        }
        
        // Write one line per ISBN/Item ID combination
        foreach ($isbnsToExport as $exportISBN) {
            $line = implode("\t", [
                $exportISBN,
                $itemId,
                $callNumber,
                $bibRecordId,
                $title
            ]) . "\n";
            
            fwrite($file, $line);
            $processedRecords++;
        }
    }
    
    fclose($file);

    echo "NoveList export completed successfully!\n";
    echo "File: $filepath\n";
    echo "Records exported: $processedRecords\n";
    
} catch (Exception $e) {
    if ($file) {
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
    
    // Remove check digit and add 978 prefix
    $isbn13 = '978' . substr($isbn10, 0, 9);
    
    // Calculate check digit
    $sum = 0;
    for ($i = 0; $i < 12; $i++) {
        $sum += ($i % 2 == 0) ? intval($isbn13[$i]) : intval($isbn13[$i]) * 3;
    }
    
    $checkDigit = (10 - ($sum % 10)) % 10;
    $isbn13 .= $checkDigit;
    
    return $isbn13;
}