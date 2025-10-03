package com.turning_leaf_technologies.hoopla;

import java.sql.ResultSet;
import java.sql.SQLException;

class HooplaSettings {
	private final long settingsId;
	private final String apiUrl;
	private final String apiUsername;
	private final String apiPassword;


	// Token settings
	private final String accessToken;
	private final long tokenExpirationTime;

	// New API endpoint tracking
	private final long lastUpdateOfChangedRecords;
	private final long lastRecordProcessed;

	private final String countryCode;

	private final boolean regroupAllRecords;

	public HooplaSettings(ResultSet settingsRS) throws SQLException {
		settingsId = settingsRS.getLong("id");
		apiUrl = settingsRS.getString("apiUrl");
		apiUsername = settingsRS.getString("apiUsername");
		apiPassword = settingsRS.getString("apiPassword");


		accessToken = settingsRS.getString("accessToken");
		tokenExpirationTime = settingsRS.getLong("tokenExpirationTime");

		lastUpdateOfChangedRecords = settingsRS.getLong("lastUpdateOfChangedRecords");
		lastRecordProcessed = settingsRS.getLong("lastRecordProcessed");

		String tmpCountryCode = settingsRS.getString("countryCode");
		countryCode = (tmpCountryCode != null && !tmpCountryCode.isEmpty()) ? tmpCountryCode : "US";

		regroupAllRecords = settingsRS.getBoolean("regroupAllRecords");
	}

	public long getSettingsId() {
		return settingsId;
	}

	public String getApiUrl() {
		return apiUrl;
	}


	public String getApiUsername() {
		return apiUsername;
	}

	public String getApiPassword() {
		return apiPassword;
	}


	public String getAccessToken() {
		return accessToken;
	}

	public long getTokenExpirationTime() {
		return tokenExpirationTime;
	}

	public boolean isRegroupAllRecords() {
		return regroupAllRecords;
	}

	public long getLastUpdateOfChangedRecords() {
		return lastUpdateOfChangedRecords;
	}

	public long getLastRecordProcessed() {
		return lastRecordProcessed;
	}

	public String getCountryCode() {
		return countryCode;
	}
}
