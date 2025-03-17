package com.turning_leaf_technologies.hoopla;

import com.turning_leaf_technologies.config.ConfigUtil;
import com.turning_leaf_technologies.file.JarUtil;
import org.aspen_discovery.grouping.RecordGroupingProcessor;
import org.aspen_discovery.grouping.RemoveRecordFromWorkResult;
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
import org.marc4j.MarcException;

import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.text.SimpleDateFormat;
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
		String type = null;
		if (args.length == 0) {
			serverName = AspenStringUtils.getInputFromCommandLine("Please enter the server name");
			if (serverName.isEmpty()) {
				System.out.println("You must provide the server name as the first argument.");
				System.exit(1);
			}
			String extractSingleWorkResponse = AspenStringUtils.getInputFromCommandLine("Process a single work? (y/N)");
			if (extractSingleWorkResponse.equalsIgnoreCase("y")) {
				extractSingleWork = true;
				String extractSingleWorkType = AspenStringUtils.getInputFromCommandLine("Enter the type of work to extract (INSTANT/FLEX)");
				if (extractSingleWorkType.equalsIgnoreCase("INSTANT") || extractSingleWorkType.equalsIgnoreCase("FLEX")){
					singleWorkType = extractSingleWorkType.toUpperCase();
				} else {
					System.out.println("Invalid work type. Please enter INSTANT or FLEX.");
					System.exit(1);
				}
			}

		} else {
			serverName = args[0];
			if (args.length > 1){
				if (args[1].equalsIgnoreCase("singleWork") || args[1].equalsIgnoreCase("singleRecord")){
					extractSingleWork = true;
					if (args.length > 2) {
						singleWorkId = args[2];
						if (args.length > 3) {
							type = args[3].toUpperCase();
							if (type.equals("INSTANT") || type.equals("FLEX")) {
								singleWorkType = type;
							} else {
								System.out.println("Invalid work type. Please enter INSTANT or FLEX.");
								System.exit(1);
							}
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
			PreparedStatement getItemDetailsForRecordStmt = aspenConn.prepareStatement("SELECT UNCOMPRESS(rawResponse) as rawResponse, type FROM hoopla_export where hooplaId = ?", ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
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
					String type = getItemDetailsForRecordRS.getString("type");
					try {
						//JSONArray itemsArray = new JSONArray(rawResponse);
						//	JSONObject itemDetails = itemsArray.getJSONObject(0);
						JSONObject itemDetails = new JSONObject(rawResponse);
						String groupedWorkId =  getRecordGroupingProcessor().groupHooplaRecord(itemDetails, hooplaId);
						//Reindex the record
						getGroupedWorkIndexer().processGroupedWork(groupedWorkId);

						if (type != null && type.equals("FLEX")){
							numFlexRecords++;
							//	updateFlexAvailability(hooplaId, itemDetails);
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
					logEntry.addNote("Could not get details for record to reload " + hooplaId + " it has been deleted");
					markRecordToReloadAsProcessedStmt.setLong(1, recordToReloadId);
					markRecordToReloadAsProcessedStmt.executeUpdate();
					numRecordsToReloadProcessed++;
				}
				getItemDetailsForRecordRS.close();
			}
			if (numRecordsToReloadProcessed > 0){
				logEntry.addNote("Regrouped " + numRecordsToReloadProcessed + " records marked for reprocessing");
				logEntry.addNote("Regrouped " + numInstantRecords + " instant records");
				logEntry.addNote("Regrouped " + numFlexRecords + " flex records");
			}
			getRecordsToReloadRS.close();
		}catch (Exception e){
			logEntry.incErrors("Error processing records to reload ", e);
		}
	}
	/*
		private static void updateFlexAvailability(long hooplaId, JSONObject itemDetails) {
			try {
				JSONObject availability = itemDetails.getJSONObject("availability");
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

				updateFlexAvailabilityStmt.setLong(1, hooplaId);
				updateFlexAvailabilityStmt.setInt(2, holdsQueueSize);
				updateFlexAvailabilityStmt.setInt(3, availability.getInt("availableCopies"));
				updateFlexAvailabilityStmt.setInt(4, availability.getInt("totalCopies"));
				updateFlexAvailabilityStmt.setString(5, availability.getString("status"));

				updateFlexAvailabilityStmt.executeUpdate();

			} catch (SQLException e) {
				logEntry.incErrors("Error updating FLEX availability for record " + hooplaId, e);
			} catch (JSONException e) {
				logEntry.incErrors("Error updating FLEX availability for record " + hooplaId, e);
			}
		}
	*/
	private static void deleteItems(String type) {
		int numDeleted = 0;
		try {
			for (HooplaTitle hooplaTitle : existingRecords.values()) {
				String titleType = hooplaTitle.getType();
				// Handle null type values safely
				boolean typeMatches = (titleType != null && titleType.equals(type)) ||
					(titleType == null && type.equals("FLEX") && isFlexTitleBasedOnAvailability(hooplaTitle.getHooplaId()));

				if (typeMatches && !hooplaTitle.isFoundInExport() && hooplaTitle.isActive()) {
					deleteHooplaItemStmt.setLong(1, hooplaTitle.getId());
					deleteHooplaItemStmt.executeUpdate();
					RemoveRecordFromWorkResult result = getRecordGroupingProcessor().removeRecordFromGroupedWork("hoopla", Long.toString(hooplaTitle.getHooplaId()));

					if (type.equals("FLEX")){
						PreparedStatement deleteFlexAvailabilityStmt = aspenConn.prepareStatement("DELETE from hoopla_flex_availability where hooplaId = ?");
						deleteFlexAvailabilityStmt.setLong(1, hooplaTitle.getHooplaId());
						deleteFlexAvailabilityStmt.executeUpdate();
					}

					if (result.reindexWork){
						getGroupedWorkIndexer().processGroupedWork(result.permanentId);
					}else if (result.deleteWork){
						//Delete the work from solr and the database
						getGroupedWorkIndexer().deleteRecord(result.permanentId, result.groupedWorkId);
					}
					numDeleted++;
					logEntry.incDeleted();
				}
			}
			if (numDeleted > 0) {
				logEntry.saveResults();
				logger.warn("Deleted " + numDeleted + " old " + type + " titles");
			}
		}catch (SQLException e) {
			logger.error("Error deleting " + type + " items", e);
			logEntry.addNote("Error deleting " + type + " items " + e);
		}
	}

	/**
	 * Helper method to check if a title with NULL type is a FLEX title based on availability data
	 */
	private static boolean isFlexTitleBasedOnAvailability(long hooplaId) {
		try {
			PreparedStatement checkFlexAvailabilityStmt = aspenConn.prepareStatement(
				"SELECT COUNT(*) as count FROM hoopla_flex_availability WHERE hooplaId = ?"
			);
			checkFlexAvailabilityStmt.setLong(1, hooplaId);
			ResultSet resultSet = checkFlexAvailabilityStmt.executeQuery();
			if (resultSet.next()) {
				return resultSet.getInt("count") > 0;
			}
		} catch (SQLException e) {
			logger.error("Error checking FLEX availability for hooplaId " + hooplaId, e);
		}
		return false;
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
					allRecordsRS.getBoolean("active"),
					allRecordsRS.getLong("rawResponseLength"),
					allRecordsRS.getString("type")
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
			while (getSettingsRS.next()) {
				numSettings++;
				hooplaAPIBaseURL = getSettingsRS.getString("apiUrl");
				String apiUsername = getSettingsRS.getString("apiUsername");
				String apiPassword = getSettingsRS.getString("apiPassword");
				String hooplaLibraryId = getSettingsRS.getString("libraryId");
				boolean indexByDay = getSettingsRS.getBoolean("indexByDay");
				boolean isRegroupAllRecords = getSettingsRS.getBoolean("regroupAllRecords");
				long settingsId = getSettingsRS.getLong("id");

				/////// THIS PART NEED TO MODIFY, BECAUSE WE NEED TO STORE TOKEN
				String accessToken = getAccessToken(apiUsername, apiPassword);
				if (accessToken == null) {
					logEntry.incErrors("Could not load access token");
					return true;
				}

				// Process Instant Content
				boolean hooplaInstantEnabled = getSettingsRS.getBoolean("hooplaInstantEnabled");
				if (hooplaInstantEnabled) {
					logEntry.addNote("Exporting Instant Content");
					boolean doFullReloadInstant = getSettingsRS.getBoolean("runFullUpdateInstant");
					long lastUpdateOfChangedRecordsInstant = getSettingsRS.getLong("lastUpdateOfChangedRecordsInstant");
					long lastUpdateOfAllRecordsInstant = getSettingsRS.getLong("lastUpdateOfAllRecordsInstant");

					boolean instantUpdated = exportHooplaContent(settingsId, doFullReloadInstant, indexByDay, lastUpdateOfChangedRecordsInstant, lastUpdateOfAllRecordsInstant, hooplaAPIBaseURL, hooplaLibraryId, accessToken, "INSTANT", apiUsername, apiPassword);
					updatesRun |= instantUpdated;

				}

				// Process Flex Content
				boolean hooplaFlexEnabled = getSettingsRS.getBoolean("hooplaFlexEnabled");
				if (hooplaFlexEnabled) {
					logEntry.addNote("Exporting Flex Content");
					boolean doFullReloadFlex = getSettingsRS.getBoolean("runFullUpdateFlex");
					long lastUpdateOfChangedRecordsFlex = getSettingsRS.getLong("lastUpdateOfChangedRecordsFlex");
					long lastUpdateOfAllRecordsFlex = getSettingsRS.getLong("lastUpdateOfAllRecordsFlex");

					boolean flexUpdated = exportHooplaContent(settingsId, doFullReloadFlex, indexByDay, lastUpdateOfChangedRecordsFlex, lastUpdateOfAllRecordsFlex, hooplaAPIBaseURL, hooplaLibraryId, accessToken, "FLEX", apiUsername, apiPassword);
					updatesRun |= flexUpdated;

					//if (flexUpdated) {
					getFlexAvailability(hooplaAPIBaseURL, hooplaLibraryId, accessToken);
					//}
				}
				if (isRegroupAllRecords) {
					regroupAllRecords(aspenConn, settingsId, getGroupedWorkIndexer(), logEntry);
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


	private static boolean exportHooplaContent(long settingsId, boolean doFullReload, boolean indexByDay, long lastUpdateOfChangedRecords, long lastUpdateOfAllRecords, String hooplaAPIBaseURL, String hooplaLibraryId, String accessToken, String type, String apiUsername, String apiPassword) {
		boolean updatedContent = false;
		long lastUpdate = Math.max(lastUpdateOfChangedRecords, lastUpdateOfAllRecords);
		String purchaseModel = type.equals("INSTANT") ? "PPU" : "EST";
		try {
			if (doFullReload){
				//Unset that a full update needs to be done
				PreparedStatement updateSettingsStmt = aspenConn.prepareStatement("UPDATE hoopla_settings set runFullUpdate" + type + " = 0 where id = ?");
				updateSettingsStmt.setLong(1, settingsId);
				updateSettingsStmt.executeUpdate();
				logEntry.addNote("Processing full update for " + type);
			} else if (indexByDay) {
				//We only want to index once a day at 1 am Local Time
				ZonedDateTime nowLocalTime = ZonedDateTime.now();
				int curHour = nowLocalTime.getHour();
				ZonedDateTime startOfToday = nowLocalTime.truncatedTo(ChronoUnit.DAYS);
				long startOfTodaySeconds = startOfToday.toEpochSecond();
				ZonedDateTime thirtyTwoHoursAgoTime = nowLocalTime.minusHours(32);
				long thirtyTwoHoursAgo = thirtyTwoHoursAgoTime.toInstant().getEpochSecond();

				if (curHour == 1){
					if (lastUpdateOfChangedRecords >= startOfTodaySeconds) {
						logger.warn("Already completed today's " + type + " extraction at 1 AM. Skipping until tomorrow.");
						return updatedContent;
					}
					//Set last update time to 32 hours ago (go bigger to get more updates)
					if (thirtyTwoHoursAgo < lastUpdate){
						lastUpdate = thirtyTwoHoursAgo;
					}
					numRetries32HoursAfter = 0;
				}else{
					//It's not 1 am Local time, skip for now.
					//Figure out when we last indexed this collection.
					if (lastUpdate >= thirtyTwoHoursAgo) {
						//Go ahead and index even if we are off schedule
						return updatedContent;
					}
					// If we don't have updates for 32 hours, we will try 3 times
					// If we exceed 3 times and fail, we will wait until 1 AM
					if (numRetries32HoursAfter >= 3){
						logger.warn("Exceeded 3 retries for 32 hours catch up, waiting until 1 AM");
						return updatedContent;
					}
					numRetries32HoursAfter++;
					logEntry.addNote("Retrying " + type + " extraction after 32 hours " + numRetries32HoursAfter + " of 3");
				}
			}

			updatedContent = true;

			//Formulate the first call depending on if we are doing a full reload or not
			String url = hooplaAPIBaseURL + "/api/v1/libraries/" + hooplaLibraryId + "/content";
			if (!doFullReload && lastUpdate > 0) {
				//Give a 2-minute buffer for the extract
				lastUpdate -= 120;
				logEntry.addNote("Extracting records since " + new Date(lastUpdate * 1000));
				url += "?startTime=" + lastUpdate + "&limit=500&purchaseModel=" + purchaseModel;
			} else {
				url += "?limit=500&purchaseModel=" + purchaseModel;
			}

			HashMap<String, String> headers = new HashMap<>();
			headers.put("Authorization", "Bearer " + accessToken);
			headers.put("Content-Type", "application/json");
			headers.put("Accept", "application/json");
			WebServiceResponse response = NetworkUtils.getURL(url, logger, headers);
			if (!response.isSuccess()){
				logEntry.incErrors("Could not get titles from " + url + " " + response.getMessage() + " " + response.getResponseCode());
				return updatedContent;
			}else {
				JSONObject responseJSON = new JSONObject(response.getMessage());
				if (responseJSON.has("titles")) {
					JSONArray responseTitles = responseJSON.getJSONArray("titles");
					if (responseTitles != null && !responseTitles.isEmpty()) {
						updateTitlesInDB(responseTitles, false, doFullReload, type);
						logEntry.saveResults();
					}

					String startToken = null;
					if (responseJSON.has("nextStartToken")) {
						startToken = responseJSON.get("nextStartToken").toString();
					}

					int numTries = 0;
					while (startToken != null) {
						if (!doFullReload && lastUpdate > 0) {
							url = hooplaAPIBaseURL + "/api/v1/libraries/" + hooplaLibraryId + "/content?startTime=" + lastUpdate + "&startToken=" + startToken + "&limit=500&purchaseModel=" + purchaseModel;
						}else {
							url = hooplaAPIBaseURL + "/api/v1/libraries/" + hooplaLibraryId + "/content?startToken=" + startToken + "&limit=500&purchaseModel=" + purchaseModel;
						}
						response = NetworkUtils.getURL(url, logger, headers);
						if (response.isSuccess()){
							responseJSON = new JSONObject(response.getMessage());
							if (responseJSON.has("titles")) {
								responseTitles = responseJSON.getJSONArray("titles");
								if (responseTitles != null && !responseTitles.isEmpty()) {
									updateTitlesInDB(responseTitles, false, doFullReload, type);
								}
							}
							if (responseJSON.has("nextStartToken")) {
								startToken = responseJSON.get("nextStartToken").toString();
							} else {
								startToken = null;
							}
						}else{
							if (response.getResponseCode() == 401 || response.getResponseCode() == 504 || response.getResponseCode() == 503){
								numTries++;
								if (numTries >= 3){
									logEntry.incErrors("Error loading data after 3 attempts for " + type + " from" + url + " " + response.getResponseCode() + " " + response.getMessage());
									startToken = null;
								}else{
									try {
										Thread.sleep(1000 * 60 * 2); //Wait for 2 minutes before trying again
									} catch (InterruptedException e) {
										logEntry.incErrors("Error sleeping for 2 minutes", e);
									}
									accessToken = getAccessToken(apiUsername, apiPassword);
									headers.put("Authorization", "Bearer " + accessToken);
								}
							}else {
								logEntry.incErrors("Error loading data for " + type + " from " + url + " " + response.getResponseCode() + " " + response.getMessage());
								startToken = null;
							}
						}

						logEntry.saveResults();
					}
				}
			}

			//Delete records from Hoopla, but not if we have errors
			if (doFullReload && !logEntry.hasErrors()){
				deleteItems(type);
			}

			try{
				//Set the extract time
				PreparedStatement updateSettingsStmt = null;
				if (doFullReload){
					if (!logEntry.hasErrors()) {
						updateSettingsStmt = aspenConn.prepareStatement("UPDATE hoopla_settings set lastUpdateOfAllRecords" + type + " = ? where id = ?");
					} else {
						//force another full update
						PreparedStatement reactiveFullUpdateStmt = aspenConn.prepareStatement("UPDATE hoopla_settings set runFullUpdate" + type + " = 1 where id = ?");
						reactiveFullUpdateStmt.setLong(1, settingsId);
						reactiveFullUpdateStmt.executeUpdate();
					}
				}else{
					// Update the lastUpdateOfChangedRecordsInstant if we have a successful response
					if (response.isSuccess()){
						updateSettingsStmt = aspenConn.prepareStatement("UPDATE hoopla_settings set lastUpdateOfChangedRecords" + type + " = ? where id = ?");
					}
				}
				if (updateSettingsStmt != null) {
					updateSettingsStmt.setLong(1, startTimeForLogging);
					updateSettingsStmt.setLong(2, settingsId);
					updateSettingsStmt.executeUpdate();
					numRetries32HoursAfter = 0;
				}
			} catch (SQLException e) {
				logEntry.incErrors("Error updating settings for" + type, e);
			}
		} catch (SQLException e) {
			logEntry.incErrors("Error updating settings for" + type, e);
		}
		return updatedContent;
	}

	private static void getFlexAvailability(String hooplaAPIBaseURL, String hooplaLibraryId, String accessToken) {
		try {
			PreparedStatement getFlexTitlesStmt = aspenConn.prepareStatement("SELECT t.id, t.hooplaId, fa.holdsQueueSize, fa.availableCopies, fa.totalCopies, fa.status, fa.hooplaId " +
				"FROM hoopla_export t " +
				"LEFT JOIN hoopla_flex_availability fa ON t.hooplaId = fa.hooplaId " +
				"WHERE t.type = 'FLEX' AND t.active = 1");
			ResultSet flexTitlesRS = getFlexTitlesStmt.executeQuery();

			ArrayList<Long> hooplaIds = new ArrayList<>();
			while (flexTitlesRS.next()) {
				long hooplaId = flexTitlesRS.getLong("hooplaId");
				hooplaIds.add(hooplaId);
				boolean existingInDB = flexTitlesRS.getString("status") != null;
				Integer existingHoldsQueueSize = existingInDB ? flexTitlesRS.getInt("holdsQueueSize") : 0;
				Integer existingAvailableCopies = existingInDB ? flexTitlesRS.getInt("availableCopies") : 0;
				Integer existingTotalCopies = existingInDB ? flexTitlesRS.getInt("totalCopies") : 0;
				String existingStatus = existingInDB ? flexTitlesRS.getString("status") : null;


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
					if (availabilityArray.length() > 0) {
						JSONObject titleInfo = availabilityArray.getJSONObject(0);
						Long contentId = titleInfo.getLong("contentId");
						if (hooplaId != contentId) {
							logEntry.incErrors("Response content ID " + contentId + " mismatch for title " + hooplaId);
							continue;
						}
						JSONObject availability = titleInfo.getJSONObject("availability");
						if (availability.length() > 0) {
							String newStatus = availability.getString("status");
							int newHoldsQueueSize = newStatus.equals("BORROW") ? 0 :
								availability.has("holdsQueueSize") ? availability.getInt("holdsQueueSize") : 0;
							int newAvailableCopies = availability.getInt("availableCopies");
							int newTotalCopies = availability.getInt("totalCopies");


							boolean needsUpdate =  !existingInDB || existingHoldsQueueSize != newHoldsQueueSize || existingAvailableCopies != newAvailableCopies || existingTotalCopies != newTotalCopies || !Objects.equals(existingStatus, newStatus);

							if (needsUpdate) {
								try {
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

									updateFlexAvailabilityStmt.setLong(1, hooplaId);
									updateFlexAvailabilityStmt.setInt(2, newHoldsQueueSize);
									updateFlexAvailabilityStmt.setInt(3, newAvailableCopies);
									updateFlexAvailabilityStmt.setInt(4, newTotalCopies);
									updateFlexAvailabilityStmt.setString(5, newStatus);
									updateFlexAvailabilityStmt.executeUpdate();
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
		}
		catch (Exception e) {
			logEntry.incErrors("Error getting flex availability", e);
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
				hooplaAPIBaseURL = getSettingsRS.getString("apiUrl");
				String apiUsername = getSettingsRS.getString("apiUsername");
				String apiPassword = getSettingsRS.getString("apiPassword");
				String hooplaLibraryId = getSettingsRS.getString("libraryId");

				String accessToken = getAccessToken(apiUsername, apiPassword);
				if (accessToken == null) {
					logEntry.incErrors("Could not load access token for single title");
					return;
				}

				long numericSingleWorkId = Long.parseLong(singleWorkId);

				// Check if the work already exists in our database
				boolean existingWork = false;
				HooplaTitle existingTitle = existingRecords.get(numericSingleWorkId);
				if (existingTitle != null) {
					existingWork = true;
					logger.info("Work " + singleWorkId + " already exists in database, will update if API returns it");
				}

				// First attempt: direct content query by ID
				String url = hooplaAPIBaseURL + "/api/v1/libraries/" + hooplaLibraryId + "/content";
				if (singleWorkType.equals("FLEX")) {
					url += "?limit=1&startToken=" + (numericSingleWorkId - 1) + "&purchaseModel=EST";
				} else {
					url += "?limit=1&startToken=" + (numericSingleWorkId - 1) + "&purchaseModel=PPU";
				}

				HashMap<String, String> headers = new HashMap<>();
				headers.put("Authorization", "Bearer " + accessToken);
				headers.put("Content-Type", "application/json");
				headers.put("Accept", "application/json");

				WebServiceResponse response = NetworkUtils.getURL(url, logger, headers);
				boolean workFound = false;

				if (!response.isSuccess()){
					logEntry.incErrors("Could not get title from " + url + " " + response.getMessage());
				} else {
					JSONObject responseJSON = new JSONObject(response.getMessage());
					if (responseJSON.has("titles")) {
						JSONArray responseTitles = responseJSON.getJSONArray("titles");
						if (responseTitles != null && responseTitles.length() > 0) {
							// Check if the returned title is actually the one we requested
							boolean foundRequestedTitle = false;
							for (int i = 0; i < responseTitles.length(); i++) {
								JSONObject titleObj = responseTitles.getJSONObject(i);
								if (titleObj.getLong("id") == numericSingleWorkId) {
									foundRequestedTitle = true;
									workFound = true;
									break;
								}
							}

							if (foundRequestedTitle) {
								logger.info("Found requested title ID " + singleWorkId + " in API response");
								updateTitlesInDB(responseTitles, true, false, singleWorkType);
								logEntry.saveResults();

								// Process availability for FLEX titles
								if (singleWorkType.equals("FLEX")) {
									getFlexAvailabilityForSingleTitle(hooplaAPIBaseURL, hooplaLibraryId, accessToken, numericSingleWorkId);
								}
							} else {
								logger.warn("API returned titles but requested ID " + singleWorkId + " was not among them");
							}
						} else {
							logger.warn("API returned empty titles array for ID " + singleWorkId);
						}
					} else {
						logger.warn("API response did not contain titles array for ID " + singleWorkId);
					}
				}

				// If we couldn't find the work and it's not in our database, report error
				if (!workFound && !existingWork) {
					logEntry.incErrors("Could not find Hoopla title with ID " + singleWorkId + " (" + singleWorkType + ")");
				} else if (!workFound && existingWork) {
					// Work exists in our database but could not be retrieved - might be deleted
					logEntry.addNote("Title " + singleWorkId + " exists in database but could not be retrieved from API. It may have been deleted.");

					// Check if we should update the status to inactive
					if (existingTitle.isActive()) {
						try {
							PreparedStatement updateStatusStmt = aspenConn.prepareStatement(
								"UPDATE hoopla_export SET active = 0 WHERE hooplaId = ?"
							);
							updateStatusStmt.setLong(1, numericSingleWorkId);
							int rowsUpdated = updateStatusStmt.executeUpdate();

							if (rowsUpdated > 0) {
								logEntry.addNote("Marked title " + singleWorkId + " as inactive");
								logEntry.incUpdated();

								// Remove this title from any grouped works
								RemoveRecordFromWorkResult result = getRecordGroupingProcessor().removeRecordFromGroupedWork("hoopla", Long.toString(numericSingleWorkId));
								if (result.reindexWork) {
									getGroupedWorkIndexer().processGroupedWork(result.permanentId);
								} else if (result.deleteWork) {
									getGroupedWorkIndexer().deleteRecord(result.permanentId, result.groupedWorkId);
								}
							}

							// For FLEX titles, clean up availability data
							if (singleWorkType.equals("FLEX")) {
								PreparedStatement deleteFlexAvailabilityStmt = aspenConn.prepareStatement(
									"DELETE FROM hoopla_flex_availability WHERE hooplaId = ?"
								);
								deleteFlexAvailabilityStmt.setLong(1, numericSingleWorkId);
								deleteFlexAvailabilityStmt.executeUpdate();
							}
						} catch (SQLException e) {
							logEntry.incErrors("Error updating status for title " + singleWorkId, e);
						}
					}
				}
			}

			if (numSettings == 0){
				logger.error("Unable to find settings for Hoopla, please add settings to the database");
			}
		} catch (Exception e){
			logEntry.incErrors("Error exporting single Hoopla title " + singleWorkId, e);
		}
	}

	private static void getFlexAvailabilityForSingleTitle(String hooplaAPIBaseURL, String hooplaLibraryId, String accessToken, long titleId) {
		String availUrl = hooplaAPIBaseURL + "/api/v1/libraries/" + hooplaLibraryId + "/content/info?contentIds=" + titleId;

		HashMap<String, String> headers = new HashMap<>();
		headers.put("Authorization", "Bearer " + accessToken);
		headers.put("Content-Type", "application/json");
		headers.put("Accept", "application/json");

		WebServiceResponse availResponse = NetworkUtils.getURL(availUrl, logger, headers);
		if (!availResponse.isSuccess()) {
			logEntry.incErrors("Could not get availability for FLEX title " + titleId + " from " + availUrl + " " + availResponse.getMessage());
			return;
		}

		try {
			JSONArray availabilityArray = new JSONArray(availResponse.getMessage());
			if (availabilityArray.length() > 0) {
				JSONObject titleInfo = availabilityArray.getJSONObject(0);
				JSONObject availability = titleInfo.getJSONObject("availability");

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

				updateFlexAvailabilityStmt.setLong(1, titleId);
				updateFlexAvailabilityStmt.setInt(2, holdsQueueSize);
				updateFlexAvailabilityStmt.setInt(3, availability.getInt("availableCopies"));
				updateFlexAvailabilityStmt.setInt(4, availability.getInt("totalCopies"));
				updateFlexAvailabilityStmt.setString(5, availability.getString("status"));
				updateFlexAvailabilityStmt.executeUpdate();

				logEntry.addNote("Updated availability for FLEX title " + titleId);
			} else {
				logEntry.addNote("No availability information found for FLEX title " + titleId);
			}
		} catch (Exception e) {
			logEntry.incErrors("Error updating FLEX availability for title " + titleId, e);
		}
	}

	private static void updateTitlesInDB(JSONArray responseTitles, boolean forceRegrouping, boolean doFullReload, String type) {
		logEntry.incNumProducts(responseTitles.length());
		for (int i = 0; i < responseTitles.length(); i++){
			try {
				JSONObject curTitle = responseTitles.getJSONObject(i);

				//ALILI ONLY FOR TESTING PURPOSES
	/* 			if (type.equals("FLEX")){
					if (!curTitle.has("price")) {
						curTitle.put("price", 0.50);
					}
					if (curTitle.has("active") && !curTitle.getBoolean("active")) {
						curTitle.put("active", true);
					}
				}*/

				String rawResponse = curTitle.toString();
				checksumCalculator.reset();
				checksumCalculator.update(rawResponse.getBytes());
				long rawChecksum = checksumCalculator.getValue();
				boolean curTitleActive = curTitle.getBoolean("active");
				logger.warn("curTitleActive: " + curTitleActive);

				long hooplaId = curTitle.getLong("id"); //formerly titleId was used but this is not unique for tv series

				HooplaTitle existingTitle = existingRecords.get(hooplaId);
				boolean recordUpdated = false;
				if (existingTitle != null) {
					//Record exists
					if ((existingTitle.getChecksum() != rawChecksum) || (existingTitle.getRawResponseLength() != rawResponse.length())){
						recordUpdated = true;
						logEntry.incUpdated();
						if (existingTitle.isActive() != curTitleActive) {
							if (curTitleActive) {
								logEntry.incAdded();
							} else {
								logEntry.incDeleted();
							}
						}else{
							logEntry.incUpdated();
						}
					}
					existingTitle.setFoundInExport(true);
				}else{
					if (!curTitleActive){
						logEntry.incSkipped();
						continue;
					}
					recordUpdated = true;
					logEntry.incAdded();
				}

				if (!curTitleActive){
					//Title is currently active (and if we got this far exists, delete it)
					//Delete the record if it exists
					if (type.equals("FLEX")) {
						try {
							PreparedStatement deleteFlexAvailabilityStmt = aspenConn.prepareStatement(
								"DELETE from hoopla_flex_availability where hooplaId = ?"
							);
							deleteFlexAvailabilityStmt.setLong(1, hooplaId);
							deleteFlexAvailabilityStmt.executeUpdate();
						} catch (SQLException e) {
							logEntry.incErrors("Error deleting FLEX availability for inactive title " + hooplaId, e);
						}
					}

					RemoveRecordFromWorkResult result = getRecordGroupingProcessor().removeRecordFromGroupedWork("hoopla", Long.toString(hooplaId));
					if (result.reindexWork) {
						getGroupedWorkIndexer().processGroupedWork(result.permanentId);
					} else if (result.deleteWork) {
						//Delete the work from solr and the database
						getGroupedWorkIndexer().deleteRecord(result.permanentId, result.groupedWorkId);
					}
					logEntry.incDeleted();
					deleteHooplaItemStmt.setLong(1, existingTitle.getId());
					deleteHooplaItemStmt.executeUpdate();
				}else {
					if (existingTitle == null){
						addHooplaTitleToDB.setLong(1, hooplaId);
						addHooplaTitleToDB.setBoolean(2, true);
						addHooplaTitleToDB.setString(3, curTitle.getString("title"));
						addHooplaTitleToDB.setString(4, curTitle.getString("kind"));
						addHooplaTitleToDB.setBoolean(5, curTitle.getBoolean("pa"));
						addHooplaTitleToDB.setBoolean(6, curTitle.getBoolean("demo"));
						addHooplaTitleToDB.setBoolean(7, curTitle.getBoolean("profanity"));
						addHooplaTitleToDB.setString(8, curTitle.has("rating") ? curTitle.getString("rating") : "");
						addHooplaTitleToDB.setBoolean(9, curTitle.getBoolean("abridged"));
						addHooplaTitleToDB.setBoolean(10, curTitle.getBoolean("children"));
						if (type.equals("FLEX")) {
							addHooplaTitleToDB.setDouble(11, 0.0);
						} else {
							addHooplaTitleToDB.setDouble(11, curTitle.getDouble("price"));
						}
						addHooplaTitleToDB.setLong(12, rawChecksum);
						addHooplaTitleToDB.setString(13, rawResponse);
						addHooplaTitleToDB.setLong(14, startTimeForLogging);
						addHooplaTitleToDB.setString(15, type);
						try {
							addHooplaTitleToDB.executeUpdate();

							try {
								String groupedWorkId = getRecordGroupingProcessor().groupHooplaRecord(curTitle, hooplaId);
								indexRecord(groupedWorkId);
							} catch (org.marc4j.MarcException e) {
								if (e.getMessage() != null && e.getMessage().contains("Cannot invoke \"String.length()\" because \"spec\" is null")) {
									// This is likely an issue with MARC tag 202 in Flex records
									logger.error("MARC conversion error with tag 202 when grouping Hoopla record " +
										hooplaId + ". This is a known issue with some Hoopla Flex records.", e);
									logEntry.addNote("MARC conversion error with tag 202 when grouping record " +
										hooplaId + ". The record was added but metadata may be incomplete.");
								} else {
									// Some other MARC exception
									logEntry.incErrors("MARC exception when grouping Hoopla record " + hooplaId + ": " + e.getMessage());
								}
							}
						} catch (DataTruncation e) {
							logEntry.addNote("Record " + hooplaId + " " + curTitle.getString("title") + " contained invalid data " + e);
						} catch (SQLException e) {
							logEntry.incErrors("Error adding hoopla title to database record " + hooplaId + " " + curTitle.getString("title"), e);
						}
					}else if (recordUpdated || doFullReload || forceRegrouping){
						logger.warn("we are here in recordUpdated");
						updateHooplaTitleInDB.setBoolean(1, true);
						updateHooplaTitleInDB.setString(2, curTitle.getString("title"));
						updateHooplaTitleInDB.setString(3, curTitle.getString("kind"));
						updateHooplaTitleInDB.setBoolean(4, curTitle.getBoolean("pa"));
						updateHooplaTitleInDB.setBoolean(5, curTitle.getBoolean("demo"));
						updateHooplaTitleInDB.setBoolean(6, curTitle.getBoolean("profanity"));
						updateHooplaTitleInDB.setString(7, curTitle.has("rating") ? curTitle.getString("rating") : "");
						updateHooplaTitleInDB.setBoolean(8, curTitle.getBoolean("abridged"));
						updateHooplaTitleInDB.setBoolean(9, curTitle.getBoolean("children"));
						if (type.equals("FLEX")) {
							updateHooplaTitleInDB.setDouble(10, 0.0);
						} else {
							updateHooplaTitleInDB.setDouble(10, curTitle.getDouble("price"));
						}
						updateHooplaTitleInDB.setLong(11, rawChecksum);
						updateHooplaTitleInDB.setString(12, rawResponse);
						updateHooplaTitleInDB.setString(13, type);
						updateHooplaTitleInDB.setLong(14, existingTitle.getId());
						try {
							updateHooplaTitleInDB.executeUpdate();

							try {
								String groupedWorkId = getRecordGroupingProcessor().groupHooplaRecord(curTitle, hooplaId);
								indexRecord(groupedWorkId);
							} catch (org.marc4j.MarcException e) {
								if (e.getMessage() != null && e.getMessage().contains("Cannot invoke \"String.length()\" because \"spec\" is null")) {
									// This is likely an issue with MARC tag 202 in Flex records
									logger.error("MARC conversion error with tag 202 when grouping Hoopla record " +
										hooplaId + ". This is a known issue with some Hoopla Flex records.", e);
									logEntry.addNote("MARC conversion error with tag 202 when grouping record " +
										hooplaId + ". The record was updated but metadata may be incomplete.");
								} else {
									// Some other MARC exception
									logEntry.incErrors("MARC exception when grouping Hoopla record " + hooplaId + ": " + e.getMessage());
								}
							}
						} catch (DataTruncation e) {
							logEntry.addNote("Record " + hooplaId + " " + curTitle.getString("title") + " contained invalid data " + e);
						} catch (SQLException e) {
							logEntry.incErrors("Error updating hoopla data in database for record " + hooplaId + " " + curTitle.getString("title"), e);
						}
					}
				}
			}catch (Exception e){
				logEntry.incErrors("Error updating hoopla " + type + " data", e);
			}
		}
		getGroupedWorkIndexer().commitChanges();
	}

	private static void indexRecord(String groupedWorkId) {
		try {
			getGroupedWorkIndexer().processGroupedWork(groupedWorkId);
		} catch (org.marc4j.MarcException e) {
			if (e.getMessage() != null && e.getMessage().contains("Cannot invoke \"String.length()\" because \"spec\" is null")) {
				// This is a known issue with MARC tag 202 in Hoopla Flex records
				logger.error("MARC conversion error with tag 202 when indexing grouped work " + groupedWorkId +
					". This is a known issue with some Hoopla Flex records.", e);
				logEntry.addNote("MARC conversion error with tag 202 when processing record. " +
					"The record was indexed but may have incomplete metadata.");
			} else {
				// Some other MARC exception
				logger.error("MARC exception when indexing grouped work " + groupedWorkId + " by id", e);
				logEntry.incErrors("Error indexing grouped work " + groupedWorkId + ": " + e.getMessage());
			}
		} catch (Exception e) {
			logger.error("Error indexing grouped work " + groupedWorkId + " by id", e);
			logEntry.incErrors("Error indexing grouped work " + groupedWorkId + ": " + e.getMessage());
		}
	}

	private static String getAccessToken(String username, String password) {
		if (username == null || password == null){
			logger.error("Please set HooplaAPIUser and HooplaAPIPassword in settings");
			logEntry.addNote("Please set HooplaAPIUser and HooplaAPIPassword in settings");
		} else {
			int numTries = 1;
			while (numTries <= 3) {
				numTries++;
				String getTokenUrl = hooplaAPIBaseURL + "/v2/token";
				WebServiceResponse response = NetworkUtils.postToURL(getTokenUrl, null, "application/json", null, logger, username + ":" + password);
				if (response.isSuccess()) {
					try {
						JSONObject responseJSON = new JSONObject(response.getMessage());
						return responseJSON.getString("access_token");
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
		}
		return null;
	}

	private static Connection connectToDatabase(){
		Connection aspenConn = null;
		try{
			String databaseConnectionInfo = ConfigUtil.cleanIniValue(configIni.get("Database", "database_aspen_jdbc"));
			if (databaseConnectionInfo != null) {
				aspenConn = DriverManager.getConnection(databaseConnectionInfo);
				getAllExistingHooplaItemsStmt = aspenConn.prepareStatement("SELECT id, hooplaId, rawChecksum, active, UNCOMPRESSED_LENGTH(rawResponse) as rawResponseLength, type from hoopla_export");
				addHooplaTitleToDB = aspenConn.prepareStatement("INSERT INTO hoopla_export (hooplaId, active, title, kind, pa, demo, profanity, rating, abridged, children, price, rawChecksum, rawResponse, dateFirstDetected, type) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,?,COMPRESS(?),?,?) ");
				updateHooplaTitleInDB = aspenConn.prepareStatement("UPDATE hoopla_export set active = ?, title = ?, kind = ?, pa = ?, demo = ?, profanity = ?, " +
					"rating = ?, abridged = ?, children = ?, price = ?, rawChecksum = ?, rawResponse = COMPRESS(?), type = ? where id = ?");
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
		PreparedStatement getAllRecordsToRegroupStmt = dbConn.prepareStatement("SELECT hooplaId, UNCOMPRESS(rawResponse) as rawResponse from hoopla_export where active = 1", ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
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
