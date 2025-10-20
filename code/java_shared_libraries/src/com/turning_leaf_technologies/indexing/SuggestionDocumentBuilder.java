package com.turning_leaf_technologies.indexing;

import org.apache.solr.common.SolrInputDocument;
import java.util.Collection;
import java.util.Locale;

/**
 * Utility for composing Solr suggestion documents prior to submitting them to the centralized suggester core.
 */
public class SuggestionDocumentBuilder {
	private final SolrInputDocument document = new SolrInputDocument();

	public SuggestionDocumentBuilder(String id, String recordType, String source) {
		document.addField("id", id);
		document.addField("record_type", recordType);
		document.addField("source", source);
	}

	public SuggestionDocumentBuilder addTitleSuggestions(Collection<String> values) {
		addValues("title_suggestions", values, false);
		return this;
	}

	public SuggestionDocumentBuilder addAuthorSuggestions(Collection<String> values) {
		addValues("author_suggestions", values, false);
		return this;
	}

	public SuggestionDocumentBuilder addSubjectSuggestions(Collection<String> values) {
		addValues("subject_suggestions", values, false);
		return this;
	}

	public SuggestionDocumentBuilder addKeywordSuggestions(Collection<String> values) {
		addValues("keyword_suggestions", values, false);
		return this;
	}

	public SuggestionDocumentBuilder addNameSuggestions(Collection<String> values) {
		addValues("name_suggestions", values, false);
		return this;
	}

	public SuggestionDocumentBuilder addInstructorSuggestions(Collection<String> values) {
		addValues("instructor_suggestions", values, false);
		return this;
	}

	public SuggestionDocumentBuilder addContextFilters(Collection<String> values) {
		addValues("suggestions_context_filter", values, false);
		return this;
	}

	public SuggestionDocumentBuilder addLanguages(Collection<String> values) {
		addValues("language", values, true);
		return this;
	}

	public SuggestionDocumentBuilder setPopularity(long popularity) {
		int safePopularity;
		if (popularity > Integer.MAX_VALUE) {
			safePopularity = Integer.MAX_VALUE;
		} else if (popularity < Integer.MIN_VALUE) {
			safePopularity = Integer.MIN_VALUE;
		} else {
			safePopularity = (int) popularity;
		}
		document.setField("popularity", safePopularity);
		return this;
	}

	public SolrInputDocument build() {
		return document;
	}

	private void addValues(String field, Collection<String> values, boolean forceLowercase) {
		if (values == null) {
			return;
		}
		for (String value : values) {
			if (value == null) {
				continue;
			}
			String normalized = value.trim();
			if (normalized.isEmpty()) {
				continue;
			}
			if (forceLowercase) {
				normalized = normalized.toLowerCase(Locale.ROOT);
			}
			document.addField(field, normalized);
		}
	}
}
