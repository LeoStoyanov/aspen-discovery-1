package org.aspendiscovery.reading_history_export;

import com.turning_leaf_technologies.config.ConfigUtil;
import com.turning_leaf_technologies.encryption.EncryptionUtils;
import com.turning_leaf_technologies.file.JarUtil;
import com.turning_leaf_technologies.logging.BaseLogEntry;
import com.turning_leaf_technologies.logging.LoggingUtil;
import com.turning_leaf_technologies.net.NetworkUtils;
import com.turning_leaf_technologies.net.WebServiceResponse;
import com.turning_leaf_technologies.strings.AspenStringUtils;
import com.turning_leaf_technologies.util.SystemUtils;

import org.apache.logging.log4j.Logger;
import org.ini4j.Ini;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Date;
import java.util.concurrent.*;

/**
 * Main class for exporting reading history from Evergreen ILS
 * This process is needed because of PHP memory issues with loading full reading history
 */
public class ReadingHistoryExportMain {
    private static Logger logger;
    private static Connection dbConn;
    private static String serverName;
    private static String baseUrl;
    private static final SimpleDateFormat dateTimeFormatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
    private static final String processName = "reading_history_export";

    public static void main(String[] args) {
        if (args.length == 0) {
            serverName = AspenStringUtils.getInputFromCommandLine("Please enter the server name");
            if (serverName.isEmpty()) {
                System.out.println("You must provide the server name as the first argument.");
                System.exit(1);
            }
        } else {
            serverName = args[0];
        }

        String patronId = "";
        if (args.length > 1) {
            patronId = args[1];
        }
        if (patronId.isEmpty()) {
            patronId = AspenStringUtils.getInputFromCommandLine("Enter the patron ID to export reading history for");
            if (patronId.isEmpty()) {
                System.out.println("You must provide a patron ID.");
                System.exit(1);
            }
        }

        logger = LoggingUtil.setupLogging(serverName, processName);

        // Get the checksum of the JAR when it was started
        long myChecksumAtStart = JarUtil.getChecksumForJar(logger, processName, "./" + processName + ".jar");

        logger.error("Starting Evergreen Reading History Export for patron " + patronId);

        try {
            // Read the base INI file to get information about the server
            Ini configIni = ConfigUtil.loadConfigFile("config.ini", serverName, logger);

            //Connect to the Aspen Database
            String databaseConnectionInfo = ConfigUtil.cleanIniValue(configIni.get("Database", "database_aspen_jdbc"));
            if (databaseConnectionInfo == null) {
                logger.error("Please provide database_aspen_jdbc within config.pwd.ini");
                System.exit(1);
            }

            dbConn = DriverManager.getConnection(databaseConnectionInfo);
            if (dbConn == null) {
                logger.error("Could not establish connection to database at " + databaseConnectionInfo);
                System.exit(1);
            }

            // Check to see if the jar has changed before processing
            if (myChecksumAtStart != JarUtil.getChecksumForJar(logger, processName, "./" + processName + ".jar")) {
                disconnectDatabase();
                System.exit(0);
            }

            // Load account information
            String staffUsername;
            String staffPassword;
            String staffToken;

            PreparedStatement accountProfileStmt = dbConn.prepareStatement("SELECT * from account_profiles WHERE ils = 'evergreen'");
            ResultSet accountProfileRS = accountProfileStmt.executeQuery();
            if (accountProfileRS.next()) {
                baseUrl = accountProfileRS.getString("patronApiUrl");
                staffUsername = accountProfileRS.getString("staffUsername");
                staffPassword = accountProfileRS.getString("staffPassword");

                // Get auth token for API calls
                staffToken = getAPIAuthToken(staffUsername, staffPassword);
                if (staffToken == null) {
                    logger.error("Failed to get API auth token");
                    accountProfileRS.close();
                    disconnectDatabase();
                    System.exit(1);
                }
            } else {
                logger.error("Could not load Evergreen account profile");
                accountProfileRS.close();
                disconnectDatabase();
                System.exit(1);
            }
            accountProfileRS.close();

            // Load patron information
            PreparedStatement patronStmt = dbConn.prepareStatement("SELECT * FROM user WHERE ils_barcode = ?");
            patronStmt.setString(1, patronId);
            ResultSet patronRS = patronStmt.executeQuery();
            if (!patronRS.next()) {
                logger.error("Could not find patron with ID " + patronId);
                patronRS.close();
                disconnectDatabase();
                System.exit(1);
            }

            // Get the actual user ID from the database to use in all operations
            String actualUserId = patronRS.getString("id");
            String patronBarcode = patronRS.getString("ils_barcode");
            String patronPassword = null;
            try{
                patronPassword = EncryptionUtils.decryptString(patronRS.getString("ils_password"), serverName, null);
            }catch (Exception e){
                logger.error("Could not decrypt password for " + patronBarcode + " " + e);
            }
            boolean trackReadingHistory = patronRS.getBoolean("trackReadingHistory");
            patronRS.close();

            if (!trackReadingHistory) {
                logger.error("Patron " + patronId + " is not opted into reading history tracking");
                disconnectDatabase();
                System.exit(0);
            }

            // Get patron's auth token
            String patronAuthToken = getAPIAuthToken(patronBarcode, patronPassword);
            if (patronAuthToken == null) {
                logger.error("Failed to get API auth token for patron " + patronId);
                disconnectDatabase();
                System.exit(1);
            }

            // Process reading history
            logger.error("Starting to export reading history for patron " + patronId);
            int processedItems = exportReadingHistory(actualUserId, patronAuthToken);
            logger.error("Finished exporting reading history for patron " + patronId + ". Total items processed: " + processedItems);

            // Mark reading history as loaded
            PreparedStatement updatePatronStmt = dbConn.prepareStatement("UPDATE user SET initialReadingHistoryLoaded = 1 WHERE id = ?");
            updatePatronStmt.setString(1, actualUserId);
            updatePatronStmt.executeUpdate();
            updatePatronStmt.close();

            disconnectDatabase();
        } catch (Exception e) {
            logger.error("Error processing reading history", e);
            System.exit(1);
        }
    }

