package org.aspen_discovery.reindexer;

import com.turning_leaf_technologies.indexing.HooplaScope;
import com.turning_leaf_technologies.indexing.Scope;
import com.turning_leaf_technologies.logging.BaseIndexingLogEntry;
import com.turning_leaf_technologies.strings.AspenStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;

class HooplaProcessor {
	private final GroupedWorkIndexer indexer;
	private final Logger logger;
	private final Connection dbConn;

	private PreparedStatement getProductInfoStmt;
	private PreparedStatement doubleDecodeRawResponseStmt;
	private PreparedStatement updateRawResponseStmt;
	private PreparedStatement getFlexAvailabilityStmt;
	private PreparedStatement checkEntitlementStmt;
	private PreparedStatement hasAnyEntitlementsStmt;
	HooplaProcessor(GroupedWorkIndexer indexer, Connection dbConn, Logger logger) {
		this.indexer = indexer;
		this.logger = logger;
		this.dbConn = dbConn;

		try {
			getProductInfoStmt = dbConn.prepareStatement("SELECT id, hooplaId, title, kind, pa, demo, profanity, rating, abridged, children, price, rawChecksum, UNCOMPRESS(rawResponse) as rawResponse, dateFirstDetected, hooplaType from hoopla_export where hooplaId = ?", ResultSet.TYPE_FORWARD_ONLY,  ResultSet.CONCUR_READ_ONLY);
			doubleDecodeRawResponseStmt = dbConn.prepareStatement("SELECT UNCOMPRESS(UNCOMPRESS(rawResponse)) as rawResponse from hoopla_export where id = ?", ResultSet.TYPE_FORWARD_ONLY,  ResultSet.CONCUR_READ_ONLY);
			updateRawResponseStmt = dbConn.prepareStatement("UPDATE hoopla_export SET rawResponse = COMPRESS(?) where id = ?");
			getFlexAvailabilityStmt = dbConn.prepareStatement("SELECT * from hoopla_flex_availability where hooplaId = ? AND libraryId = ?", ResultSet.TYPE_FORWARD_ONLY,  ResultSet.CONCUR_READ_ONLY);
			checkEntitlementStmt = dbConn.prepareStatement("SELECT purchaseModel FROM hoopla_entitlements WHERE hooplaId = ? AND libraryId = ? AND active = 1", ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
			hasAnyEntitlementsStmt = dbConn.prepareStatement("SELECT 1 FROM hoopla_entitlements WHERE hooplaId = ? AND active = 1 LIMIT 1", ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
		} catch (SQLException e) {
			logger.error("Error setting up hoopla processor", e);
		}
	}

	void processRecord(AbstractGroupedWorkSolr groupedWork, String identifier, BaseIndexingLogEntry logEntry) {
		try {
			getProductInfoStmt.setString(1, identifier);
			ResultSet productRS = getProductInfoStmt.executeQuery();
			if (productRS.next()) {
				// Check if this title has any active entitlements
				// If no library has entitlements for this title, don't bother processing it
				hasAnyEntitlementsStmt.setString(1, identifier);
				ResultSet hasEntitlementsRS = hasAnyEntitlementsStmt.executeQuery();
				boolean hasActiveEntitlements = hasEntitlementsRS.next();
				hasEntitlementsRS.close();

				if (!hasActiveEntitlements) {
					if (logger.isDebugEnabled()) {
						logger.debug("Hoopla product " + identifier + " has no active entitlements, skipping");
					}
					return;
				}
				byte[] rawResponseBytes = productRS.getBytes("rawResponse");
				if (rawResponseBytes == null){
					logEntry.incErrors("rawResponse for Hoopla title " + identifier + " was null skipping");
					return;
				}
				String kind = productRS.getString("kind");
				float price = productRS.getFloat("price");
				String hooplaType = productRS.getString("hooplaType");

				RecordInfo hooplaRecord = groupedWork.addRelatedRecord("hoopla", identifier);
				hooplaRecord.setRecordIdentifier("hoopla", identifier);

				String title = productRS.getString("title");
				String subTitle = "";

				String formatCategory;
				String primaryFormat;
				switch (kind) {
					case "MOVIE":
					case "TELEVISION":
						formatCategory = "Movies";
						primaryFormat = "eVideo";
						break;
					case "AUDIOBOOK":
						formatCategory = "Audio Books";
						hooplaRecord.addFormatCategory("eBook");
						primaryFormat = "eAudiobook";
						break;
					case "EBOOK":
						formatCategory = "eBook";
						primaryFormat = "eBook";
						break;
					case "COMIC":
						formatCategory = "eBook";
						primaryFormat = "eComic";
						break;
					case "MUSIC":
						formatCategory = "Music";
						primaryFormat = "eMusic";
						break;
					case "BINGEPASS":
						formatCategory = "Other";
						primaryFormat = "Binge Pass";
						break;
					default:
						logger.error("Unhandled hoopla kind " + kind);
						formatCategory = kind;
						primaryFormat = kind;
						break;
				}
				if (groupedWork.isDebugEnabled()) {groupedWork.addDebugMessage("Format is " + primaryFormat + " based on kind of " + kind, 2);}

				hooplaRecord.addFormat(primaryFormat);
				hooplaRecord.addFormatCategory(formatCategory);

				String rawResponseString = new String(rawResponseBytes, StandardCharsets.UTF_8);
				if (rawResponseString.charAt(0) != '{' || rawResponseString.charAt(rawResponseString.length() -1) != '}'){
					//If the first char is not { check to see if it has been double encoded
					rawResponseString = fixHooplaData(productRS.getLong("id"));
					if (rawResponseString == null){
						logEntry.incErrors("Could not read or correct Hoopla raw response for " + identifier);
					}
				}
				JSONObject rawResponse = new JSONObject(rawResponseString);

				if (rawResponse.has("titleTitle")){
					title = rawResponse.getString("titleTitle");
					subTitle = rawResponse.getString("title");
				}else if (rawResponse.has("subtitle")){
					subTitle = rawResponse.getString("subtitle");
				}

				String fullTitle = title + " " + subTitle;
				fullTitle = fullTitle.trim();
				String sortableTitle = AspenStringUtils.makeValueSortable(title);
				groupedWork.setTitle(title, subTitle, title, sortableTitle, primaryFormat, formatCategory);
				groupedWork.addFullTitle(fullTitle);


				String primaryAuthor = "";
				if (rawResponse.has("artist")){
					primaryAuthor = rawResponse.getString("artist");
					//Don't swap artist names for music since these are typically group names.
					if (!kind.equals("MUSIC")) {
						primaryAuthor = AspenStringUtils.swapFirstLastNames(primaryAuthor);
					}
				}else if (rawResponse.has("publisher")){
					primaryAuthor = rawResponse.getString("publisher");
				}
				groupedWork.setAuthor(primaryAuthor);
				groupedWork.setAuthAuthor(primaryAuthor);
				groupedWork.setAuthorDisplay(primaryAuthor, formatCategory);

				if (rawResponse.has("series")){
					String series = rawResponse.getString("series");
					groupedWork.addSeries(series);
					String volume = "";
					if (rawResponse.has("episode")){
						volume = rawResponse.get("episode").toString();
					}
					groupedWork.addSeriesWithVolume(series, volume, 2);
				}

				boolean children = rawResponse.getBoolean("children");
				boolean isAdult = false;
				boolean isTeen = false;
				boolean isKids = false;
				if (children){
					isKids = true;
					groupedWork.addTargetAudience("Juvenile");
					groupedWork.addTargetAudienceFull("Juvenile");
					if (groupedWork != null && groupedWork.isDebugEnabled()) {groupedWork.addDebugMessage("Target/full target audience is Juvenile based on Hoopla record", 2);}
				}else {
					//Todo: Also check the genres (Children's, Teen
					boolean foundAudience = false;
					if (rawResponse.has("genres")) {
						JSONArray genres = rawResponse.getJSONArray("genres");
						for (int i = 0; i < genres.length(); i++) {
							if (genres.getString(i).equals("Teen")) {
								isTeen = true;
								groupedWork.addTargetAudience("Young Adult");
								groupedWork.addTargetAudienceFull("Adolescent (14-17)");
								if (groupedWork != null && groupedWork.isDebugEnabled()) {groupedWork.addDebugMessage("Target audience is Young Adult based on Hoopla genre", 2);}
								if (groupedWork != null && groupedWork.isDebugEnabled()) {groupedWork.addDebugMessage("Full target audience is Adolescent (14-17) based on Hoopla genre", 2);}
								foundAudience = true;
							} else if (genres.getString(i).startsWith("Young Adult")) {
								isTeen = true;
								groupedWork.addTargetAudience("Young Adult");
								groupedWork.addTargetAudienceFull("Adolescent (14-17)");
								if (groupedWork != null && groupedWork.isDebugEnabled()) {groupedWork.addDebugMessage("Target audience is Young Adult based on Hoopla genre", 2);}
								if (groupedWork != null && groupedWork.isDebugEnabled()) {groupedWork.addDebugMessage("Full target audience is Adolescent (14-17) based on Hoopla genre", 2);}
								foundAudience = true;
							} else if (genres.getString(i).equals("Children's")) {
								isKids = true;
								groupedWork.addTargetAudience("Juvenile");
								groupedWork.addTargetAudienceFull("Juvenile");
								if (groupedWork != null && groupedWork.isDebugEnabled()) {groupedWork.addDebugMessage("Target/full target audience is Juvenile based on Hoopla genre", 2);}
								foundAudience = true;
							} else if (genres.getString(i).equals("Adult")) {
								isAdult = true;
								groupedWork.addTargetAudience("Adult");
								groupedWork.addTargetAudienceFull("Adult");
								if (groupedWork != null && groupedWork.isDebugEnabled()) {groupedWork.addDebugMessage("Target/full target audience is Adult based on Hoopla genre", 2);}
								foundAudience = true;
							}
						}
					}

					if (!foundAudience && rawResponse.has("rating")) {
						String rating = rawResponse.getString("rating");
						//noinspection SpellCheckingInspection
						if (rating.equals("TVMA") || rating.equals("M") || rating.equals("NC17")) {
							isAdult = true;
							groupedWork.addTargetAudience("Adult");
							groupedWork.addTargetAudienceFull("Adult");
							if (groupedWork != null && groupedWork.isDebugEnabled()) {groupedWork.addDebugMessage("Target/full target audience is Adult based on Hoopla rating", 2);}
						} else {
							if (kind.equals("MOVIE") || kind.equals("TELEVISION")) {
								switch (rating) {
									case "R":
									case "NR":
									case "NRA":
									case "NRM":
									case "NC-17":
										isAdult = true;
										groupedWork.addTargetAudience("Adult");
										groupedWork.addTargetAudienceFull("Adult");
										if (groupedWork != null && groupedWork.isDebugEnabled()) {groupedWork.addDebugMessage("Target/full target audience is Adult based on Hoopla rating " + rating, 2);}
										break;
									case "PG-13":
									case "PG13":
									case "PG":
										//noinspection SpellCheckingInspection
									case "TVPG":
									case "TV14":
									case "NRT":
										isAdult = true;
										isTeen = true;
										groupedWork.addTargetAudience("Young Adult");
										groupedWork.addTargetAudienceFull("Adolescent (14-17)");
										if (groupedWork != null && groupedWork.isDebugEnabled()) {groupedWork.addDebugMessage("Target audience is Young Adult based on Hoopla rating " + rating, 2);}
										if (groupedWork != null && groupedWork.isDebugEnabled()) {groupedWork.addDebugMessage("Full target audience is Adolescent (14-17) based on Hoopla rating " + rating, 2);}
										groupedWork.addTargetAudience("Adult");
										groupedWork.addTargetAudienceFull("Adult");
										if (groupedWork != null && groupedWork.isDebugEnabled()) {groupedWork.addDebugMessage("Target/full target audience is Adult based on Hoopla rating " + rating, 2);}
										break;
									case "TVY":
									case "TVY7":
									case "NRC":
										isKids = true;
										groupedWork.addTargetAudience("Juvenile");
										groupedWork.addTargetAudienceFull("Juvenile");
										if (groupedWork != null && groupedWork.isDebugEnabled()) {groupedWork.addDebugMessage("Target/full target audience is Juvenile based on Hoopla rating " + rating, 2);}
										break;
									case "TVG":
									case "G":
										isKids = true;
										isTeen = true;
										isAdult = true;
										groupedWork.addTargetAudience("General");
										groupedWork.addTargetAudienceFull("General");
										if (groupedWork != null && groupedWork.isDebugEnabled()) {groupedWork.addDebugMessage("Target/full target audience is General based on Hoopla rating " + rating, 2);}
										break;
									default:
										//todo, do we want to add additional ratings here?
										logger.debug("rating " + rating);
										break;
								}
							} else if (kind.equals("COMIC")) {
								switch (rating) {
									case "E":
										isKids = true;
										groupedWork.addTargetAudience("Juvenile");
										groupedWork.addTargetAudienceFull("Juvenile");
										if (groupedWork != null && groupedWork.isDebugEnabled()) {groupedWork.addDebugMessage("Target/full target audience is Juvenile based on Hoopla rating " + rating, 2);}
										break;
									case "PA":
									case "EX":
										isAdult = true;
										groupedWork.addTargetAudience("Adult");
										groupedWork.addTargetAudienceFull("Adult");
										if (groupedWork != null && groupedWork.isDebugEnabled()) {groupedWork.addDebugMessage("Target/full target audience is Adult based on Hoopla rating " + rating, 2);}
										break;
									case "T":
										isTeen = true;
										groupedWork.addTargetAudience("Young Adult");
										groupedWork.addTargetAudienceFull("Adolescent (14-17)");
										if (groupedWork != null && groupedWork.isDebugEnabled()) {groupedWork.addDebugMessage("Target audience is Young Adult based on Hoopla rating " + rating, 2);}
										if (groupedWork != null && groupedWork.isDebugEnabled()) {groupedWork.addDebugMessage("Full target audience is Adolescent (14-17) based on Hoopla rating " + rating, 2);}
										break;
									case "T+":
									default:
										isAdult = true;
										isTeen = true;
										groupedWork.addTargetAudience("Young Adult");
										groupedWork.addTargetAudienceFull("Adolescent (14-17)");
										if (groupedWork != null && groupedWork.isDebugEnabled()) {groupedWork.addDebugMessage("Target audience is Young Adult based on Hoopla rating " + rating, 2);}
										if (groupedWork != null && groupedWork.isDebugEnabled()) {groupedWork.addDebugMessage("Full target audience is Adolescent (14-17) based on Hoopla rating " + rating, 2);}
										groupedWork.addTargetAudience("Adult");
										groupedWork.addTargetAudienceFull("Adult");
										if (groupedWork != null && groupedWork.isDebugEnabled()) {groupedWork.addDebugMessage("Target/full target audience is Adult based on Hoopla rating " + rating, 2);}
								}

							} else {
								isAdult = true;
								isTeen = true;
								groupedWork.addTargetAudience("Young Adult");
								groupedWork.addTargetAudienceFull("Adolescent (14-17)");
								groupedWork.addTargetAudience("Adult");
								groupedWork.addTargetAudienceFull("Adult");
								if (groupedWork != null && groupedWork.isDebugEnabled()) {groupedWork.addDebugMessage("Target audience is Young Adult based on Hoopla rating " + rating, 2);}
								if (groupedWork != null && groupedWork.isDebugEnabled()) {groupedWork.addDebugMessage("Full target audience is Adolescent (14-17) based on Hoopla rating " + rating, 2);}
								if (groupedWork != null && groupedWork.isDebugEnabled()) {groupedWork.addDebugMessage("Target/full target audience is Adult based on Hoopla rating " + rating, 2);}
							}
						}
					} else if (!foundAudience) {
						isAdult = true;
						groupedWork.addTargetAudience("Adult");
						groupedWork.addTargetAudienceFull("Adult");
						if (groupedWork != null && groupedWork.isDebugEnabled()) {groupedWork.addDebugMessage("Target/full target audience is Adult based on Hoopla record", 2);}
					}
				}

				String language = rawResponse.getString("language");
				language = StringUtils.capitalize(language.toLowerCase());
				hooplaRecord.setPrimaryLanguage(language);
				groupedWork.addLanguage(language);
				if (language.equalsIgnoreCase("English")){
					groupedWork.setLanguageBoost(10L);
				}else if (language.equalsIgnoreCase("Spanish")){
					groupedWork.setLanguageBoostSpanish(10L);
				}
				long formatBoost = 1;
				try {
					formatBoost = Long.parseLong(indexer.translateSystemValue("format_boost_hoopla", primaryFormat, identifier));
				} catch (Exception e) {
					logger.warn("Could not translate format boost for " + primaryFormat + " create translation map format_boost_hoopla");
				}
				hooplaRecord.setFormatBoost(formatBoost);
				if (rawResponse.has("artists")) {
					JSONArray artists = rawResponse.getJSONArray("artists");
					HashSet<String> artistsToAdd = new HashSet<>();
					HashSet<String> artistsWithRoleToAdd = new HashSet<>();
					for (int i = 0; i < artists.length(); i++) {
						JSONObject curArtist = artists.getJSONObject(i);
						String artistName = AspenStringUtils.swapFirstLastNames(curArtist.getString("name"));
						artistsToAdd.add(artistName);
						artistsWithRoleToAdd.add(artistName + "|" + StringUtils.capitalize(curArtist.getString("relationship").toLowerCase()));
					}
					groupedWork.addAuthor2(artistsToAdd);
					groupedWork.addAuthor2Role(artistsWithRoleToAdd);
					groupedWork.addKeywords(artistsToAdd);
				}

				JSONArray genres = rawResponse.getJSONArray("genres");
				HashSet<String> genresToAdd = new HashSet<>();
				HashSet<String> topicsToAdd = new HashSet<>();
				for (int i = 0; i < genres.length(); i++) {
					String genre = genres.getString(i);

					genresToAdd.add(genre);
					topicsToAdd.add(genre);
				}
				groupedWork.addGenre(genresToAdd);
				groupedWork.addGenreFacet(genresToAdd);
				groupedWork.addTopicFacet(topicsToAdd);
				groupedWork.addTopic(topicsToAdd);

				HashMap<String, Integer> literaryForm = new HashMap<>();
				HashMap<String, Integer> literaryFormFull = new HashMap<>();
				if (rawResponse.has("fiction")){
					if (rawResponse.getBoolean("fiction")){
						Util.addToMapWithCount(literaryForm, "Fiction");
						Util.addToMapWithCount(literaryFormFull, "Fiction");
						if (groupedWork != null && groupedWork.isDebugEnabled()) {groupedWork.addDebugMessage("Literary Form is fiction based on Hoopla record", 2);}
					}else{
						Util.addToMapWithCount(literaryForm, "Non Fiction");
						Util.addToMapWithCount(literaryFormFull, "Non Fiction");
						if (groupedWork != null && groupedWork.isDebugEnabled()) {groupedWork.addDebugMessage("Literary Form is non fiction based on Hoopla record", 2);}
					}
				}
				if (!literaryForm.isEmpty()){
					groupedWork.addLiteraryForms(literaryForm);
				}
				if (!literaryFormFull.isEmpty()){
					groupedWork.addLiteraryFormsFull(literaryFormFull);
				}

				String publisher = rawResponse.getString("publisher");
				groupedWork.addPublisher(publisher);
				//publication date
				Object yearObj = rawResponse.get("year");
				String releaseYear = yearObj.toString();

				groupedWork.addPublicationDate(releaseYear);
				//physical description
				if (rawResponse.has("duration")){
					groupedWork.addPhysical(rawResponse.getString("duration"));
				}

				//Description
				if (rawResponse.has("synopsis")) {
					String description = rawResponse.getString("synopsis");
					groupedWork.addDescription(description, formatCategory);
				}

				String isbn = rawResponse.getString("isbn");
				groupedWork.addIsbn(isbn, primaryFormat);

				String upc = rawResponse.getString("upc");
				groupedWork.addUpc(upc);

				boolean abridged = productRS.getBoolean("abridged");
				boolean pa = productRS.getBoolean("pa");
				boolean profanity = productRS.getBoolean("profanity");
				String rating = productRS.getString("rating");
				Date dateAdded = new Date(productRS.getLong("dateFirstDetected") * 1000);

				// For Flex titles, create separate ItemInfo per library to support per-library availability
				// For Instant titles, create single ItemInfo since availability is global
				if (hooplaType.equalsIgnoreCase("Flex")) {
					// Get all libraries that have entitlements for this Flex title
					HashSet<Long> entitledLibraries = getEntitledLibraries(identifier);

					for (Long libraryId : entitledLibraries) {
						ItemInfo itemInfo = new ItemInfo();
						itemInfo.setItemIdentifier(identifier + ":" + libraryId + ":" + primaryFormat);
						itemInfo.seteContentSource("Hoopla");
						itemInfo.setIsEContent(true);
						itemInfo.seteContentUrl(rawResponse.getString("url"));
						itemInfo.setShelfLocation("Online Hoopla Collection");
						itemInfo.setDetailedLocation("Online Hoopla Collection");
						itemInfo.setCallNumber("Online Hoopla");
						itemInfo.setSortableCallNumber("Online Hoopla");
						itemInfo.setFormat(primaryFormat);
						itemInfo.setFormatCategory(formatCategory);
						itemInfo.setInLibraryUseOnly(false);
						itemInfo.setDateAdded(dateAdded);

						// Set per-library Flex availability
						setFlexAvailabilityForLibrary(itemInfo, identifier, libraryId);

						// Scope this ItemInfo only to the relevant library's scopes
						for (Scope scope : indexer.getScopes()) {
							boolean okToAdd = false;
							HooplaScope hooplaScope = scope.getHooplaScope();
							if (hooplaScope != null && libraryId.equals(scope.getLibraryId())) {
								// Check entitlement and get purchase model for this library
								String libraryPurchaseModel = checkEntitlementAndGetPurchaseModel(identifier, libraryId, hooplaType, groupedWork, scope);
								if (libraryPurchaseModel != null) {
									// Check if the content passes the scoping rules
									okToAdd = hooplaScope.isOkToAdd(identifier, kind, price, abridged, pa, profanity, isAdult, isTeen, isKids, rating, genresToAdd, libraryPurchaseModel, logger);
								}
							}

							if (okToAdd) {
								ScopingInfo scopingInfo = itemInfo.addScope(scope);
								groupedWork.addScopingInfo(scope.getScopeName(), scopingInfo);
								scopingInfo.setLibraryOwned(true);
								scopingInfo.setLocallyOwned(true);
							}
						}

						hooplaRecord.addItem(itemInfo);
					}
				} else {
					// For Instant titles, create single ItemInfo with global availability
					ItemInfo itemInfo = new ItemInfo();
					itemInfo.setItemIdentifier(identifier);
					itemInfo.seteContentSource("Hoopla");
					itemInfo.setIsEContent(true);
					itemInfo.seteContentUrl(rawResponse.getString("url"));
					itemInfo.setShelfLocation("Online Hoopla Collection");
					itemInfo.setDetailedLocation("Online Hoopla Collection");
					itemInfo.setCallNumber("Online Hoopla");
					itemInfo.setSortableCallNumber("Online Hoopla");
					itemInfo.setFormat(primaryFormat);
					itemInfo.setFormatCategory(formatCategory);
					itemInfo.setInLibraryUseOnly(false);
					itemInfo.setDateAdded(dateAdded);

					// For Instant titles, always available
					itemInfo.setNumCopies(1);
					itemInfo.setAvailable(true);
					itemInfo.setDetailedStatus("Available Online");
					itemInfo.setGroupedStatus("Available Online");
					itemInfo.setHoldable(false);

					// Scope to all libraries that have entitlements for this title
					for (Scope scope : indexer.getScopes()) {
						boolean okToAdd = false;
						HooplaScope hooplaScope = scope.getHooplaScope();
						if (hooplaScope != null) {
							Long libraryId = scope.getLibraryId();
							if (libraryId != null) {
								// Check entitlement and get purchase model for this library
								String libraryPurchaseModel = checkEntitlementAndGetPurchaseModel(identifier, libraryId, hooplaType, groupedWork, scope);
								if (libraryPurchaseModel != null) {
									// Check if the content passes the scoping rules
									okToAdd = hooplaScope.isOkToAdd(identifier, kind, price, abridged, pa, profanity, isAdult, isTeen, isKids, rating, genresToAdd, libraryPurchaseModel, logger);
								}
							} else {
								if (groupedWork.isDebugEnabled()) {
									groupedWork.addDebugMessage("Scope " + scope.getScopeName() + " excluded due to missing libraryId", 2);
								}
							}
						}

						if (okToAdd) {
							ScopingInfo scopingInfo = itemInfo.addScope(scope);
							groupedWork.addScopingInfo(scope.getScopeName(), scopingInfo);
							scopingInfo.setLibraryOwned(true);
							scopingInfo.setLocallyOwned(true);
						}
					}

					hooplaRecord.addItem(itemInfo);
				}

			}
			productRS.close();
		}catch (NullPointerException e) {
			logEntry.incErrors("Null pointer exception processing Hoopla record " + identifier + " grouped work " + groupedWork.getId(), e);
		} catch (JSONException e) {
			logEntry.incErrors("Error parsing raw data for Hoopla record " + identifier, e);
		} catch (SQLException e) {
			logEntry.incErrors("Error loading information from Database for Hoopla title " + identifier, e);
		}
	}

	private String fixHooplaData(long id) throws SQLException{
		doubleDecodeRawResponseStmt.setLong(1, id);
		ResultSet doubleDecodeRawResponseRS = doubleDecodeRawResponseStmt.executeQuery();
		if (doubleDecodeRawResponseRS.next()){
			String rawResponseString = doubleDecodeRawResponseRS.getString("rawResponse");
			if (rawResponseString.charAt(0) == '{' && rawResponseString.charAt(rawResponseString.length() -1) == '}'){
				updateRawResponseStmt.setString(1, rawResponseString);
				updateRawResponseStmt.setLong(2, id);
				updateRawResponseStmt.executeUpdate();
				return rawResponseString;
			}
		}
		doubleDecodeRawResponseRS.close();

		return null;
	}

	/**
	 * Check if the library has an active entitlement for this content and return the purchase model
	 * @param identifier The Hoopla identifier
	 * @param libraryId The library ID
	 * @param fallbackPurchaseModel Fallback purchase model if none is stored
	 * @param groupedWork For debug messages
	 * @param scope For debug messages
	 * @return The purchase model for this library, or null if no entitlement
	 */
	private String checkEntitlementAndGetPurchaseModel(String identifier, Long libraryId, String fallbackPurchaseModel, AbstractGroupedWorkSolr groupedWork, Scope scope) {
		try {
			checkEntitlementStmt.setString(1, identifier);
			checkEntitlementStmt.setLong(2, libraryId);
			ResultSet entitlementRS = checkEntitlementStmt.executeQuery();

			boolean hasEntitlement = entitlementRS.next();
			String purchaseModel = null;

			if (hasEntitlement) {
				purchaseModel = entitlementRS.getString("purchaseModel");
				// If no purchase model is stored, fall back to global hooplaType
				if (purchaseModel == null || purchaseModel.isEmpty()) {
					purchaseModel = fallbackPurchaseModel;
				}
			} else {
				if (groupedWork.isDebugEnabled()) {
					groupedWork.addDebugMessage("Scope " + scope.getScopeName() + " excluded due to inactive entitlement (libraryId " + libraryId + " not found in hoopla_entitlements)", 2);
				}
			}

			entitlementRS.close();
			return hasEntitlement ? purchaseModel : null;
		} catch (SQLException e) {
			logger.error("Error checking entitlement for hooplaId " + identifier + " and libraryId " + libraryId, e);
			return null; // Default to not adding if there's an error
		}
	}

	/**
	 * Get all libraries that have active entitlements for a given Hoopla identifier.
	 *
	 * @param identifier The Hoopla identifier
	 * @return Set of library IDs that have active entitlements
	 */
	private HashSet<Long> getEntitledLibraries(String identifier) {
		HashSet<Long> entitledLibraries = new HashSet<>();
		try {
			PreparedStatement getEntitledLibrariesStmt = dbConn.prepareStatement(
				"SELECT libraryId FROM hoopla_entitlements WHERE hooplaId = ? AND active = 1"
			);
			getEntitledLibrariesStmt.setString(1, identifier);
			ResultSet entitledLibrariesRS = getEntitledLibrariesStmt.executeQuery();

			while (entitledLibrariesRS.next()) {
				entitledLibraries.add(entitledLibrariesRS.getLong("libraryId"));
			}

			entitledLibrariesRS.close();
			getEntitledLibrariesStmt.close();

		} catch (SQLException e) {
			logger.error("Error getting entitled libraries for hooplaId " + identifier, e);
		}
		return entitledLibraries;
	}

	/**
	 * Set Flex availability for a specific library by checking that library's availability data.
	 * Following OverDrive pattern - per-library ItemInfo objects with per-library availability.
	 *
	 * @param itemInfo The item info to set availability on
	 * @param identifier The Hoopla identifier
	 * @param libraryId The specific library ID
	 */
	private void setFlexAvailabilityForLibrary(ItemInfo itemInfo, String identifier, Long libraryId) {
		// Start with conservative defaults
		int totalCopies = 1;
		boolean hasAvailableCopies = false;

		try {
			// Query this specific library's Flex availability for this title
			getFlexAvailabilityStmt.setString(1, identifier);
			getFlexAvailabilityStmt.setLong(2, libraryId);
			ResultSet flexAvailabilityRS = getFlexAvailabilityStmt.executeQuery();

			if (flexAvailabilityRS.next()) {
				totalCopies = flexAvailabilityRS.getInt("totalCopies");
				int availableCopies = flexAvailabilityRS.getInt("availableCopies");
				hasAvailableCopies = availableCopies > 0;
			}

			flexAvailabilityRS.close();

		} catch (SQLException e) {
			logger.error("Error checking Flex availability for hooplaId " + identifier + " and libraryId " + libraryId, e);
		}

		// Set availability based on findings
		itemInfo.setNumCopies(totalCopies);
		itemInfo.setAvailable(hasAvailableCopies);
		itemInfo.setHoldable(!hasAvailableCopies);

		if (hasAvailableCopies) {
			itemInfo.setDetailedStatus("Available Online");
			itemInfo.setGroupedStatus("Available Online");
		} else {
			itemInfo.setDetailedStatus("Checked Out");
			itemInfo.setGroupedStatus("Checked Out");
		}
	}

}
