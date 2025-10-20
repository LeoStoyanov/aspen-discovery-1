package com.turning_leaf_technologies.indexing;

import org.apache.solr.common.SolrInputDocument;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;

/**
 * Convenience helper for building and publishing website suggestion documents to the centralized suggester core.
 */
public class WebsiteSuggestionPublisher {
	private final SuggestionUpdateManager suggestionManager;
	private final String sourceContext;

	public WebsiteSuggestionPublisher(SuggestionUpdateManager suggestionManager, long websiteId) {
		this.suggestionManager = suggestionManager;
		this.sourceContext = "source#website#" + websiteId;
	}

	public void deleteBySource() {
		if (suggestionManager == null || !suggestionManager.isActive()) {
			return;
		}
		suggestionManager.deleteByQuery("record_type:website AND suggestions_context_filter:\"" + sourceContext + "\"");
	}

	public void submit(String documentId, SolrInputDocument document, Collection<String> scopes) {
		if (suggestionManager == null || !suggestionManager.isActive()) {
			return;
		}
		Object titleValue = document.getFieldValue("title");
		if (!(titleValue instanceof String)) {
			return;
		}
		String title = ((String) titleValue).trim();
		if (title.isEmpty()) {
			return;
		}

		HashSet<String> keywordSuggestions = new HashSet<>();
		// Don't add description or table_of_contents to suggestions - they're too long and not useful for autocomplete

		HashSet<String> contextFilters = new HashSet<>();
		contextFilters.add("record_type#website");
		contextFilters.add(sourceContext);
		if (scopes != null) {
			for (String scope : scopes) {
				contextFilters.add("scope#" + scope);
			}
		}

		SuggestionDocumentBuilder builder = new SuggestionDocumentBuilder("website|" + documentId, "website", "website")
			.addTitleSuggestions(Collections.singleton(title))
			.addKeywordSuggestions(keywordSuggestions)
			.addContextFilters(contextFilters)
			.setPopularity(1);

		suggestionManager.submit(builder.build());
	}

	public void deleteById(String documentId) {
		if (suggestionManager != null && suggestionManager.isActive()) {
			suggestionManager.deleteById("website|" + documentId);
		}
	}

	public void commit() {
		if (suggestionManager != null && suggestionManager.isActive()) {
			suggestionManager.commit();
		}
	}

	private void addFieldValues(SolrInputDocument document, String fieldName, HashSet<String> target) {
		Collection<Object> values = document.getFieldValues(fieldName);
		if (values == null) {
			return;
		}
		for (Object value : values) {
			if (value instanceof String) {
				String stringValue = ((String) value).trim();
				if (!stringValue.isEmpty()) {
					target.add(stringValue);
				}
			}
		}
	}
}
