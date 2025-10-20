package com.turning_leaf_technologies.indexing;

import org.apache.logging.log4j.Logger;
import org.apache.solr.common.SolrInputDocument;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;

/**
 * Convenience helper for building and publishing event suggestion documents to the centralized suggester core.
 */
public class EventSuggestionPublisher {
    private final SuggestionUpdateManager suggestionManager;
    private final String sourceContext;
    private final Logger logger;

    public EventSuggestionPublisher(SuggestionUpdateManager suggestionManager, String sourceContext, Logger logger) {
        this.suggestionManager = suggestionManager;
        this.sourceContext = sourceContext;
        this.logger = logger;
    }

    public void deleteBySource() {
        if (suggestionManager == null || !suggestionManager.isActive()) {
            return;
        }
        suggestionManager.deleteByQuery("record_type:event AND suggestions_context_filter:\"" + sourceContext + "\"");
    }

    public void submit(String documentId, SolrInputDocument document, Collection<String> additionalContexts, int popularity) {
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

        HashSet<String> subjectSuggestions = new HashSet<>();
        addFieldValues(document, "event_type", subjectSuggestions);
        addFieldValues(document, "program_type", subjectSuggestions);
        addFieldValues(document, "age_group", subjectSuggestions);
        addFieldValues(document, "internal_category", subjectSuggestions);

        HashSet<String> keywordSuggestions = new HashSet<>();
        addFieldValues(document, "branch", keywordSuggestions);
        addFieldValues(document, "room", keywordSuggestions);
        addFieldValues(document, "offsite_address", keywordSuggestions);
        addFieldValues(document, "online_address", keywordSuggestions);

        HashSet<String> contextFilters = new HashSet<>();
        contextFilters.add("record_type#event");
        contextFilters.add(sourceContext);
        if (additionalContexts != null) {
            contextFilters.addAll(additionalContexts);
        }

        SuggestionDocumentBuilder builder = new SuggestionDocumentBuilder("event|" + documentId, "event", "events")
            .addTitleSuggestions(Collections.singleton(title))
            .addSubjectSuggestions(subjectSuggestions)
            .addKeywordSuggestions(keywordSuggestions)
            .addContextFilters(contextFilters)
            .setPopularity(Math.max(popularity, 1));

        suggestionManager.submit(builder.build());
    }

    public void deleteById(String documentId) {
        if (suggestionManager != null && suggestionManager.isActive()) {
            suggestionManager.deleteById("event|" + documentId);
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
            addStringValue(value, target);
        }
    }

    private void addStringValue(Object value, HashSet<String> target) {
        if (value instanceof String) {
            String stringValue = ((String) value).trim();
            if (!stringValue.isEmpty()) {
                target.add(stringValue);
            }
        }
    }
}