    /**
     * Exports reading history for a patron from Evergreen and stores it in Aspen Discovery
     *
     * @param userId The ID of the patron to export reading history for
     * @param authToken The authentication token for the patron
     * @return Number of history items processed
     */
    private static int exportReadingHistory(String userId, String authToken) throws Exception {
        int processedItems = 0;
        int offset = 0;
        int limit = 100;
        boolean hasMoreHistory = true;

        // First, clear any existing reading history entries if needed
        PreparedStatement checkExistingEntriesStmt = dbConn.prepareStatement(
                "SELECT COUNT(*) as count FROM user_reading_history_work WHERE userId = ? AND deleted = 0");
        checkExistingEntriesStmt.setString(1, userId);
        ResultSet existingEntriesRS = checkExistingEntriesStmt.executeQuery();
        boolean hasExistingEntries = existingEntriesRS.next() && existingEntriesRS.getInt("count") > 0;
        existingEntriesRS.close();
        checkExistingEntriesStmt.close();

        if (hasExistingEntries) {
            // We already have entries, so no need to re-import
            logger.error("Patron " + userId + " already has reading history entries");
            return 0;
        }

        // Prepare statements for database operations
        PreparedStatement insertHistoryEntryStmt = dbConn.prepareStatement(
                "INSERT INTO user_reading_history_work (userId, groupedWorkPermanentId, source, sourceId, title, author, format, checkOutDate, checkInDate, isIll, deleted) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)");

        while (hasMoreHistory) {
            logger.error("Getting reading history batch from offset " + offset);

            // Get a batch of reading history items
            String evergreenUrl = baseUrl + "/osrf-gateway-v1";
            String params = "service=open-ils.actor" +
                    "&method=open-ils.actor.history.circ" +
                    "&param=\"" + authToken + "\"" +
                    "&param={\"offset\":" + offset + ",\"limit\":" + limit + "}";

            logger.error("Reading History API URL: " + evergreenUrl);
            logger.error("Reading History params: " + params);

            WebServiceResponse webServiceResponse = callEvergreenAPI(evergreenUrl, params);
            if (webServiceResponse == null || !webServiceResponse.isSuccess()) {
                logger.error("Failed to get reading history from Evergreen API");
                break;
            }

            try {
                String responseBody = webServiceResponse.getMessage();
                logger.error("API Response: " + responseBody);
                JSONObject responseObj = new JSONObject(responseBody);

                if (!responseObj.has("payload")) {
                    logger.error("No payload in response, ending history fetch");
                    break;
                }

                JSONArray historyItems = responseObj.getJSONArray("payload");
                logger.error("Found " + historyItems.length() + " history items in response");

                if (historyItems.isEmpty()) {
                    hasMoreHistory = false;
                    logger.error("No more history items found");
                    break;
                }

                // Process each history item
                for (int i = 0; i < historyItems.length(); i++) {
                    JSONObject historyItem = historyItems.getJSONObject(i);
                    logger.error("History item " + i + ": " + historyItem.toString());
                    processHistoryItem(userId, historyItem, insertHistoryEntryStmt);
                    processedItems++;
                }

                // If we got fewer items than the limit, we've reached the end
                if (historyItems.length() < limit) {
                    hasMoreHistory = false;
                } else {
                    offset += limit;
                }

            } catch (JSONException e) {
                logger.error("Error parsing JSON from Evergreen API", e);
                break;
            }
        }

        insertHistoryEntryStmt.close();
        return processedItems;
    }

