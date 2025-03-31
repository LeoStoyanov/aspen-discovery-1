<?php

function getUpdates25_04_00(): array {
	$curTime = time();
	return [
		/*'name' => [
			 'title' => '',
			 'description' => '',
			 'continueOnError' => false,
			 'sql' => [
				 ''
			 ]
		 ], //name*/

		//mark - Grove
		'restrict_local_ill_by_patron_type' => [
			'title' => 'Restrict Local ILL by Patron Type',
			'description' => 'Add an option to restrict local ILL by Patron Type',
			'continueOnError' => false,
			'sql' => [
				'ALTER TABLE ptype ADD COLUMN allowLocalIll TINYINT DEFAULT  1'
			]
		], //restrict_local_ill_by_patron_type
		'force_regrouping_all_works_25_04' => [
			'title' => 'Force Regrouping All Works 25.04',
			'description' => 'Force Regrouping All Works',
			'sql' => [
				"UPDATE system_variables set regroupAllRecordsDuringNightlyIndex = 1",
			],
		], //force_regrouping_all_works_25_04
		'make_local_ill_form_note_optional' => [
			'title' => 'Make Local ILL Form Note Optional',
			'description' => 'Make Local ILL Form Note Optional',
			'sql' => [
				'ALTER TABLE local_ill_form ADD COLUMN showNote TINYINT DEFAULT  1'
			]
		], //make_local_ill_form_note_optional

		//katherine - Grove

		//kirstien - Grove

		//kodi - Grove

		//Yanjun Li - ByWater

		// Leo Stoyanov - BWS
		'create_potential_grouped_work_merges_table' => [
			'title' => 'Create potential_grouped_work_merges Table',
			'description' => 'Adds a table to store potentially mergeable grouped work pairs identified during reindexing.',
			'continueOnError' => false,
			'sql' => [
				"CREATE TABLE IF NOT EXISTS potential_grouped_work_merges (
				  id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
				  grouped_work_id_1 BIGINT(20) NOT NULL COMMENT 'ID of the first work (always the smaller ID)',
				  grouped_work_permanent_id_1 VARCHAR(40) NOT NULL COMMENT 'Permanent ID of the first work',
				  grouped_work_id_2 BIGINT(20) NOT NULL COMMENT 'ID of the second work (always the larger ID)',
				  grouped_work_permanent_id_2 VARCHAR(40) NOT NULL COMMENT 'Permanent ID of the second work',
				  title_similarity_score DECIMAL(5,4) DEFAULT NULL COMMENT 'Similarity score for titles (0-1)',
				  author_similarity_score DECIMAL(5,4) DEFAULT NULL COMMENT 'Similarity score for authors (0-1)',
				  overall_similarity_score DECIMAL(5,4) NOT NULL COMMENT 'Combined similarity score (0-1)',
				  reason TEXT COMMENT 'Brief explanation for potential merge',
				  date_added INT(11) NOT NULL COMMENT 'Timestamp when added',
				  INDEX idx_grouped_work_id_1 (grouped_work_id_1),
				  INDEX idx_grouped_work_id_2 (grouped_work_id_2),
				  INDEX idx_overall_similarity_score (overall_similarity_score),
				  INDEX idx_date_added (date_added),
				  UNIQUE INDEX unique_pair (grouped_work_id_1, grouped_work_id_2),
				  FOREIGN KEY (grouped_work_id_1) REFERENCES grouped_work(id) ON DELETE CASCADE,
				  FOREIGN KEY (grouped_work_id_2) REFERENCES grouped_work(id) ON DELETE CASCADE
				) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Stores pairs of potentially mergeable grouped works identified during reindexing';"
			]
		], //create_potential_grouped_work_merges_table

		//alexander - PTFS-Europe

		//chloe - PTFS-Europe

		//James Staub - Nashville Public Library

		//Lucas Montoya - Theke Solutions

		//other

	];
}