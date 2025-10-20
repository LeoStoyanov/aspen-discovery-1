package com.turning_leaf_technologies.indexing;

import org.apache.logging.log4j.Logger;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.BaseHttpSolrClient;
import org.apache.solr.client.solrj.impl.ConcurrentUpdateHttp2SolrClient;
import org.apache.solr.client.solrj.impl.Http2SolrClient;
import org.apache.solr.common.SolrInputDocument;

import java.io.IOException;

/**
 * Lightweight helper for publishing suggestion documents to the centralized Solr suggester core.
 */
public class SuggestionUpdateManager implements AutoCloseable {
	private final Logger logger;
	private final Http2SolrClient http2Client;
	private final ConcurrentUpdateHttp2SolrClient suggestionClient;

	private SuggestionUpdateManager(Logger logger, Http2SolrClient http2Client, ConcurrentUpdateHttp2SolrClient suggestionClient) {
		this.logger = logger;
		this.http2Client = http2Client;
		this.suggestionClient = suggestionClient;
	}

	public static SuggestionUpdateManager create(String solrHost, String solrPort, Logger logger) {
		Http2SolrClient http2Client = new Http2SolrClient.Builder().build();
		try {
			ConcurrentUpdateHttp2SolrClient client = new ConcurrentUpdateHttp2SolrClient.Builder("http://" + solrHost + ":" + solrPort + "/solr/suggest", http2Client)
				.withThreadCount(1)
				.withQueueSize(25)
				.build();
			return new SuggestionUpdateManager(logger, http2Client, client);
		} catch (OutOfMemoryError outOfMemoryError) {
			logger.error("Unable to create suggestion Solr client, out of memory", outOfMemoryError);
			return null;
		}
	}

	public boolean isActive() {
		return suggestionClient != null;
	}

	public void submit(SolrInputDocument document) {
		if (!isActive() || document == null) {
			return;
		}
		try {
			suggestionClient.add(document);
		} catch (SolrServerException | IOException e) {
			logger.warn("Unable to submit suggestion document {}", document.getFieldValue("id"), e);
		}
	}

	public void deleteById(String id) {
		if (!isActive() || id == null) {
			return;
		}
		try {
			suggestionClient.deleteById(id);
		} catch (SolrServerException | IOException e) {
			logger.warn("Unable to delete suggestion document {}", id, e);
		}
	}

	public void deleteByQuery(String query) {
		if (!isActive() || query == null) {
			return;
		}
		try {
			suggestionClient.deleteByQuery(query);
		} catch (SolrServerException | IOException e) {
			logger.warn("Unable to delete suggestion documents with query {}", query, e);
		}
	}

	public void commit(boolean waitFlush, boolean waitSearcher, boolean softCommit) {
		if (!isActive()) {
			return;
		}
		try {
			suggestionClient.commit(waitFlush, waitSearcher, softCommit);
		} catch (SolrServerException | IOException e) {
			logger.warn("Unable to commit suggestion updates", e);
		}
	}

	public void commit() {
		commit(false, false, true);
	}

	public void blockUntilFinished() {
		if (!isActive()) {
			return;
		}
		try {
			suggestionClient.blockUntilFinished();
		} catch (Exception e) {
			logger.warn("Unable to wait for suggestion updates to finish", e);
		}
	}

	@Override
	public void close() {
		if (!isActive()) {
			return;
		}
		try {
			suggestionClient.close();
		} catch (Exception e) {
			logger.warn("Unable to close suggestion client", e);
		}
		try {
			http2Client.close();
		} catch (Exception e) {
			logger.warn("Unable to close HTTP2 client for suggestion manager", e);
		}
	}
}