    /**
     * Processes a single reading history item and inserts it into the database
     */
    private static void processHistoryItem(String userId, JSONObject historyItem, PreparedStatement insertStmt) throws SQLException {
        try {
            String recordId = null;
            if (historyItem.has("target_biblio_record_entry")) {
                recordId = historyItem.getString("target_biblio_record_entry");
                logger.error("Found record ID: " + recordId);
            } else {
                logger.error("Missing target_biblio_record_entry field in history item. Available fields: " + String.join(", ", JSONObject.getNames(historyItem)));
            }

            if (recordId == null || recordId.isEmpty()) {
                logger.error("Skipping history item with no record ID");
                return;
            }

            String permanentId = null;
            String title = historyItem.optString("title", "Unknown Title");
            String author = historyItem.optString("author", "Unknown Author");
            String format = historyItem.optString("format", "");

            // Get checkout and check-in dates
            long checkoutTimestamp = 0;
            if (historyItem.has("xact_start")) {
                String checkoutDateStr = historyItem.getString("xact_start");
                Date checkoutDate = dateTimeFormatter.parse(checkoutDateStr);
                checkoutTimestamp = checkoutDate.getTime() / 1000;
            }

            Long checkinTimestamp = null;
            if (historyItem.has("xact_finish") && !historyItem.isNull("xact_finish")) {
                String checkinDateStr = historyItem.getString("xact_finish");
                Date checkinDate = dateTimeFormatter.parse(checkinDateStr);
                checkinTimestamp = checkinDate.getTime() / 1000;
            }

            // Get additional data from records table if available
            PreparedStatement getRecordDetailsStmt = dbConn.prepareStatement(
                    "SELECT permanent_id FROM grouped_work_records " +
                            "LEFT JOIN grouped_work ON groupedWorkId = grouped_work.id " +
                            "LEFT JOIN indexed_record_source ON sourceId = indexed_record_source.id " +
                            "WHERE source = 'evergreen' AND recordIdentifier = ?");
            getRecordDetailsStmt.setString(1, recordId);
            ResultSet recordDetailsRS = getRecordDetailsStmt.executeQuery();
            if (recordDetailsRS.next()) {
                permanentId = recordDetailsRS.getString("permanent_id");
            }
            recordDetailsRS.close();
            getRecordDetailsStmt.close();

            // Insert into database
            insertStmt.setString(1, userId);
            insertStmt.setString(2, permanentId);
            insertStmt.setString(3, "evergreen");
            insertStmt.setString(4, recordId);
            insertStmt.setString(5, title.length() > 150 ? title.substring(0, 150) : title);
            insertStmt.setString(6, author.length() > 75 ? author.substring(0, 75) : author);
            insertStmt.setString(7, format);
            insertStmt.setLong(8, checkoutTimestamp);
            if (checkinTimestamp != null) {
                insertStmt.setLong(9, checkinTimestamp);
            } else {
                insertStmt.setNull(9, Types.BIGINT);
            }
            insertStmt.setInt(10, 0); // isIll

            insertStmt.executeUpdate();

        } catch (Exception e) {
            logger.error("Error processing history item", e);
        }
    }

    /**
     * Calls the Evergreen API with the given URL and parameters
     */
    private static WebServiceResponse callEvergreenAPI(String url, String params) {
        try {
            return NetworkUtils.postToURL(url, params, "application/x-www-form-urlencoded", null, logger);
        } catch (Exception e) {
            logger.error("Error calling Evergreen API", e);
            return null;
        }
    }

    /**
     * Gets an authentication token for the Evergreen API
     */
    public static String getAPIAuthToken(String username, String password) {
        try {
            String evergreenUrl = baseUrl + "/osrf-gateway-v1";
            String authType = "persist"; // For patron access (vs. "persist" for staff access)
            // URL encode parameters as done in EvergreenExportMain
            String params = "service=open-ils.auth&method=open-ils.auth.login&param=%7B%22password%22%3A%22" + password +
                    "%22%2C%22type%22%3A%22" + authType + "%22%2C%22org%22%3Anull%2C%22identifier%22%3A%22" + username + "%22%7D";

            WebServiceResponse response = NetworkUtils.postToURL(evergreenUrl, params, "application/x-www-form-urlencoded", null, logger);
            if (response.isSuccess()){
                String responseString = response.getMessage();
                logger.error("Auth response: " + responseString);
                JSONObject authData = response.getJSONResponse();
                if (authData.has("payload")){
                    JSONArray mainPayload = authData.getJSONArray("payload");
                    for (int i = 0; i < mainPayload.length(); i++){
                        JSONObject mainPayloadObject = mainPayload.getJSONObject(i);
                        if (mainPayloadObject.has("payload")){
                            JSONObject subPayload = mainPayloadObject.getJSONObject("payload");
                            //noinspection SpellCheckingInspection
                            if (subPayload.has("authtoken")){
                                //noinspection SpellCheckingInspection
                                return subPayload.getString("authtoken");
                            }
                        }
                    }
                    logger.error("No authtoken found in response payload");
                } else {
                    logger.error("No payload in auth response");
                }
            } else {
                logger.error("Auth request failed with code: " + response.getResponseCode());
            }

            return null;
        } catch (Exception e) {
            logger.error("Error getting API auth token", e);
            return null;
        }
    }

    /**
     * Disconnects from the database
     */
    private static void disconnectDatabase() {
        try {
            if (dbConn != null) {
                dbConn.close();
                dbConn = null;
            }
        } catch (Exception e) {
            logger.error("Error disconnecting from database", e);
        }
    }
}