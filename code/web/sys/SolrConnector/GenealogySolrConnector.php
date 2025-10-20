<?php

require_once 'Solr.php';

class GenealogySolrConnector extends Solr {
	function __construct($host) {
		parent::__construct($host, 'genealogy');
	}

	/**
	 * @return string
	 */
	function getSearchSpecsFile() {
		return ROOT_DIR . '/../../sites/default/conf/genealogySearchSpecs.yaml';
	}

	/** return string */
	public function getSearchesFile() {
		return 'genealogySearches';
	}

	protected function getScopingFiltersForCFQ(?Library $searchLibrary, ?Location $searchLocation): array {
		$cfqParts = parent::getScopingFiltersForCFQ($searchLibrary, $searchLocation);
		$cfqParts[] = 'record_type#person';
		return $cfqParts;
	}
}