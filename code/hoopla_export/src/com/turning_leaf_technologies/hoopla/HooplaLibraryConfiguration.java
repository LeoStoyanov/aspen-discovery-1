package com.turning_leaf_technologies.hoopla;

import java.sql.ResultSet;
import java.sql.SQLException;

class HooplaLibraryConfiguration {
	private final int libraryId;
	private final int hooplaLibraryId;
	private final boolean enableFlex;
	private final boolean enableInstant;
	private final boolean runFullEntitlementsUpdate;
	private final boolean clearDisabledFlex;
	private final boolean clearDisabledInstant;

	public HooplaLibraryConfiguration(ResultSet rs) throws SQLException {
		libraryId = rs.getInt("libraryId");
		hooplaLibraryId = rs.getInt("hooplaLibraryId");
		enableFlex = rs.getBoolean("enableFlex");
		enableInstant = rs.getBoolean("enableInstant");
		runFullEntitlementsUpdate = rs.getBoolean("runFullEntitlementsUpdate");
		clearDisabledFlex = rs.getBoolean("clearDisabledFlex");
		clearDisabledInstant = rs.getBoolean("clearDisabledInstant");
	}

	public int getLibraryId() {
		return libraryId;
	}

	public int getHooplaLibraryId() {
		return hooplaLibraryId;
	}

	public boolean isFlexEnabled() {
		return enableFlex;
	}

	public boolean isInstantEnabled() {
		return enableInstant;
	}

	public boolean isRunFullEntitlementsUpdate() {
		return runFullEntitlementsUpdate;
	}

	public boolean isClearDisabledFlex() {
		return clearDisabledFlex;
	}

	public boolean isClearDisabledInstant() {
		return clearDisabledInstant;
	}

	public boolean isPurchaseModelEnabled(String purchaseModel) {
		if (purchaseModel == null) return true;
		switch (purchaseModel.toUpperCase()) {
			case "EST": // Flex purchase model
			case "FLEX":
				return enableFlex;
			case "PPU": // Instant purchase model
			case "INSTANT":
				return enableInstant;
			default:
				return true; // Unknown purchase models default to enabled
		}
	}
}
