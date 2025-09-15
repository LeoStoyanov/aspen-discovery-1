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
	private final long lastUpdateOfGlobalContent;
	private final long lastUpdateOfEntitlements;

	private final boolean regroupAllRecords;

	public HooplaSettings(ResultSet settingsRS) throws SQLException {
		settingsId = settingsRS.getLong("id");
		apiUrl = settingsRS.getString("apiUrl");
		apiUsername = settingsRS.getString("apiUsername");
		apiPassword = settingsRS.getString("apiPassword");


		accessToken = settingsRS.getString("accessToken");
		tokenExpirationTime = settingsRS.getLong("tokenExpirationTime");

		lastUpdateOfGlobalContent = settingsRS.getLong("lastUpdateOfGlobalContent");
		lastUpdateOfEntitlements = settingsRS.getLong("lastUpdateOfEntitlements");

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

	public long getLastUpdateOfGlobalContent() {
		return lastUpdateOfGlobalContent;
	}

	public long getLastUpdateOfEntitlements() {
		return lastUpdateOfEntitlements;
	}
}
