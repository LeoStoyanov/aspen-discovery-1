package com.turning_leaf_technologies.hoopla;

import com.turning_leaf_technologies.config.ConfigUtil;
import com.turning_leaf_technologies.file.JarUtil;
import org.aspen_discovery.grouping.RecordGroupingProcessor;
import com.turning_leaf_technologies.indexing.IndexingUtils;
import com.turning_leaf_technologies.logging.LoggingUtil;
import com.turning_leaf_technologies.net.NetworkUtils;
import com.turning_leaf_technologies.net.WebServiceResponse;
import org.aspen_discovery.reindexer.GroupedWorkIndexer;
import com.turning_leaf_technologies.strings.AspenStringUtils;
import com.turning_leaf_technologies.util.SystemUtils;
import org.apache.logging.log4j.Logger;
import org.ini4j.Ini;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.apache.commons.lang3.StringUtils;

import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.Date;
import java.util.zip.CRC32;

public class HooplaExportMain {
	private static Logger logger;
	private static String serverName;

	private static Ini configIni;

	private static Long startTimeForLogging;
	private static HooplaExtractLogEntry logEntry;
	private static String hooplaAPIBaseURL;

	private static Connection aspenConn;
	private static PreparedStatement getAllExistingHooplaItemsStmt;
	private static PreparedStatement addHooplaTitleToDB = null;
	private static PreparedStatement updateHooplaTitleInDB = null;
	private static PreparedStatement deleteHooplaItemStmt;

	//Record grouper
	private static GroupedWorkIndexer groupedWorkIndexer;
	private static RecordGroupingProcessor recordGroupingProcessorSingleton = null;

	//Existing records
	private static HashMap<Long, HooplaTitle> existingRecords = new HashMap<>();

	//For Checksums
	private static final CRC32 checksumCalculator = new CRC32();

	//For 32 hours catch up
	private static int numRetries32HoursAfter = 0;

	public static void main(String[] args){
		boolean extractSingleWork = false;
		String singleWorkId = null;
		String singleWorkType = null;
		String hooplaType = null;
		if (args.length == 0) {
			serverName = AspenStringUtils.getInputFromCommandLine("Please enter the server name");
			if (serverName.isEmpty()) {
				System.out.println("You must provide the server name as the first argument.");
				System.exit(1);
			}
			String extractSingleWorkResponse = AspenStringUtils.getInputFromCommandLine("Process a single work? (y/N)");
			if (extractSingleWorkResponse.equalsIgnoreCase("y")) {
				extractSingleWork = true;
				String extractSingleWorkType = AspenStringUtils.getInputFromCommandLine("Enter the type of work to extract (Instant/Flex)");
				if (extractSingleWorkType.equalsIgnoreCase("Instant")) {
					singleWorkType = "Instant";
				} else if (extractSingleWorkType.equalsIgnoreCase("Flex")) {
					singleWorkType = "Flex";
				} else {
					System.out.println("Invalid work type. Please enter Instant or Flex.");
					System.exit(1);
				}

			}

		} else {
			serverName = args[0];
			if (args.length > 1){
				if (args[1].equalsIgnoreCase("singleWork") || args[1].equalsIgnoreCase("singleRecord")){
					extractSingleWork = true;
					if (args.length > 2) {
						hooplaType = args[2];
						if (hooplaType.equalsIgnoreCase("Instant")) {
							singleWorkType = "Instant";
						} else if (hooplaType.equalsIgnoreCase("Flex")) {
							singleWorkType = "Flex";
						} else {
							System.out.println("Invalid work type. Please enter Instant or Flex.");
							System.exit(1);
						}
						if (args.length > 3) {
							singleWorkId = args[3];
						}
					}
				}
			}
		}
		if (extractSingleWork && singleWorkId == null) {
			singleWorkId = AspenStringUtils.getInputFromCommandLine("Enter the id of the title to extract");
		}

		String processName = "hoopla_export";
		logger = LoggingUtil.setupLogging(serverName, processName);

		//Get the checksum of the JAR when it was started, so we can stop if it has changed.
		long myChecksumAtStart = JarUtil.getChecksumForJar(logger, processName, "./" + processName + ".jar");
		long reindexerChecksumAtStart = JarUtil.getChecksumForJar(logger, "reindexer", "../reindexer/reindexer.jar");
		long timeAtStart = new Date().getTime();

		while (true) {
			//Hoopla only needs to run once a day so just run it in cron
			Date startTime = new Date();
			startTimeForLogging = startTime.getTime() / 1000;
			logger.info(startTime + ": Starting Hoopla Export");

			// Read the base INI file to get information about the server (current directory/cron/config.ini)
			configIni = ConfigUtil.loadConfigFile("config.ini", serverName, logger);

			//Connect to the Aspen database
			aspenConn = connectToDatabase();

			//Check to see if the jar has changes before processing records, and if so quit
			if (myChecksumAtStart != JarUtil.getChecksumForJar(logger, processName, "./" + processName + ".jar")){
				IndexingUtils.markNightlyIndexNeeded(aspenConn, logger);
				disconnectDatabase(aspenConn);
				break;
			}
			if (reindexerChecksumAtStart != JarUtil.getChecksumForJar(logger, "reindexer", "../reindexer/reindexer.jar")){
				IndexingUtils.markNightlyIndexNeeded(aspenConn, logger);
				disconnectDatabase(aspenConn);
				break;
			}

			//Start a log entry
			createDbLogEntry(startTime, aspenConn);
			logEntry.addNote("Starting extract");
			logEntry.saveResults();

			//Get a list of all existing records in the database
			loadExistingTitles();

			//Do work here
			boolean updatesRun;
			if (singleWorkId == null) {
				updatesRun = exportHooplaData();
			} else {
				exportSingleHooplaTitle(singleWorkId, singleWorkType);
				updatesRun = true;
			}
			int numChanges = logEntry.getNumChanges();

			processRecordsToReload(logEntry);

			if (recordGroupingProcessorSingleton != null) {
				recordGroupingProcessorSingleton.close();
				recordGroupingProcessorSingleton = null;
			}

			if (groupedWorkIndexer != null) {
				groupedWorkIndexer.finishIndexingFromExtract(logEntry);
				groupedWorkIndexer.close();
				groupedWorkIndexer = null;
				existingRecords = null;
			}

			if (logEntry.hasErrors()) {
				logger.error("There were errors during the export!");
			}

			logger.info("Finished exporting data " + new Date());
			long endTime = new Date().getTime();
			long elapsedTime = endTime - startTime.getTime();
			logger.info("Elapsed Minutes " + (elapsedTime / 60000));

			//Mark that indexing has finished
			logEntry.setFinished();

			if (!updatesRun) {
				//delete the log entry
				try {
					PreparedStatement deleteLogEntryStmt = aspenConn.prepareStatement("DELETE from hoopla_export_log WHERE id = " + logEntry.getLogEntryId());
					deleteLogEntryStmt.executeUpdate();
				} catch (SQLException e) {
					logger.error("Could not delete log export ", e);
				}

			}

			if (extractSingleWork) {
				disconnectDatabase(aspenConn);
				break;
			}

			//Check to see if the jar has changes, and if so quit
			if (myChecksumAtStart != JarUtil.getChecksumForJar(logger, processName, "./" + processName + ".jar")){
				IndexingUtils.markNightlyIndexNeeded(aspenConn, logger);
				disconnectDatabase(aspenConn);
				break;
			}
			if (reindexerChecksumAtStart != JarUtil.getChecksumForJar(logger, "reindexer", "../reindexer/reindexer.jar")){
				IndexingUtils.markNightlyIndexNeeded(aspenConn, logger);
				disconnectDatabase(aspenConn);
				break;
			}
			//Check to see if it's between midnight and 1 am and the jar has been running more than 15 hours.  If so, restart just to clean up memory.
			GregorianCalendar nowAsCalendar = new GregorianCalendar();
			Date now = new Date();
			nowAsCalendar.setTime(now);
			if (nowAsCalendar.get(Calendar.HOUR_OF_DAY) <=1 && (now.getTime() - timeAtStart) > 15 * 60 * 60 * 1000 ){
				logger.info("Ending because we have been running for more than 15 hours and it's between midnight and one AM");
				disconnectDatabase(aspenConn);
				break;
			}
			//Check memory to see if we should close
			if (SystemUtils.hasLowMemory(configIni, logger)){
				logger.info("Ending because we have low memory available");
				disconnectDatabase(aspenConn);
				break;
			}

			disconnectDatabase(aspenConn);

			//Check to see if nightly indexing is running and if so, wait until it is done.
			if (IndexingUtils.isNightlyIndexRunning(configIni, serverName, logger)) {
				//Quit and we will restart after if finishes
				System.exit(0);
			}else {
				//Pause before running the next export (longer if we didn't get any actual changes)
				try {
					System.gc();
					if (numChanges == 0) {
						Thread.sleep(1000 * 60 * 5);
					} else {
						Thread.sleep(1000 * 60);
					}
				} catch (InterruptedException e) {
					logger.info("Thread was interrupted");
				}
			}
		}

		System.exit(0);
	}

