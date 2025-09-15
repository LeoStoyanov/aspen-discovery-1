package com.turning_leaf_technologies.hoopla;

import java.sql.ResultSet;
import java.sql.SQLException;

class HooplaLibraryConfiguration {
	private final int libraryId;
	private final boolean enableFlex;
	private final boolean enableInstant;

	public HooplaLibraryConfiguration(ResultSet rs) throws SQLException {
		libraryId = rs.getInt("libraryId");
		enableFlex = rs.getBoolean("enableFlex");
		enableInstant = rs.getBoolean("enableInstant");
	}

	public int getLibraryId() {
		return libraryId;
	}

	public boolean isFlexEnabled() {
		return enableFlex;
	}

	public boolean isInstantEnabled() {
		return enableInstant;
	}

	public boolean isPurchaseModelEnabled(String purchaseModel) {
		if (purchaseModel == null) return true;
		switch (purchaseModel.toLowerCase()) {
			case "flex":
				return enableFlex;
			case "instant":
				return enableInstant;
			default:
				return true; // Unknown purchase models default to enabled
		}
	}
}