	private static void processRecordsToReload(HooplaExtractLogEntry logEntry) {
		try {
			PreparedStatement getRecordsToReloadStmt = aspenConn.prepareStatement("SELECT * from record_identifiers_to_reload WHERE processed = 0 and type='hoopla'", ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
			PreparedStatement markRecordToReloadAsProcessedStmt = aspenConn.prepareStatement("UPDATE record_identifiers_to_reload SET processed = 1 where id = ?");
			PreparedStatement getItemDetailsForRecordStmt = aspenConn.prepareStatement(
				"SELECT UNCOMPRESS(e.rawResponse) as rawResponse, ent.hooplaType " +
				"FROM hoopla_export e " +
				"LEFT JOIN hoopla_entitlements ent ON e.hooplaId = ent.hooplaId " +
				"WHERE e.hooplaId = ?",
				ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
			ResultSet getRecordsToReloadRS = getRecordsToReloadStmt.executeQuery();
			int numRecordsToReloadProcessed = 0;
			int numInstantRecords = 0;
			int numFlexRecords = 0;
			while (getRecordsToReloadRS.next()){
				long recordToReloadId = getRecordsToReloadRS.getLong("id");
				String recordId = getRecordsToReloadRS.getString("identifier");
				long hooplaId = Long.parseLong(StringUtils.replace(recordId,"MWT", ""));
				//Regroup the record
				getItemDetailsForRecordStmt.setLong(1, hooplaId);
				ResultSet getItemDetailsForRecordRS = getItemDetailsForRecordStmt.executeQuery();
				if (getItemDetailsForRecordRS.next()){
					String rawResponse = getItemDetailsForRecordRS.getString("rawResponse");
					String hooplaType = getItemDetailsForRecordRS.getString("hooplaType");
					try {
						JSONObject itemDetails = new JSONObject(rawResponse);
						String groupedWorkId =  getRecordGroupingProcessor().groupHooplaRecord(itemDetails, hooplaId);
						//Reindex the record
						getGroupedWorkIndexer().processGroupedWork(groupedWorkId);

						if (hooplaType != null && hooplaType.equalsIgnoreCase("Flex")){
							numFlexRecords++;
						} else {
							numInstantRecords++;
						}

						markRecordToReloadAsProcessedStmt.setLong(1, recordToReloadId);
						markRecordToReloadAsProcessedStmt.executeUpdate();
						numRecordsToReloadProcessed++;
					}catch (JSONException e){
						logEntry.incErrors("Could not parse item details for record to reload " + hooplaId, e);
					}
				}else{
					//The record has likely been deleted
					logEntry.addNote("Could not get details for Hoopla record to reload " + hooplaId + " it has been deleted");
					markRecordToReloadAsProcessedStmt.setLong(1, recordToReloadId);
					markRecordToReloadAsProcessedStmt.executeUpdate();
					numRecordsToReloadProcessed++;
				}
				getItemDetailsForRecordRS.close();
			}
			if (numRecordsToReloadProcessed > 0){
				logEntry.addNote("Regrouped " + numRecordsToReloadProcessed + " records marked for reprocessing");
				logEntry.addNote("Regrouped " + numInstantRecords + " Instant records");
				logEntry.addNote("Regrouped " + numFlexRecords + " Flex records");
			}
			getRecordsToReloadRS.close();
		}catch (Exception e){
			logEntry.incErrors("Error processing records to reload ", e);
		}
	}


	private static void loadExistingTitles() {
		try {
			if (existingRecords == null) existingRecords = new HashMap<>();
			ResultSet allRecordsRS = getAllExistingHooplaItemsStmt.executeQuery();
			while (allRecordsRS.next()) {
				long hooplaId = allRecordsRS.getLong("hooplaId");
				HooplaTitle newTitle = new HooplaTitle(
						allRecordsRS.getLong("id"),
						hooplaId,
						allRecordsRS.getLong("rawChecksum"),
						allRecordsRS.getLong("rawResponseLength")
				);
				existingRecords.put(hooplaId, newTitle);
			}
			allRecordsRS.close();
			//noinspection UnusedAssignment
			allRecordsRS = null;
			getAllExistingHooplaItemsStmt.close();
			getAllExistingHooplaItemsStmt = null;
		} catch (SQLException e) {
			logger.error("Error loading existing titles", e);
			logEntry.addNote("Error loading existing titles" + e);
			System.exit(-1);
		}
	}

	private static void createDbLogEntry(Date startTime, Connection aspenConn) {
		//Remove log entries older than 45 days
		long earliestLogToKeep = (startTime.getTime() / 1000) - (60 * 60 * 24 * 45);
		try {
			int numDeletions = aspenConn.prepareStatement("DELETE from hoopla_export_log WHERE startTime < " + earliestLogToKeep).executeUpdate();
			logger.info("Deleted " + numDeletions + " old log entries");
		} catch (SQLException e) {
			logger.error("Error deleting old log entries", e);
		}

		logEntry = new HooplaExtractLogEntry(aspenConn, logger);
	}

	private static boolean exportHooplaData() {
		boolean updatesRun = false;
		try{
			PreparedStatement getSettingsStmt = aspenConn.prepareStatement("SELECT * from hoopla_settings");
			ResultSet getSettingsRS = getSettingsStmt.executeQuery();
			int numSettings = 0;

			// First, process global content once (not per library)
			boolean globalContentUpdated = false;
			hooplaIdsToReindex.clear(); // Clear any previous entitlement changes

			while (getSettingsRS.next()) {
				HooplaSettings settings = new HooplaSettings(getSettingsRS);
				numSettings++;

				// Only sync global content once, using the first settings record
				if (!globalContentUpdated) {
					globalContentUpdated = syncGlobalContent(settings);
					updatesRun |= globalContentUpdated;
				}  

				// Process library entitlements for each library configuration
				// Pass globalContentUpdated flag to determine if entitlements should run
				boolean entitlementsUpdated = syncLibraryEntitlements(settings, globalContentUpdated);
				updatesRun |= entitlementsUpdated;

				// Process Flex availability (for titles that support it)
				boolean availabilityUpdated = getFlexAvailability(settings);
				updatesRun |= availabilityUpdated;

				if (settings.isRegroupAllRecords()) {
					regroupAllRecords(aspenConn, settings.getSettingsId(), getGroupedWorkIndexer(), logEntry);
				}
			}

			// After all entitlements are processed, index the entitled content that was updated
			if (globalContentUpdated || updatesRun) {
				if (!hooplaIdsToReindex.isEmpty()) {
					indexQueuedHooplaRecords(new ArrayList<>(hooplaIdsToReindex));
					hooplaIdsToReindex.clear();
				}
			}

			if (numSettings == 0){
				logger.error("Unable to find settings for Hoopla, please add settings to the database");
			}
		}catch (Exception e){
			logEntry.incErrors("Error exporting hoopla data", e);
		}
		return updatesRun;
	}

private static final Set<Long> hooplaIdsToReindex = new HashSet<>();

	private static boolean syncGlobalContent(HooplaSettings settings) {
		boolean updatedContent = false;
		long lastUpdateOfChangedRecords = settings.getLastUpdateOfChangedRecords();
		String hooplaAPIBaseURL = settings.getApiUrl();

		String accessToken = settings.getAccessToken();
		long tokenExpirationTime = settings.getTokenExpirationTime();

		if (accessToken == null || tokenExpirationTime < (System.currentTimeMillis() / 1000)) {
			accessToken = getAccessToken(settings);
		}

		if (accessToken == null) {
			logEntry.incErrors("Could not load access token for global content sync");
			return true;
		}

        //We only want to index once a day at 1 am Local Time
        ZonedDateTime nowLocalTime = ZonedDateTime.now();
        int curHour = nowLocalTime.getHour();
        ZonedDateTime startOfToday = nowLocalTime.truncatedTo(ChronoUnit.DAYS);
        long startOfTodaySeconds = startOfToday.toEpochSecond();
        ZonedDateTime thirtyTwoHoursAgoTime = nowLocalTime.minusHours(32);
        long thirtyTwoHoursAgo = thirtyTwoHoursAgoTime.toInstant().getEpochSecond();

        if (curHour == 1){
            if (lastUpdateOfChangedRecords >= startOfTodaySeconds) {
                logger.warn("Already completed today's global content extraction at 1 AM. Skipping until tomorrow.");
                return updatedContent;
            }
            //Set last update time to 32 hours ago (go bigger to get more updates)
            if (thirtyTwoHoursAgo < lastUpdateOfChangedRecords){
                lastUpdateOfChangedRecords = thirtyTwoHoursAgo;
            }
            logEntry.addNote("Starting daily global content extraction");
        }else{
            //It's not 1 am Local time, skip for now.
            //Figure out when we last indexed this collection.
            if (lastUpdateOfChangedRecords >= thirtyTwoHoursAgo) {
                //Go ahead and index even if we are off schedule
                return updatedContent;
            }
            logEntry.addNote("Retrying global content extraction after 32 hours");
        }

        updatedContent = true;

        if (lastUpdateOfChangedRecords > 0) {
            //Give a 2-minute buffer for the extract
            lastUpdateOfChangedRecords -= 120;
            logEntry.addNote("Extracting global content since " + new Date(lastUpdateOfChangedRecords * 1000));
        }

        HashMap<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + accessToken);
        headers.put("Content-Type", "application/json");
        headers.put("Accept", "application/json");

        // Resume from last record processed if we have one, otherwise start fresh
        long lastRecordProcessed = settings.getLastRecordProcessed();
        String startToken = lastRecordProcessed > 0 ? String.valueOf(lastRecordProcessed) : null;
        if (startToken != null) {
            logEntry.addNote("Resuming global content from record " + lastRecordProcessed);
        }

        int numTries = 0;
        WebServiceResponse response;

        do {
            // Build URL with optional startTime and startToken parameters
            String url = hooplaAPIBaseURL + "/api/v1/global/content?limit=500&countryCode=" + settings.getCountryCode();
            if (lastUpdateOfChangedRecords > 0) {
                url += "&startTime=" + lastUpdateOfChangedRecords;
            }
            if (startToken != null) {
                url += "&startToken=" + startToken;
            }

            response = NetworkUtils.getURL(url, logger, headers);
            if (!response.isSuccess()) {
                if (response.getResponseCode() == 401 || response.getResponseCode() == 504 || response.getResponseCode() == 503) {
                    numTries++;
                    if (numTries >= 3) {
                        logEntry.incErrors("Could not get global content after 3 attempts from " + url + " " + response.getResponseCode() + " " + response.getMessage());
                        break;
                    } else {
                        try {
                            Thread.sleep(1000 * 60 * 2); //Wait for 2 minutes before trying again
                        } catch (InterruptedException e) {
                            logEntry.incErrors("Error sleeping for 2 minutes", e);
                        }
                        accessToken = getAccessToken(settings);
                        headers.put("Authorization", "Bearer " + accessToken);
                        continue; // Retry with new token
                    }
                } else {
                    logEntry.incErrors("Could not get global content from " + url + " " + response.getMessage() + " " + response.getResponseCode());
                    break;
                }
            }

            JSONObject responseJSON = new JSONObject(response.getMessage());
            if (responseJSON.has("contents")) {
                JSONArray responseTitles = responseJSON.getJSONArray("contents");
                if (responseTitles != null && !responseTitles.isEmpty()) {
                    updateGlobalContentMetadata(responseTitles);
                }
            }

            // Check for next page (in metadata object for global content)
            if (responseJSON.has("metadata")) {
                JSONObject metadata = responseJSON.getJSONObject("metadata");
                if (metadata.has("nextStartToken")) {
                    startToken = metadata.getString("nextStartToken");

                    // Save progress after each successful page
                    try {
                        PreparedStatement updateProgressStmt = aspenConn.prepareStatement(
                            "UPDATE hoopla_settings SET lastRecordProcessed = ? WHERE id = ?"
                        );
                        updateProgressStmt.setLong(1, Long.parseLong(startToken));
                        updateProgressStmt.setLong(2, settings.getSettingsId());
                        updateProgressStmt.executeUpdate();
                    } catch (SQLException e) {
                        logEntry.incErrors("Error updating lastRecordProcessed", e);
                    }
                } else {
                    startToken = null;
                }
            } else {
                startToken = null;
            }

            logEntry.saveResults();
        } while (startToken != null);

        logEntry.addNote("Completed global content extraction");
        logEntry.saveResults();

        try {
            //Set the extract time for changed records and reset lastRecordProcessed
            if (response.isSuccess()){
                PreparedStatement updateSettingsStmt = aspenConn.prepareStatement(
                    "UPDATE hoopla_settings SET lastUpdateOfChangedRecords = ?, lastRecordProcessed = 0 WHERE id = ?"
                );
                updateSettingsStmt.setLong(1, startTimeForLogging);
                updateSettingsStmt.setLong(2, settings.getSettingsId());
                updateSettingsStmt.executeUpdate();
            }
        } catch (SQLException e) {
            logEntry.incErrors("Error updating changed records timestamp", e);
        }
        return updatedContent;
	}

	private static List<HooplaLibraryConfiguration> getLibraryConfigurations(long settingId) {
		List<HooplaLibraryConfiguration> configurations = new ArrayList<>();
		try {
			PreparedStatement getLibraryConfigsStmt = aspenConn.prepareStatement(
				"SELECT libraryId, hooplaLibraryId, enableFlex, enableInstant, runFullEntitlementsUpdate, clearDisabledFlex, clearDisabledInstant FROM hoopla_library_settings WHERE settingId = ?"
			);
			getLibraryConfigsStmt.setLong(1, settingId);
			ResultSet libraryConfigRS = getLibraryConfigsStmt.executeQuery();
			while (libraryConfigRS.next()) {
				configurations.add(new HooplaLibraryConfiguration(libraryConfigRS));
			}
			libraryConfigRS.close();
			getLibraryConfigsStmt.close();
		} catch (SQLException e) {
			logEntry.incErrors("Error loading library configurations for setting " + settingId, e);
		}
		return configurations;
	}

	private static boolean syncLibraryEntitlements(HooplaSettings settings, boolean globalContentWasSynced) {
		boolean updatedContent = false;
		long lastUpdateOfChangedRecords = settings.getLastUpdateOfChangedRecords();
		String hooplaAPIBaseURL = settings.getApiUrl();
		List<HooplaLibraryConfiguration> libraryConfigs = getLibraryConfigurations(settings.getSettingsId());
		if (libraryConfigs.isEmpty()) {
			logEntry.addNote("No library configurations found for Hoopla setting " + settings.getSettingsId());
			return false;
		}

		String accessToken = settings.getAccessToken();
		long tokenExpirationTime = settings.getTokenExpirationTime();

		if (accessToken == null || tokenExpirationTime < (System.currentTimeMillis() / 1000)) {
			accessToken = getAccessToken(settings);
		}

		if (accessToken == null) {
			logEntry.incErrors("Could not load access token for entitlements sync");
			return true;
		}

		// Process entitlements for each library configured for this setting
		for (HooplaLibraryConfiguration libraryConfig : libraryConfigs) {
			// Check if this library should run entitlements update
			boolean shouldRunEntitlements = globalContentWasSynced || libraryConfig.isRunFullEntitlementsUpdate();

			if (!shouldRunEntitlements) {
				logEntry.addNote("Skipping entitlements update for library " + libraryConfig.getLibraryId() +
					" - global content was not synced and library does not have full entitlements update enabled");
				continue;
			}
			boolean libraryUpdatedContent = syncEntitlementsForLibrary(settings, libraryConfig, accessToken, hooplaAPIBaseURL, lastUpdateOfChangedRecords);
			if (libraryUpdatedContent) {
				updatedContent = true;
			}
		}

		return updatedContent;
	}

	private static boolean syncEntitlementsForLibrary(HooplaSettings settings, HooplaLibraryConfiguration libraryConfig, String accessToken, String hooplaAPIBaseURL, long lastUpdateOfChangedRecords) {
		boolean updatedContent = false;

		if (lastUpdateOfChangedRecords > 0 && !libraryConfig.isRunFullEntitlementsUpdate()) {
			//Give a 2-minute buffer for the extract
			lastUpdateOfChangedRecords -= 120;
			logEntry.addNote("Extracting entitlements since " + new Date(lastUpdateOfChangedRecords * 1000));
		}

		// Sync Flex entitlements if enabled or if we need to clear disabled titles
		if (libraryConfig.isFlexEnabled() || libraryConfig.isClearDisabledFlex()) {
			boolean flexUpdated = syncEntitlementsForPurchaseModel(
				settings,
				libraryConfig,
				accessToken,
				hooplaAPIBaseURL,
				lastUpdateOfChangedRecords,
				"Flex",
				libraryConfig.isFlexEnabled()
			);
			updatedContent |= flexUpdated;

			// Clear the flag if we ran for disabled Flex
			if (libraryConfig.isClearDisabledFlex() && !libraryConfig.isFlexEnabled()) {
				clearDisabledFlag(libraryConfig.getLibraryId(), "clearDisabledFlex");
			}
		}

		// Sync Instant entitlements if enabled or if we need to clear disabled titles
		if (libraryConfig.isInstantEnabled() || libraryConfig.isClearDisabledInstant()) {
			boolean instantUpdated = syncEntitlementsForPurchaseModel(
				settings,
				libraryConfig,
				accessToken,
				hooplaAPIBaseURL,
				lastUpdateOfChangedRecords,
				"Instant",
				libraryConfig.isInstantEnabled()
			);
			updatedContent |= instantUpdated;

			// Clear the flag if we ran for disabled Instant
			if (libraryConfig.isClearDisabledInstant() && !libraryConfig.isInstantEnabled()) {
				clearDisabledFlag(libraryConfig.getLibraryId(), "clearDisabledInstant");
			}
		}

		// Clear the runFullEntitlementsUpdate flag if it was set
		if (libraryConfig.isRunFullEntitlementsUpdate()) {
			clearDisabledFlag(libraryConfig.getLibraryId(), "runFullEntitlementsUpdate");
		}

		return updatedContent;
	}

	private static boolean syncEntitlementsForPurchaseModel(HooplaSettings settings, HooplaLibraryConfiguration libraryConfig, String accessToken, String hooplaAPIBaseURL, long lastUpdateOfChangedRecords, String purchaseModelName, boolean isEnabled) {
		boolean updatedContent;
		int hooplaLibraryId = libraryConfig.getHooplaLibraryId();

		HashMap<String, String> headers = new HashMap<>();
		headers.put("Authorization", "Bearer " + accessToken);
		headers.put("Content-Type", "application/json");
		headers.put("Accept", "application/json");

		String startToken = null;
		// Force full update if runFullEntitlementsUpdate flag is set, otherwise use timestamp to determine
		boolean isIncremental = (lastUpdateOfChangedRecords > 0) && !libraryConfig.isRunFullEntitlementsUpdate();
		WebServiceResponse response;

		String syncType = isIncremental ? "incremental" : "full";
		logEntry.addNote("Syncing " + purchaseModelName + " entitlements for library " + hooplaLibraryId + " (" + syncType + " sync)" + (isEnabled ? "" : " (clearing disabled)"));

		do {
			// Build URL with purchaseModel parameter and optional startTime and startToken parameters
			String url = hooplaAPIBaseURL + "/api/v1/libraries/" + hooplaLibraryId + "/entitlements?limit=500&purchaseModel=" + purchaseModelName;
			// Only include startTime for incremental updates
			if (isIncremental) {
				url += "&startTime=" + lastUpdateOfChangedRecords;
			}
			if (startToken != null) {
				url += "&startToken=" + startToken;
			}

			response = NetworkUtils.getURL(url, logger, headers);
			if (!response.isSuccess()) {
				logEntry.incErrors("Could not get " + purchaseModelName + " entitlements from " + url + " " + response.getMessage() + " " + response.getResponseCode());
				break;
			}

			JSONObject responseJSON = new JSONObject(response.getMessage());
			if (responseJSON.has("entitlements")) {
				JSONArray entitlements = responseJSON.getJSONArray("entitlements");

				// For first page, use isIncremental flag; for subsequent pages, always incremental
				boolean useIncremental = (startToken != null) || isIncremental;

				if (entitlements != null && !entitlements.isEmpty()) {
					List<Long> changedIds = updateEntitlementsInDB(entitlements, libraryConfig.getLibraryId(), useIncremental, libraryConfig);
					hooplaIdsToReindex.addAll(changedIds);
				}
			}

			// Check for next page
			if (responseJSON.has("metadata")) {
				JSONObject metadata = responseJSON.getJSONObject("metadata");
				if (metadata.has("nextStartToken")) {
					startToken = metadata.getString("nextStartToken");
				} else {
					startToken = null;
				}
			} else {
				startToken = null;
			}

			logEntry.saveResults();
		} while (startToken != null);

		logEntry.addNote("Completed " + purchaseModelName + " entitlements extraction for library " + hooplaLibraryId);
		logEntry.saveResults();

		try {
			//Set the extract time for changed records (only updates if this was successful)
			if (response.isSuccess()) {
				PreparedStatement updateSettingsStmt = aspenConn.prepareStatement("UPDATE hoopla_settings set lastUpdateOfChangedRecords = ? where id = ?");
				updateSettingsStmt.setLong(1, startTimeForLogging);
				updateSettingsStmt.setLong(2, settings.getSettingsId());
				updateSettingsStmt.executeUpdate();
			}
		} catch (SQLException e) {
			logEntry.incErrors("Error updating changed records timestamp", e);
		}
		updatedContent = true;
		return updatedContent;
	}

	private static void clearDisabledFlag(int libraryId, String flagName) {
		try {
			PreparedStatement clearFlagStmt = aspenConn.prepareStatement(
				"UPDATE hoopla_library_settings SET " + flagName + " = 0 WHERE libraryId = ?"
			);
			clearFlagStmt.setInt(1, libraryId);
			clearFlagStmt.executeUpdate();
			logEntry.addNote("Cleared " + flagName + " for library " + libraryId);
		} catch (SQLException e) {
			logEntry.incErrors("Error clearing " + flagName + " for library " + libraryId, e);
		}
	}
	private static List<Long> updateEntitlementsInDB(JSONArray entitlements, int libraryId, boolean isIncremental, HooplaLibraryConfiguration libraryConfig) {
		List<Long> changedRecords = new ArrayList<>();
		try {
			// Prepare statements
			PreparedStatement checkEntitlementStmt = aspenConn.prepareStatement(
					"SELECT id FROM hoopla_entitlements WHERE hooplaId = ? AND hooplaType = ?"
			);
			PreparedStatement insertEntitlementStmt = aspenConn.prepareStatement(
					"INSERT INTO hoopla_entitlements (hooplaId, hooplaType, dateAdded) VALUES (?, ?, NOW())",
					Statement.RETURN_GENERATED_KEYS
			);
			PreparedStatement checkScopeStmt = aspenConn.prepareStatement(
					"SELECT entitlementId FROM hoopla_entitlement_scopes WHERE entitlementId = ? AND libraryId = ?"
			);
			PreparedStatement insertScopeStmt = aspenConn.prepareStatement(
					"INSERT INTO hoopla_entitlement_scopes (entitlementId, libraryId) VALUES (?, ?)"
			);
			PreparedStatement deleteScopeStmt = aspenConn.prepareStatement(
					"DELETE FROM hoopla_entitlement_scopes WHERE entitlementId = ? AND libraryId = ?"
			);


			int activeEntitlements = 0;
			int inactiveEntitlements = 0;
			int entitlementsInserted = 0;
			int entitlementsExisting = 0;
			int scopesAdded = 0;
			int scopesRemoved = 0;

			for (int i = 0; i < entitlements.length(); i++) {
				JSONObject entitlement = entitlements.getJSONObject(i);
				if (entitlement.has("contentId") && entitlement.has("active")) {
					long contentId = entitlement.getLong("contentId");
					boolean isActive = entitlement.getBoolean("active");

					String purchaseModel = null;
					if (entitlement.has("purchaseModel")) {
						purchaseModel = entitlement.getString("purchaseModel");
					} else if (entitlement.has("type")) {
						purchaseModel = entitlement.getString("type");
					}

					String hooplaType = null;
					if (purchaseModel != null) {
						if (purchaseModel.equals("PPU")) {
							hooplaType = "Instant";
						} else if (purchaseModel.equals("EST")) {
							hooplaType = "Flex";
						}
					}
					if (hooplaType == null) {
						logEntry.incErrors("Unrecognized Hoopla purchase model for content " + contentId + ": " + purchaseModel);
						continue;
					}

					if (libraryConfig != null && !libraryConfig.isPurchaseModelEnabled(purchaseModel)) {
						isActive = false;
					}

						checkEntitlementStmt.setLong(1, contentId);
						checkEntitlementStmt.setString(2, hooplaType);
						long entitlementDbId = -1;
						try (ResultSet existingEntitlement = checkEntitlementStmt.executeQuery()) {
							if (existingEntitlement.next()) {
								entitlementDbId = existingEntitlement.getLong("id");
								entitlementsExisting++;
							} else {
								insertEntitlementStmt.setLong(1, contentId);
								insertEntitlementStmt.setString(2, hooplaType);
								insertEntitlementStmt.executeUpdate();
								entitlementsInserted++;
							try (ResultSet generatedKeys = insertEntitlementStmt.getGeneratedKeys()) {
								if (generatedKeys.next()) {
									entitlementDbId = generatedKeys.getLong(1);
								}
							}
								if (entitlementDbId == -1) {
									checkEntitlementStmt.setLong(1, contentId);
									checkEntitlementStmt.setString(2, hooplaType);
								try (ResultSet insertedEntitlement = checkEntitlementStmt.executeQuery()) {
									if (insertedEntitlement.next()) {
										entitlementDbId = insertedEntitlement.getLong("id");
									}
								}
							}
						}
					}

					if (entitlementDbId == -1) {
						logEntry.incErrors("Could not determine entitlement id for Hoopla title " + contentId);
						continue;
					}

						changedRecords.add(contentId); // Always reindex

					checkScopeStmt.setLong(1, entitlementDbId);
					checkScopeStmt.setInt(2, libraryId);
					boolean scopeExists;
					try (ResultSet existingScope = checkScopeStmt.executeQuery()) {
						scopeExists = existingScope.next();
					}

					if (isActive) {
						if (!scopeExists) {
							insertScopeStmt.setLong(1, entitlementDbId);
							insertScopeStmt.setInt(2, libraryId);
							insertScopeStmt.executeUpdate();
							scopesAdded++;
						}
						activeEntitlements++;
					} else {
						if (scopeExists) {
							deleteScopeStmt.setLong(1, entitlementDbId);
							deleteScopeStmt.setInt(2, libraryId);
							deleteScopeStmt.executeUpdate();
							scopesRemoved++;
						}
						inactiveEntitlements++;
					}
				}
			}

			try (PreparedStatement findOrphanedStmt = aspenConn.prepareStatement(
				"SELECT e.id, e.hooplaId FROM hoopla_entitlements e " +
				"LEFT JOIN hoopla_entitlement_scopes s ON e.id = s.entitlementId " +
				"WHERE s.entitlementId IS NULL"
			);
				ResultSet orphanedRS = findOrphanedStmt.executeQuery()) {
				List<Long> orphanedEntitlementIds = new ArrayList<>();
				List<Long> orphanedHooplaIds = new ArrayList<>();
				while (orphanedRS.next()) {
					orphanedEntitlementIds.add(orphanedRS.getLong("id"));
					orphanedHooplaIds.add(orphanedRS.getLong("hooplaId"));
				}
				if (!orphanedEntitlementIds.isEmpty()) {
					try (PreparedStatement deleteOrphanedStmt = aspenConn.prepareStatement(
						"DELETE FROM hoopla_entitlements WHERE id = ?"
					)) {
						for (int i = 0; i < orphanedEntitlementIds.size(); i++) {
							Long entitlementId = orphanedEntitlementIds.get(i);
							Long hooplaId = orphanedHooplaIds.get(i);
							deleteOrphanedStmt.setLong(1, entitlementId);
							deleteOrphanedStmt.executeUpdate();
							changedRecords.add(hooplaId);
						}
					}
					logEntry.addNote("Deleted " + orphanedEntitlementIds.size() + " orphaned entitlements (no scopes)");
				}
			}

			String syncType = isIncremental ? "incremental" : "full";
			logEntry.addNote("Updated entitlements for library " + libraryId + " (" + syncType + " sync): " +
						entitlements.length() + " entitlements processed (" +
						activeEntitlements + " active, " + inactiveEntitlements + " inactive), " +
						entitlementsInserted + " inserted, " + entitlementsExisting + " existing, " +
						scopesAdded + " scopes added, " + scopesRemoved + " scopes removed");
		} catch (Exception e) {
			logEntry.incErrors("Error updating entitlements in database", e);
		}
	return changedRecords;
}

	private static boolean getFlexAvailability(HooplaSettings settings) {
		// Update all the flex titles availability
		logEntry.addNote("Starting Flex availability update");
		logEntry.saveResults();
		int numUpdates = 0;
		boolean doFullReloadFlex = false; // Simplified - no longer tracking separate reload flags
		String hooplaAPIBaseURL = settings.getApiUrl();

		// Get library configurations for this setting - use the first one for API calls
		// Note: Flex availability is typically global, not per-library specific
		List<HooplaLibraryConfiguration> libraryConfigs = getLibraryConfigurations(settings.getSettingsId());
		if (libraryConfigs.isEmpty()) {
			logEntry.addNote("No library configurations found for Flex availability update");
			return false;
		}
		int hooplaLibraryId = libraryConfigs.get(0).getLibraryId(); // Use first library for API calls
		String accessToken = settings.getAccessToken();
		long tokenExpirationTime = settings.getTokenExpirationTime();

		if (accessToken == null || tokenExpirationTime < (System.currentTimeMillis() / 1000)) {
			accessToken = getAccessToken(settings);
		}

		if (accessToken == null) {
			logEntry.incErrors("Could not load access token");
			return true;
		}
	try (PreparedStatement getFlexTitlesStmt = aspenConn.prepareStatement(
		"SELECT t.id, t.hooplaId, UNCOMPRESS(t.rawResponse) as rawResponse, fa.holdsQueueSize, fa.availableCopies, fa.totalCopies, fa.status, scope.libraryId " +
		"FROM hoopla_export t " +
		"INNER JOIN hoopla_entitlements ent ON t.hooplaId = ent.hooplaId " +
		"INNER JOIN hoopla_entitlement_scopes scope ON ent.id = scope.entitlementId " +
		"LEFT JOIN hoopla_flex_availability fa ON t.hooplaId = fa.hooplaId AND scope.libraryId = fa.libraryId " +
		"WHERE ent.hooplaType = 'Flex'"
	);
		ResultSet flexTitlesRS = getFlexTitlesStmt.executeQuery();
		PreparedStatement updateFlexAvailabilityStmt = aspenConn.prepareStatement(
		"INSERT INTO hoopla_flex_availability (hooplaId, libraryId, holdsQueueSize, availableCopies, totalCopies, status) " +
		"VALUES (?, ?, ?, ?, ?, ?) " +
		"ON DUPLICATE KEY UPDATE " +
		"holdsQueueSize = VALUES(holdsQueueSize), " +
		"availableCopies = VALUES(availableCopies), " +
		"totalCopies = VALUES(totalCopies), " +
		"status = VALUES(status)"
	)) {

		while (flexTitlesRS.next()) {
			long hooplaId = flexTitlesRS.getLong("hooplaId");
			boolean existingInDB = flexTitlesRS.getString("status") != null;
			int existingHoldsQueueSize = existingInDB ? flexTitlesRS.getInt("holdsQueueSize") : 0;
			int existingAvailableCopies = existingInDB ? flexTitlesRS.getInt("availableCopies") : 0;
			int existingTotalCopies = existingInDB ? flexTitlesRS.getInt("totalCopies") : 0;
			String existingStatus = existingInDB ? flexTitlesRS.getString("status") : null;
			int scopeLibraryId = flexTitlesRS.getInt("libraryId");

				if (!doFullReloadFlex && existingInDB){
					logEntry.incNumProducts(1);
				}

				String url = hooplaAPIBaseURL + "/api/v1/libraries/" + hooplaLibraryId + "/content/info?contentIds=" + hooplaId;

				HashMap<String, String> headers = new HashMap<>();
				headers.put("Authorization", "Bearer " + accessToken);
				headers.put("Content-Type", "application/json");
				headers.put("Accept", "application/json");
				WebServiceResponse response = NetworkUtils.getURL(url, logger, headers);
				if (!response.isSuccess()){
					logEntry.incErrors("Could not get availability for title " + hooplaId + " from " + url + " " + response.getMessage());
					continue;
				}
				try {
					JSONArray availabilityArray = new JSONArray(response.getMessage());
					if (!availabilityArray.isEmpty()) {
						JSONObject titleInfo = availabilityArray.getJSONObject(0);
						long contentId = titleInfo.getLong("contentId");
						if (hooplaId != contentId) {
							logEntry.incErrors("Response content ID " + contentId + " mismatch for title " + hooplaId);
							continue;
						}
						JSONObject availability = titleInfo.getJSONObject("availability");
						if (!availability.isEmpty()) {
							String newStatus = availability.getString("status");
							int newHoldsQueueSize = newStatus.equals("BORROW") ? 0 :
							availability.has("holdsQueueSize") ? availability.getInt("holdsQueueSize") : 0;
							int newAvailableCopies = availability.getInt("availableCopies");
							int newTotalCopies = availability.getInt("totalCopies");


							boolean needsUpdate =  !existingInDB || existingHoldsQueueSize != newHoldsQueueSize || existingAvailableCopies != newAvailableCopies || existingTotalCopies != newTotalCopies || !Objects.equals(existingStatus, newStatus);

							if (needsUpdate) {
					try {
						updateFlexAvailabilityStmt.setLong(1, hooplaId);
						updateFlexAvailabilityStmt.setInt(2, scopeLibraryId);
						updateFlexAvailabilityStmt.setInt(3, newHoldsQueueSize);
						updateFlexAvailabilityStmt.setInt(4, newAvailableCopies);
						updateFlexAvailabilityStmt.setInt(5, newTotalCopies);
						updateFlexAvailabilityStmt.setString(6, newStatus);
						updateFlexAvailabilityStmt.executeUpdate();
						numUpdates++;
						logEntry.incAvailabilityChanges();
						hooplaIdsToReindex.add(hooplaId);
					} catch (SQLException e) {
						logEntry.incErrors("Error updating flex availability for title " + hooplaId, e);
					}
				}
				}
			}
				} catch (JSONException e) {
					logEntry.incErrors("Error parsing availability JSON for title " + hooplaId + ". Response: " + response.getMessage(), e);
				}
			}

			if (numUpdates > 0) {
				logEntry.addNote("Updated availability for " + numUpdates + " Flex titles");
				return true;
			} else {
				logEntry.addNote("No availability changes found for Hoopla Flex titles");
				return true;
			}

		}
		catch (Exception e) {
			logEntry.incErrors("Error getting flex availability", e);
			return false;
		} finally {
			logEntry.saveResults();
		}
	}

	private static void exportSingleHooplaTitle(String singleWorkId, String singleWorkType) {
		try{
			logEntry.addNote("Doing extract of single work " + singleWorkId);
			logEntry.saveResults();
			PreparedStatement getSettingsStmt = aspenConn.prepareStatement("SELECT * from hoopla_settings");
			ResultSet getSettingsRS = getSettingsStmt.executeQuery();
			int numSettings = 0;
			while (getSettingsRS.next()) {
				numSettings++;
				HooplaSettings settings = new HooplaSettings(getSettingsRS);
				String hooplaAPIBaseURL = settings.getApiUrl();

				// Get library configurations for this setting
				List<HooplaLibraryConfiguration> libraryConfigs = getLibraryConfigurations(settings.getSettingsId());
				if (libraryConfigs.isEmpty()) {
					logEntry.addNote("No library configurations found for settings ID " + settings.getSettingsId());
					continue;
				}
				// Use the first library for API calls
				int hooplaLibraryId = libraryConfigs.get(0).getLibraryId();

				String accessToken = getAccessToken(settings);
				if (accessToken == null) {
					logEntry.incErrors("Could not load access token");
					return;
				}
				String url = hooplaAPIBaseURL + "/api/v1/libraries/" + hooplaLibraryId + "/content";
				long numericSingleWorkId = Long.parseLong(singleWorkId);
				if (singleWorkType.equalsIgnoreCase("Flex")) {
					url += "?limit=1&startToken=" + (numericSingleWorkId - 1) + "&purchaseModel=EST";
				} else {
					url += "?limit=1&startToken=" + (numericSingleWorkId - 1) + "&purchaseModel=PPU";
				}
				HashMap<String, String> headers = new HashMap<>();
				headers.put("Authorization", "Bearer " + accessToken);
				headers.put("Content-Type", "application/json");
				headers.put("Accept", "application/json");
				WebServiceResponse response = NetworkUtils.getURL(url, logger, headers);
				if (!response.isSuccess()){
					logEntry.incErrors("Could not get titles from " + url + " " + response.getMessage());
				}else {
					JSONObject responseJSON = new JSONObject(response.getMessage());
					if (responseJSON.has("titles")) {
						JSONArray responseTitles = responseJSON.getJSONArray("titles");
						if (responseTitles != null && !responseTitles.isEmpty()) {
							List<Long> updatedIds = updateTitlesInDB(responseTitles, true, false, singleWorkType);

							// Index the updated record(s)
							if (!updatedIds.isEmpty()) {
								indexUpdatedEntitledRecords(updatedIds);
							}

							logEntry.saveResults();

							if (singleWorkType.equalsIgnoreCase("Flex")) {
								if (!responseTitles.isEmpty()) {
									JSONObject titleObj = responseTitles.getJSONObject(0);
									boolean isActive = titleObj.getBoolean("active");
									if (!isActive) {
										logEntry.addNote("Skipping availability check for inactive Flex title: " + numericSingleWorkId);
									} else {
										String availUrl = hooplaAPIBaseURL + "/api/v1/libraries/" + hooplaLibraryId + "/content/info?contentIds=" + numericSingleWorkId;
										WebServiceResponse availResponse = NetworkUtils.getURL(availUrl, logger, headers);
										if (!availResponse.isSuccess()) {
											logEntry.incErrors("Could not get availability for Flex title " + numericSingleWorkId + " from " + availUrl + " " + availResponse.getMessage());
										} else {
											try {
												JSONArray availabilityArray = new JSONArray(availResponse.getMessage());
												if (!availabilityArray.isEmpty()) {
													JSONObject titleInfo = availabilityArray.getJSONObject(0);
													JSONObject availability = titleInfo.getJSONObject("availability");

													// Direct update without comparing old values
													PreparedStatement updateFlexAvailabilityStmt = aspenConn.prepareStatement(
														"INSERT INTO hoopla_flex_availability (hooplaId, holdsQueueSize, " +
														"availableCopies, totalCopies, status) " +
														"VALUES (?, ?, ?, ?, ?) " +
														"ON DUPLICATE KEY UPDATE " +
														"holdsQueueSize = VALUES(holdsQueueSize), " +
														"availableCopies = VALUES(availableCopies), " +
														"totalCopies = VALUES(totalCopies), " +
														"status = VALUES(status)"
													);
													int holdsQueueSize = availability.has("holdsQueueSize") ? availability.getInt("holdsQueueSize") : 0;

													updateFlexAvailabilityStmt.setLong(1, numericSingleWorkId);
													updateFlexAvailabilityStmt.setInt(2, holdsQueueSize);
													updateFlexAvailabilityStmt.setInt(3, availability.getInt("availableCopies"));
													updateFlexAvailabilityStmt.setInt(4, availability.getInt("totalCopies"));
													updateFlexAvailabilityStmt.setString(5, availability.getString("status"));
													updateFlexAvailabilityStmt.executeUpdate();

													logEntry.addNote("Updated availability for Flex title " + numericSingleWorkId);
													logEntry.incAvailabilityChanges();
												}
											} catch (Exception e) {
												logEntry.incErrors("Error updating Flex availability for title " +
													numericSingleWorkId, e);
											}
										}
									}
								}
							}
						}
					}
				}
			}
			if (numSettings == 0){
				logger.error("Unable to find settings for Hoopla, please add settings to the database");
			}
		}catch (Exception e){
			logEntry.incErrors("Error exporting hoopla data", e);
		}
	}

	private static void updateGlobalContentMetadata(JSONArray contentItems) {
		logEntry.incNumProducts(contentItems.length());

		for (int i = 0; i < contentItems.length(); i++) {
			try {
				JSONObject content = contentItems.getJSONObject(i);

				String rawResponse = content.toString();
				checksumCalculator.reset();
				checksumCalculator.update(rawResponse.getBytes());
				long rawChecksum = checksumCalculator.getValue();

				long hooplaId = content.getLong("id");

				// Extract price from ppuPrices array (or 0.0 for Flex titles)
				// Since we pass countryCode to API, response only contains data for that country
				double price = 0.0;
				if (content.has("ppuPrices")) {
					JSONArray ppuPrices = content.getJSONArray("ppuPrices");
					if (!ppuPrices.isEmpty()) {
						price = ppuPrices.getJSONObject(0).getDouble("ppuPrice");
					}
				}

				HooplaTitle existingTitle = existingRecords.get(hooplaId);
				boolean recordUpdated = false;

				if (existingTitle != null) {
					// Record exists - check if metadata changed
					if ((existingTitle.getChecksum() != rawChecksum) || (existingTitle.getRawResponseLength() != rawResponse.length())) {
						recordUpdated = true;
						logEntry.incUpdated();
					}
					existingTitle.setFoundInExport(true);
				} else {
					// New record
					recordUpdated = true;
					logEntry.incAdded();
				}

				if (recordUpdated) {
					// Extract rating (or empty)
					// Since we pass countryCode to API, response only contains data for that country
					String rating = "";
					if (content.has("ratings")) {
						JSONArray ratings = content.getJSONArray("ratings");
						if (!ratings.isEmpty()) {
							rating = ratings.getJSONObject(0).getString("ratingValue");
						}
					}

					if (existingTitle == null) {
						// INSERT new record
						addHooplaTitleToDB.setLong(1, hooplaId);
						addHooplaTitleToDB.setString(2, content.getString("title"));
						addHooplaTitleToDB.setString(3, content.optString("format", ""));
						addHooplaTitleToDB.setBoolean(4, content.getBoolean("isParentalAdvisory"));
						addHooplaTitleToDB.setBoolean(5, content.getBoolean("isDemo"));
						addHooplaTitleToDB.setBoolean(6, content.getBoolean("containsProfanity"));
						addHooplaTitleToDB.setString(7, rating);
						addHooplaTitleToDB.setBoolean(8, content.getBoolean("isAbridged"));
						addHooplaTitleToDB.setBoolean(9, content.getBoolean("isForChildren"));
						addHooplaTitleToDB.setDouble(10, price);
						addHooplaTitleToDB.setLong(11, rawChecksum);
						addHooplaTitleToDB.setString(12, rawResponse);
						addHooplaTitleToDB.setLong(13, startTimeForLogging);

						try {
							addHooplaTitleToDB.executeUpdate();
						} catch (DataTruncation e) {
							logEntry.addNote("Record " + hooplaId + " " + content.getString("title") + " contained invalid data " + e);
						} catch (SQLException e) {
							logEntry.incErrors("Error adding hoopla title to database record " + hooplaId + " " + content.getString("title"), e);
						}
					} else {
						// UPDATE existing record
						updateHooplaTitleInDB.setString(1, content.getString("title"));
						updateHooplaTitleInDB.setString(2, content.optString("format", ""));
						updateHooplaTitleInDB.setBoolean(3, content.getBoolean("isParentalAdvisory"));
						updateHooplaTitleInDB.setBoolean(4, content.getBoolean("isDemo"));
						updateHooplaTitleInDB.setBoolean(5, content.getBoolean("containsProfanity"));
						updateHooplaTitleInDB.setString(6, rating);
						updateHooplaTitleInDB.setBoolean(7, content.getBoolean("isAbridged"));
						updateHooplaTitleInDB.setBoolean(8, content.getBoolean("isForChildren"));
						updateHooplaTitleInDB.setDouble(9, price);
						updateHooplaTitleInDB.setLong(10, rawChecksum);
						updateHooplaTitleInDB.setString(11, rawResponse);
						updateHooplaTitleInDB.setLong(12, existingTitle.getId());

						try {
							updateHooplaTitleInDB.executeUpdate();
						} catch (DataTruncation e) {
							logEntry.addNote("Record " + hooplaId + " " + content.getString("title") + " contained invalid data " + e);
						} catch (SQLException e) {
							logEntry.incErrors("Error updating hoopla data in database for record " + hooplaId + " " + content.getString("title"), e);
						}
					}
				}
			} catch (Exception e) {
				logEntry.incErrors("Error processing global content item", e);
			}
		}
	}

	// Legacy method for single work extraction - simplified to just update metadata
	// Active status and hooplaType are now managed via entitlements
	private static List<Long> updateTitlesInDB(JSONArray responseTitles, boolean forceRegrouping, boolean doFullReload, String hooplaType) {
		List<Long> updatedRecordIds = new ArrayList<>();
		logEntry.incNumProducts(responseTitles.length());

		for (int i = 0; i < responseTitles.length(); i++){
			try {
				JSONObject curTitle = responseTitles.getJSONObject(i);

				String rawResponse = curTitle.toString();
				checksumCalculator.reset();
				checksumCalculator.update(rawResponse.getBytes());
				long rawChecksum = checksumCalculator.getValue();

				long hooplaId = curTitle.getLong("id");

				// Extract price (0.0 for Flex)
				double price = 0.0;
				if (hooplaType != null && hooplaType.equalsIgnoreCase("Flex")) {
					price = 0.0;
				} else {
					price = curTitle.optDouble("price", 0.0);
				}

				HooplaTitle existingTitle = existingRecords.get(hooplaId);
				boolean recordUpdated = false;

				if (existingTitle != null) {
					if ((existingTitle.getChecksum() != rawChecksum) || (existingTitle.getRawResponseLength() != rawResponse.length())){
						recordUpdated = true;
						logEntry.incUpdated();
					}
					existingTitle.setFoundInExport(true);
				} else {
					recordUpdated = true;
					logEntry.incAdded();
				}

				if (recordUpdated || doFullReload || forceRegrouping) {
					// Extract rating
					String rating = "";
					if (curTitle.has("rating")) {
						rating = curTitle.getString("rating");
					}

					if (existingTitle == null){
						// INSERT
						addHooplaTitleToDB.setLong(1, hooplaId);
						addHooplaTitleToDB.setString(2, curTitle.getString("title"));
						addHooplaTitleToDB.setString(3, curTitle.optString("kind", ""));
						addHooplaTitleToDB.setBoolean(4, curTitle.optBoolean("pa", false));
						addHooplaTitleToDB.setBoolean(5, curTitle.optBoolean("demo", false));
						addHooplaTitleToDB.setBoolean(6, curTitle.optBoolean("profanity", false));
						addHooplaTitleToDB.setString(7, rating);
						addHooplaTitleToDB.setBoolean(8, curTitle.optBoolean("abridged", false));
						addHooplaTitleToDB.setBoolean(9, curTitle.optBoolean("children", false));
						addHooplaTitleToDB.setDouble(10, price);
						addHooplaTitleToDB.setLong(11, rawChecksum);
						addHooplaTitleToDB.setString(12, rawResponse);
						addHooplaTitleToDB.setLong(13, startTimeForLogging);

						try {
							addHooplaTitleToDB.executeUpdate();
							updatedRecordIds.add(hooplaId);
						}catch (DataTruncation e) {
							logEntry.addNote("Record " + hooplaId + " " + curTitle.getString("title") + " contained invalid data " + e);
						}catch (SQLException e){
							logEntry.incErrors("Error adding hoopla title to database record " + hooplaId + " " + curTitle.getString("title"), e);
						}
					} else {
						// UPDATE
						updateHooplaTitleInDB.setString(1, curTitle.getString("title"));
						updateHooplaTitleInDB.setString(2, curTitle.optString("kind", ""));
						updateHooplaTitleInDB.setBoolean(3, curTitle.optBoolean("pa", false));
						updateHooplaTitleInDB.setBoolean(4, curTitle.optBoolean("demo", false));
						updateHooplaTitleInDB.setBoolean(5, curTitle.optBoolean("profanity", false));
						updateHooplaTitleInDB.setString(6, rating);
						updateHooplaTitleInDB.setBoolean(7, curTitle.optBoolean("abridged", false));
						updateHooplaTitleInDB.setBoolean(8, curTitle.optBoolean("children", false));
						updateHooplaTitleInDB.setDouble(9, price);
						updateHooplaTitleInDB.setLong(10, rawChecksum);
						updateHooplaTitleInDB.setString(11, rawResponse);
						updateHooplaTitleInDB.setLong(12, existingTitle.getId());

						try {
							updateHooplaTitleInDB.executeUpdate();
							updatedRecordIds.add(hooplaId);
						}catch (DataTruncation e) {
							logEntry.addNote("Record " + hooplaId + " " + curTitle.getString("title") + " contained invalid data " + e);
						}catch (SQLException e){
							logEntry.incErrors("Error updating hoopla data in database for record " + hooplaId + " " + curTitle.getString("title"), e);
						}
					}
				}
		}catch (Exception e){
			logEntry.incErrors("Error updating hoopla data for single work extraction", e);
		}
		}

		return updatedRecordIds;
	}

	private static void indexQueuedHooplaRecords(List<Long> hooplaIds) {
		if (hooplaIds.isEmpty()) {
			return;
		}

		try (PreparedStatement findOrphanedStmt = aspenConn.prepareStatement(
			"SELECT e.id, e.hooplaId FROM hoopla_entitlements e " +
			"LEFT JOIN hoopla_entitlement_scopes s ON e.id = s.entitlementId " +
			"WHERE s.entitlementId IS NULL"
		);
			ResultSet orphanedRS = findOrphanedStmt.executeQuery()) {
			List<Long> orphanedEntitlementIds = new ArrayList<>();
			List<Long> orphanedHooplaIds = new ArrayList<>();
			while (orphanedRS.next()) {
				orphanedEntitlementIds.add(orphanedRS.getLong("id"));
				orphanedHooplaIds.add(orphanedRS.getLong("hooplaId"));
			}
			if (!orphanedEntitlementIds.isEmpty()) {
				try (PreparedStatement deleteOrphanedStmt = aspenConn.prepareStatement(
					"DELETE FROM hoopla_entitlements WHERE id = ?"
				)) {
					for (int i = 0; i < orphanedEntitlementIds.size(); i++) {
						Long entitlementId = orphanedEntitlementIds.get(i);
						Long hooplaId = orphanedHooplaIds.get(i);
						deleteOrphanedStmt.setLong(1, entitlementId);
						deleteOrphanedStmt.executeUpdate();
						hooplaIds.add(hooplaId);
					}
				}
				logEntry.addNote("Deleted " + orphanedEntitlementIds.size() + " orphaned entitlements (no scopes)");
			}
		} catch (SQLException e) {
			logEntry.incErrors("Error cleaning up orphaned Hoopla entitlements", e);
		}

		if (hooplaIds.isEmpty()) {
			return;
		}

		String placeholders = String.join(",", Collections.nCopies(hooplaIds.size(), "?"));
		String sql = "SELECT DISTINCT e.hooplaId, UNCOMPRESS(e.rawResponse) as rawResponse " +
			"FROM hoopla_export e " +
			"INNER JOIN hoopla_entitlements ent ON e.hooplaId = ent.hooplaId " +
			"INNER JOIN hoopla_entitlement_scopes scope ON ent.id = scope.entitlementId " +
			"WHERE e.hooplaId IN (" + placeholders + ")";

		try (PreparedStatement getUpdatedEntitledTitlesStmt = aspenConn.prepareStatement(sql)) {
			int paramIndex = 1;
			for (Long hooplaId : hooplaIds) {
				getUpdatedEntitledTitlesStmt.setLong(paramIndex++, hooplaId);
			}
			try (ResultSet entitledTitlesRS = getUpdatedEntitledTitlesStmt.executeQuery()) {
				int numIndexed = 0;
				while (entitledTitlesRS.next()) {
					try {
						long hooplaId = entitledTitlesRS.getLong("hooplaId");
						String rawResponse = entitledTitlesRS.getString("rawResponse");
						JSONObject curTitle = new JSONObject(rawResponse);

						String groupedWorkId = getRecordGroupingProcessor().groupHooplaRecord(curTitle, hooplaId);
						getGroupedWorkIndexer().processGroupedWork(groupedWorkId);
						numIndexed++;
					} catch (Exception e) {
						logEntry.incErrors("Error indexing updated entitled content", e);
					}
				}
				logEntry.addNote("Indexed " + numIndexed + " updated entitled titles");
			}
			getGroupedWorkIndexer().commitChanges();
		} catch (Exception e) {
			logEntry.incErrors("Error during updated entitled content indexing", e);
		}
	}

	private static String getAccessToken(HooplaSettings settings) {
		String username = settings.getApiUsername();
		String password = settings.getApiPassword();
		if (username == null || password == null){
			logger.error("Please set HooplaAPIUser and HooplaAPIPassword in settings");
			logEntry.addNote("Please set HooplaAPIUser and HooplaAPIPassword in settings");
			return null;
		}
		int numTries = 0;
		while (numTries <= 3) {
			numTries++;
			String getTokenUrl = settings.getApiUrl() + "/v2/token";
			WebServiceResponse response = NetworkUtils.postToURL(getTokenUrl, null, "application/json", null, logger, username + ":" + password);

			if (response.isSuccess()) {
				try {
					JSONObject responseJSON = new JSONObject(response.getMessage());
					String  accessToken =  responseJSON.getString("access_token");
					long tokenExpirationTime = (System.currentTimeMillis() / 1000) + responseJSON.getLong("expires_in");

					try {
						PreparedStatement updateTokenStmt = aspenConn.prepareStatement(
							"UPDATE hoopla_settings SET accessToken = ?, tokenExpirationTime = ? WHERE id = ?"
						);
						updateTokenStmt.setString(1, accessToken);
						updateTokenStmt.setLong(2, tokenExpirationTime);
						updateTokenStmt.setLong(3, settings.getSettingsId());
						updateTokenStmt.executeUpdate();
						return accessToken;
					} catch (SQLException e) {
						logEntry.incErrors("Error storing token", e);
					}
				} catch (JSONException e) {
					if (numTries == 3) {
						logEntry.addNote("Could not parse JSON for token " + response.getMessage());
						logger.error("Could not parse JSON for token " + response.getMessage(), e);
						return null;
					} else {
						try {
							Thread.sleep(1000 * 60 * 2);
						} catch (InterruptedException ex) {
							logEntry.incErrors("Thread was interrupted while sleeping");
						}
					}
				}
			}
		}
		logEntry.addNote("Could not get access token in 3 tries");
		return null;
	}

	private static Connection connectToDatabase(){
		Connection aspenConn = null;
		try{
			String databaseConnectionInfo = ConfigUtil.cleanIniValue(configIni.get("Database", "database_aspen_jdbc"));
			if (databaseConnectionInfo != null) {
				aspenConn = DriverManager.getConnection(databaseConnectionInfo);
				getAllExistingHooplaItemsStmt = aspenConn.prepareStatement("SELECT id, hooplaId, rawChecksum, UNCOMPRESSED_LENGTH(rawResponse) as rawResponseLength from hoopla_export");
				addHooplaTitleToDB = aspenConn.prepareStatement("INSERT INTO hoopla_export (hooplaId, title, kind, pa, demo, profanity, rating, abridged, children, price, rawChecksum, rawResponse, dateFirstDetected) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, COMPRESS(?), ?) ");
				updateHooplaTitleInDB = aspenConn.prepareStatement("UPDATE hoopla_export SET title = ?, kind = ?, pa = ?, demo = ?, profanity = ?, " +
						"rating = ?, abridged = ?, children = ?, price = ?, rawChecksum = ?, rawResponse = COMPRESS(?) WHERE id = ?");
				deleteHooplaItemStmt = aspenConn.prepareStatement("DELETE FROM hoopla_export where id = ?");
			}else{
				logger.error("Aspen database connection information was not provided");
				System.exit(1);
			}
		}catch (Exception e){
			logger.error("Error connecting to Aspen database " + e);
			System.exit(1);
		}
		return aspenConn;
	}

	private static void disconnectDatabase(Connection aspenConn) {
		try{
			addHooplaTitleToDB.close();
			addHooplaTitleToDB = null;
			updateHooplaTitleInDB.close();
			updateHooplaTitleInDB = null;
			deleteHooplaItemStmt.close();
			deleteHooplaItemStmt = null;
			aspenConn.close();
			//noinspection UnusedAssignment
			aspenConn = null;
		}catch (Exception e){
			logger.error("Error closing database ", e);
			System.exit(1);
		}
	}

	private static GroupedWorkIndexer getGroupedWorkIndexer() {
		if (groupedWorkIndexer == null) {
			groupedWorkIndexer = new GroupedWorkIndexer(serverName, aspenConn, configIni, false, false, logEntry, logger);
		}
		return groupedWorkIndexer;
	}

	private static RecordGroupingProcessor getRecordGroupingProcessor(){
		if (recordGroupingProcessorSingleton == null) {
			recordGroupingProcessorSingleton = new RecordGroupingProcessor(aspenConn, serverName, logEntry, logger);
		}
		return recordGroupingProcessorSingleton;
	}

	private static void regroupAllRecords(Connection dbConn, long settingsId, GroupedWorkIndexer indexer, HooplaExtractLogEntry logEntry)  throws SQLException {
		logEntry.addNote("Starting to regroup all records");
		PreparedStatement getAllRecordsToRegroupStmt = dbConn.prepareStatement(
			"SELECT DISTINCT e.hooplaId, UNCOMPRESS(e.rawResponse) as rawResponse " +
			"FROM hoopla_export e " +
			"INNER JOIN hoopla_entitlements ent ON e.hooplaId = ent.hooplaId " +
			"INNER JOIN hoopla_entitlement_scopes scope ON ent.id = scope.entitlementId",
			ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
		//It turns out to be quite slow to look this up repeatedly, just grab the existing values for all and store in memory
		PreparedStatement getOriginalPermanentIdForRecordStmt = dbConn.prepareStatement("SELECT identifier, permanent_id from grouped_work_primary_identifiers join grouped_work on grouped_work_id = grouped_work.id WHERE type = 'hoopla'", ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
		HashMap<Long, String> allPermanentIdsForHoopla = new HashMap<>();
		ResultSet getOriginalPermanentIdForRecordRS = getOriginalPermanentIdForRecordStmt.executeQuery();
		while (getOriginalPermanentIdForRecordRS.next()){
			allPermanentIdsForHoopla.put(getOriginalPermanentIdForRecordRS.getLong("identifier"), getOriginalPermanentIdForRecordRS.getString("permanent_id"));
		}
		getOriginalPermanentIdForRecordRS.close();
		getOriginalPermanentIdForRecordStmt.close();
		ResultSet allRecordsToRegroupRS = getAllRecordsToRegroupStmt.executeQuery();
		while (allRecordsToRegroupRS.next()) {
			logEntry.incRecordsRegrouped();
			long recordIdentifier = allRecordsToRegroupRS.getLong("hooplaId");
			String originalGroupedWorkId;
			originalGroupedWorkId = allPermanentIdsForHoopla.get(recordIdentifier);
			if (originalGroupedWorkId == null){
				originalGroupedWorkId = "false";
			}
			String rawResponseString = new String(allRecordsToRegroupRS.getBytes("rawResponse"), StandardCharsets.UTF_8);
			JSONObject rawResponse = new JSONObject(rawResponseString);
			//Pass null to processMarcRecord.  It will do the lookup to see if there is an existing id there.
			String groupedWorkId = getRecordGroupingProcessor().groupHooplaRecord(rawResponse, recordIdentifier);
			if (!originalGroupedWorkId.equals(groupedWorkId)) {
				logEntry.incChangedAfterGrouping();
			}
			//process records to regroup after every 1000 changes, so we keep up with the changes.
			if (logEntry.getNumChangedAfterGrouping() % 1000 == 0){
				indexer.processScheduledWorks(logEntry, false, -1);
			}
		}

		//Finish reindexing anything that just changed
		if (logEntry.getNumChangedAfterGrouping() > 0){
			indexer.processScheduledWorks(logEntry, false, -1);
		}

		try {
			PreparedStatement clearRegroupAllRecordsStmt = dbConn.prepareStatement("UPDATE hoopla_settings set regroupAllRecords = 0 where id =?");
			clearRegroupAllRecordsStmt.setLong(1, settingsId);
			clearRegroupAllRecordsStmt.executeUpdate();
		}catch (Exception e){
			logEntry.incErrors("Could not clear regroup all records", e);
		}
		logEntry.addNote("Finished regrouping all records");
		logEntry.saveResults();
	}
}
