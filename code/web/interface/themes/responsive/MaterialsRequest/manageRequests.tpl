{strip}
	<style>
		/* Improve dropdown badge styling */
		.badge.dropdown-toggle::after {
			margin-left: 0.5em;
			vertical-align: middle;
			display: inline-block !important;
			content: "";
			border-top: 0.3em solid;
			border-right: 0.3em solid transparent;
			border-bottom: 0;
			border-left: 0.3em solid transparent;
		}
		.badge-pill {
			padding-right: 1em;
			padding-left: 1em;
			font-size: 90%;
		}
		/* Ensure all badge dropdowns have the same styling */
		.badge.dropdown-toggle {
			position: relative;
			padding-right: 1.75em !important;
		}
		.badge.dropdown-toggle::after {
			position: absolute;
			right: 0.6em;
			top: 50%;
			transform: translateY(-50%);
		}
		.dropdown-menu.status-dropdown {
			min-width: 200px;
			padding: 0.5rem 0;
		}
		.dropdown-menu.status-dropdown .dropdown-item {
			padding: 0.4rem 1.5rem;
			white-space: normal;
			display: block;
			width: 100%;
			clear: both;
			text-align: left;
		}
		/* Add more specific padding for buttons */
		.action-button {
			margin-left: 10px;
		}
		/* When buttons wrap, don't apply left margin */
		@media (max-width: 768px) {
			.form-inline .action-button {
				margin-left: 0;
				margin-top: 10px;
			}
		}
		/* Add more specific padding for buttons */
		.action-button-wide {
			margin-left: 10px;
		}
		/* When buttons wrap, don't apply left margin */
		@media (max-width: 934px) {
			.form-inline .action-button-wide {
				margin-left: 0;
				margin-top: 10px;
			}
		}

		/* Badge colors for various statuses */
		.badge-pending { background-color: #007bff; color: white; }
		.badge-owned, .badge-purchased { background-color: #28a745; color: white; }
		.badge-rejected, .badge-canceled { background-color: #dc3545; color: white; }
		.badge-processing { background-color: #ffc107; color: #212529; }
		.badge-yes { background-color: #28a745; color: white; }
		.badge-no { background-color: #6c757d; color: white; }
		/* Additional badge colors */
		.badge-received { background-color: #28a745; color: white; }
		.badge-ordered { background-color: #17a2b8; color: white; }
		.badge-archived { background-color: #6c757d; color: white; }
		.badge-referral { background-color: #6610f2; color: white; }

		/* Results count badge */
		.total-results-badge {
			font-size: 100%;
			padding: 0.5em 0.75em;
		}

		/* Fix dropdown display issues */
		.dropdown-toggle {
			cursor: pointer;
		}
		.dropdown-menu {
			max-height: 300px;
			overflow-y: auto;
		}
		/* Ensure dropdown items stack properly */
		.dropdown-item {
			display: block !important;
			width: 100% !important;
			padding: 0.4rem 1.5rem !important;
			clear: both !important;
			font-weight: 400 !important;
			text-align: inherit !important;
			white-space: nowrap !important;
			background-color: transparent !important;
			border: 0 !important;
		}

		/* Enhanced action buttons */
		.actions-container {
			display: flex;
			align-items: center;
			justify-content: flex-start;
			gap: 5px;
		}
		.actions-container .dropdown {
			margin-right: 8px;
		}
		.actions-container .btn-group {
			flex-grow: 1;
			display: flex;
			justify-content: space-between;
		}
		/* Filter header toggle styling */
		.filter-header {
			display: flex;
			justify-content: space-between;
			align-items: center;
			width: 100%;
			text-decoration: none !important;
		}
		.filter-header:hover {
			text-decoration: none;
		}
		.filter-header .fa {
			transition: transform 0.25s ease;
			margin-left: 10px;
		}
		.filter-header[aria-expanded="true"] .fa-angle-down {
			transform: rotate(180deg);
		}
		.filter-header-title {
			display: flex;
			align-items: center;
		}
	</style>
	<div id="main-content" class="col-md-12">
		<h1>{translate text="Manage Materials Requests" isAdminFacing=true}</h1>
		{if !empty($error)}
			<div class="alert alert-danger">{$error}</div>
		{/if}

		{if !empty($loggedIn)}
			<div class="row">
				<!-- Filters Panel -->
				<div class="col-md-12 mb-3">
					<div class="card">
						<div class="card-header">
							<a data-toggle="collapse" href="#filterPanel" role="button" aria-expanded="false" class="filter-header">
								<div class="filter-header-title">
									<h2 class="h4 mb-0">{translate text="Filters" isAdminFacing=true}</h2>
									<i class="fa fa-angle-down" style="position: relative; top: 6px;"></i>
								</div>
								<span></span>
							</a>
						</div>
						<div id="filterPanel" class="collapse">
							<div class="card-body">
								<form action="/MaterialsRequest/ManageRequests" method="get" class="form">
									<!-- Main Filters -->
									<div class="row">
										<!-- Left Column - Status and Format -->
										<div class="col-md-6 mb-3">
											<!-- Status Filter -->
											<div class="card mb-3">
												<div class="card-header bg-light">
													<div class="d-flex justify-content-between align-items-center">
														<h3 class="h5 mb-0 text-primary">{translate text="Status & Archive Options" isAdminFacing=true}</h3>
														<div class="custom-control custom-checkbox">
															<input type="checkbox" class="custom-control-input" name="selectAllStatusFilter" id="selectAllStatusFilter" onchange="AspenDiscovery.toggleCheckboxes('.statusFilter', '#selectAllStatusFilter');">
															<label class="custom-control-label" for="selectAllStatusFilter">
																<small>{translate text="Select All Statuses" isAdminFacing=true}</small>
															</label>
														</div>
													</div>
												</div>
												<div class="card-body">
													<div class="row">
														<!-- Status Checkboxes -->
														<div class="col-md-7">
															<div class="border-bottom mb-2 pb-1">
																<strong class="text-muted small text-uppercase">{translate text="Available Statuses" isAdminFacing=true}</strong>
															</div>
															<div class="form-group py-1" style="max-height: 200px; overflow-y: auto;">
																{foreach from=$availableStatuses item=statusLabel key=status}
																	<div class="custom-control custom-checkbox pl-4 mb-1">
																		<input type="checkbox" class="custom-control-input statusFilter" name="statusFilter[]" id="status_{$status}" value="{$status}" {if in_array($status, $statusFilter)}checked="checked"{/if}>
																		<label class="custom-control-label" for="status_{$status}">
																			{translate text=$statusLabel isAdminFacing=true isAdminEnteredData=true}
																		</label>
																	</div>
																{/foreach}
															</div>
														</div>
														<!-- Archive Options -->
														<div class="col-md-5">
															<div class="border-bottom mb-2 pb-1">
																<strong class="text-muted small text-uppercase">{translate text="Archive View" isAdminFacing=true}</strong>
															</div>
															<div class="form-group">
																<div class="custom-control custom-radio pl-4 mb-2">
																	<input type="radio" class="custom-control-input" id="showActiveRequestsOnly" name="archiveFilter" value="showActive" {if $archiveFilter == 'showActive'}checked="checked"{/if}>
																	<label class="custom-control-label" for="showActiveRequestsOnly">
																		{translate text="Active only" isAdminFacing=true}
																	</label>
																</div>
																<div class="custom-control custom-radio pl-4 mb-2">
																	<input type="radio" class="custom-control-input" id="showAllRequests" name="archiveFilter" value="showAll" {if $archiveFilter == 'showAll'}checked="checked"{/if}>
																	<label class="custom-control-label" for="showAllRequests">
																		{translate text="All requests" isAdminFacing=true}
																	</label>
																</div>
																<div class="custom-control custom-radio pl-4 mb-2">
																	<input type="radio" class="custom-control-input" id="showArchivedRequestsOnly" name="archiveFilter" value="showArchived" {if $archiveFilter == 'showArchived'}checked="checked"{/if}>
																	<label class="custom-control-label" for="showArchivedRequestsOnly">
																		{translate text="Archived only" isAdminFacing=true}
																	</label>
																</div>
															</div>
														</div>
													</div>
												</div>
											</div>

											<!-- Format Filter -->
											<div class="card">
												<div class="card-header bg-light">
													<div class="d-flex justify-content-between align-items-center">
														<h3 class="h5 mb-0 text-primary">{translate text="Format" isAdminFacing=true}</h3>
														<div class="custom-control custom-checkbox">
															<input type="checkbox" class="custom-control-input" name="selectAllFormatFilter" id="selectAllFormatFilter" onchange="AspenDiscovery.toggleCheckboxes('.formatFilter', '#selectAllFormatFilter');">
															<label class="custom-control-label" for="selectAllFormatFilter">
																<small>{translate text="Select All" isAdminFacing=true}</small>
															</label>
														</div>
													</div>
												</div>
												<div class="card-body">
													<div class="border-bottom mb-2 pb-1">
														<strong class="text-muted small text-uppercase">{translate text="Available Formats" isAdminFacing=true}</strong>
													</div>
													<div class="form-group" style="max-height: 150px; overflow-y: auto;">
														{foreach from=$availableFormats item=formatLabel key=format}
															<div class="custom-control custom-checkbox pl-4 mb-1">
																<input type="checkbox" class="custom-control-input formatFilter" name="formatFilter[]" id="format_{$format}" value="{$format}" {if in_array($format, $formatFilter)}checked="checked"{/if}>
																<label class="custom-control-label" for="format_{$format}">
																	{translate text=$formatLabel isAdminFacing=true}
																</label>
															</div>
														{/foreach}
													</div>
												</div>
											</div>
										</div>

										<!-- Right Column - Date, IDs, and Assignment -->
										<div class="col-md-6 mb-3">
											<!-- Date Filter -->
											<div class="card mb-3">
												<div class="card-header bg-light">
													<h3 class="h5 mb-0 text-primary">{translate text="Date Range & Request IDs" isAdminFacing=true}</h3>
												</div>
												<div class="card-body">
													<div class="row">
														<!-- Date Range -->
														<div class="col-md-6">
															<div class="border-bottom mb-2 pb-1">
																<strong class="text-muted small text-uppercase">{translate text="Date Range" isAdminFacing=true}</strong>
															</div>
															<div class="form-group">
																<div class="input-group input-group-sm mb-2">
																	<div class="input-group-prepend">
																		<span class="input-group-text">{translate text="From" isAdminFacing=true}</span>
																	</div>
																	<input type="date" id="startDate" name="startDate" value="{if !empty($startDate)}{$startDate}{/if}" class="form-control" max="{$smarty.now|date_format:"%Y-%m-%d"}">
																</div>
																<div class="input-group input-group-sm">
																	<div class="input-group-prepend">
																		<span class="input-group-text">{translate text="To" isAdminFacing=true}</span>
																	</div>
																	<input type="date" id="endDate" name="endDate" value="{if !empty($endDate)}{$endDate}{/if}" class="form-control" max="{$smarty.now|date_format:"%Y-%m-%d"}">
																</div>
															</div>
														</div>
														<!-- Request IDs -->
														<div class="col-md-6">
															<div class="border-bottom mb-2 pb-1">
																<strong class="text-muted small text-uppercase">{translate text="Request IDs" isAdminFacing=true}</strong>
															</div>
															<div class="form-group">
																<textarea id="idsToShow" name="idsToShow" class="form-control form-control-sm" rows="4" placeholder="{translate text="Enter IDs separated by commas" isAdminFacing=true inAttribute=true}">{if !empty($idsToShow)}{$idsToShow}{/if}</textarea>
															</div>
														</div>
													</div>
												</div>
											</div>

											<!-- Assigned To Filter -->
											<div class="card">
												<div class="card-header bg-light">
													<div class="d-flex justify-content-between align-items-center">
														<h3 class="h5 mb-0 text-primary">{translate text="Assigned To" isAdminFacing=true}</h3>
														<div class="custom-control custom-checkbox">
															<input type="checkbox" class="custom-control-input" name="selectAllAssigneesFilter" id="selectAllAssigneesFilter" onchange="AspenDiscovery.toggleCheckboxes('.assigneesFilter', '#selectAllAssigneesFilter');">
															<label class="custom-control-label" for="selectAllAssigneesFilter">
																<small>{translate text="Select All" isAdminFacing=true}</small>
															</label>
														</div>
													</div>
												</div>
												<div class="card-body">
													<div class="border-bottom mb-2 pb-1">
														<strong class="text-muted small text-uppercase">{translate text="Assignment Options" isAdminFacing=true}</strong>
													</div>
													<div class="form-group mb-3">
														<div class="custom-control custom-checkbox pl-4">
															<input type="checkbox" class="custom-control-input" name="showUnassigned" id="showUnassigned" {if !empty($showUnassigned)} checked{/if}>
															<label class="custom-control-label font-weight-bold" for="showUnassigned">
																{translate text="Show Unassigned" isAdminFacing=true}
															</label>
														</div>
													</div>
													<div class="border-bottom mb-2 pb-1">
														<strong class="text-muted small text-uppercase">{translate text="Staff Members" isAdminFacing=true}</strong>
													</div>
													<div class="form-group" style="max-height: 150px; overflow-y: auto;">
														{foreach from=$assignees item=displayName key=assigneeId}
															<div class="custom-control custom-checkbox pl-4 mb-1">
																<input type="checkbox" class="custom-control-input assigneesFilter" name="assigneesFilter[]" id="assignee_{$assigneeId}" value="{$assigneeId}" {if in_array($assigneeId, $assigneesFilter)}checked="checked"{/if}>
																<label class="custom-control-label" for="assignee_{$assigneeId}">
																	{$displayName|escape}
																</label>
															</div>
														{/foreach}
													</div>
												</div>
											</div>
										</div>
									</div>

									<div class="row">
										<div class="col-md-12">
											<button type="submit" name="submit" class="btn btn-primary">
												<i class="fa fa-filter"></i> {translate text="Apply Filters" inAttribute=true isAdminFacing=true}
											</button>
											<button type="button" class="btn btn-outline-secondary action-button" onclick="window.location.href='/MaterialsRequest/ManageRequests'">
												<i class="fa fa-times"></i> {translate text="Clear Filters" inAttribute=true isAdminFacing=true}
											</button>
										</div>
									</div>
								</form>
							</div>
						</div>
					</div>
				</div>
			</div>

			<!-- Add larger gap between filters and results -->
			<div class="mb-4"></div>

			{if count($allRequests) > 0}
				<form id="updateRequests" method="post" action="/MaterialsRequest/ManageRequests" class="form">
					<!-- Entries Per Page Selector -->
					<div class="row mb-3">
						<div class="col-md-7">
							<div class="form-inline align-items-center">
								<label for="pageSize" class="mr-2">{translate text="Entries Per Page" isAdminFacing=true}</label>
								<select id="pageSize" name="pageSize" class="form-control custom-select action-button" onchange="AspenDiscovery.changePageSize()">
									<option value="30"{if $materialsRequestsPerPage == 30} selected="selected"{/if}>30</option>
									<option value="50"{if $materialsRequestsPerPage == 50} selected="selected"{/if}>50</option>
									<option value="75"{if $materialsRequestsPerPage == 75} selected="selected"{/if}>75</option>
									<option value="100"{if $materialsRequestsPerPage == 100} selected="selected"{/if}>100</option>
									<option value="250"{if $materialsRequestsPerPage == 250} selected="selected"{/if}>250</option>
									<option value="500"{if $materialsRequestsPerPage == 500} selected="selected"{/if}>500</option>
								</select>
							</div>
						</div>
						<div class="col-md-5 text-right">
							<span class="badge badge-info total-results-badge">{translate text="Total Results" isAdminFacing=true}: {count($allRequests)}</span>
						</div>
					</div>

					<!-- Materials Requests Table -->
					<div class="table-responsive">
						<table id="requestedMaterials" class="table table-striped table-hover table-bordered">
							<thead class="thead-dark">
							<tr>
								<th>
									<div class="custom-control custom-checkbox">
										<input type="checkbox" class="custom-control-input" name="selectAll" id="selectAll" aria-label="{translate text="Select All" isAdminFacing=true inAttribute=true}" onchange="AspenDiscovery.toggleCheckboxes('.select', '#selectAll');">
										<label class="custom-control-label" for="selectAll"></label>
									</div>
								</th>
								{foreach from=$columnsToDisplay item=label}
									<th>{translate text=$label isAdminFacing=true}</th>
								{/foreach}
								{if $showExistingTitleInformation}
									<th>{translate text="Exists In Catalog?" isAdminFacing=true}</th>
								{/if}
								<th class="actions">{translate text="Actions" isAdminFacing=true}</th>
							</tr>
							</thead>
							<tbody>
							{foreach from=$allRequests item=request}
								<tr>
									<td>
										<div class="custom-control custom-checkbox">
											<input type="checkbox" class="custom-control-input select" name="select[{$request->id}]" id="select_{$request->id}" aria-label="{translate text="Select Row" isAdminFacing=true inAttribute=true}">
											<label class="custom-control-label" for="select_{$request->id}"></label>
										</div>
									</td>
									{foreach name="columnLoop" from=$columnsToDisplay item=label key=column}
										{if $column == 'format'}
											<td>
												{if in_array($request->format, array_keys($availableFormats))}
													{assign var="key" value=$request->format}
													{translate text=$availableFormats.$key isAdminFacing=true}
												{else}
													{translate text=$request->format isAdminFacing=true}
												{/if}
											</td>
										{elseif $column == 'abridged'}
											<td>{if $request->$column == 1}{translate text="Yes" isAdminFacing=true}{elseif $request->$column == 2}{translate text="N/A" isAdminFacing=true}{else}{translate text="No" isAdminFacing=true}{/if}</td>
										{elseif $column == 'about' || $column == 'comments' || $column == 'staffCommments'}
											<td>
												{if !empty($request->$column)}
													<div class="text-truncate" style="max-width: 200px;" title="{$request->$column|escape}">
														{$request->$column|truncate:50}
													</div>
												{/if}
											</td>
										{elseif $column == 'status'}
											<td>
												<div class="dropdown">
													<span class="badge badge-pill badge-info dropdown-toggle" data-toggle="dropdown" aria-haspopup="true" aria-expanded="false" id="status-badge-{$request->id}">
														{translate text=$request->statusLabel isAdminFacing=true}
													</span>
													<div class="dropdown-menu dropdown-menu-right status-dropdown" aria-labelledby="status-badge-{$request->id}">
														{foreach from=$availableStatuses item=statusLabel key=status}
															<a class="dropdown-item" href="#" onclick="return AspenDiscovery.MaterialsRequest.updateRequestStatus('{$request->id}', '{$status}');">{translate text=$statusLabel isAdminFacing=true isAdminEnteredData=true}</a>
														{/foreach}
													</div>
												</div>
											</td>
										{elseif $column == 'dateCreated' || $column == 'dateUpdated'}
											{* Date Columns*}
											<td>{$request->$column|date_format}</td>
										{elseif $column == 'createdBy'}
											<td>{$request->getCreatedByLastName()|escape}, {$request->getCreatedByFirstName()|escape}<br>{$request->getCreatedByUserBarcode()}</td>
										{elseif $column == 'emailSent' || $column == 'holdsCreated' || $column == 'illItem'}
											{* Simple Boolean Columns *}
											<td>
												{if $request->$column}
													<span class="badge badge-pill badge-yes">{translate text="Yes" isAdminFacing=true}</span>
												{else}
													<span class="badge badge-pill badge-no">{translate text="No" isAdminFacing=true}</span>
												{/if}
											</td>
										{elseif $column == 'email'}
											<td>{$request->email}</td>
										{elseif $column == 'placeHoldWhenAvailable'}
											<td>
												{if $request->$column}
													<span class="badge badge-pill badge-yes">{translate text="Yes" isAdminFacing=true}</span>
													{if $request->location} - {$request->location|escape}{/if}
												{else}
													<span class="badge badge-pill badge-no">{translate text="No" isAdminFacing=true}</span>
												{/if}
											</td>
										{elseif $column == 'holdPickupLocation'}
											<td>
												{$request->getHoldLocationName($request->holdPickupLocation)|escape}
											</td>
										{elseif $column == 'bookmobileStop'}
											<td>{$request->bookmobileStop}</td>
										{elseif $column == 'assignedTo'}
											<td>
												<div class="dropdown">
													{if $request->assignedTo}
														<span class="badge badge-pill badge-info dropdown-toggle" data-toggle="dropdown" aria-haspopup="true" aria-expanded="false" id="assignee-badge-{$request->id}">
															{if $request->getAssigneeName()|escape}{$request->getAssigneeName()|escape}{else}Name not found{/if}
														</span>
													{else}
														<span class="badge badge-pill badge-warning dropdown-toggle" data-toggle="dropdown" aria-haspopup="true" aria-expanded="false" id="assignee-badge-{$request->id}">
															{translate text="Unassigned" isAdminFacing=true}
														</span>
													{/if}
													<div class="dropdown-menu dropdown-menu-right status-dropdown" aria-labelledby="assignee-badge-{$request->id}">
														<a class="dropdown-item" href="#" onclick="return AspenDiscovery.MaterialsRequest.updateRequestAssignment('{$request->id}', 'unassign');">{translate text="Unassign" isAdminFacing=true}</a>
														<div class="dropdown-divider"></div>
														{foreach from=$assignees item=displayName key=assigneeId}
															<a class="dropdown-item" href="#" onclick="return AspenDiscovery.MaterialsRequest.updateRequestAssignment('{$request->id}', '{$assigneeId}');">{$displayName|escape}</a>
														{/foreach}
													</div>
												</div>
											</td>
										{else}
											{* All columns that can be displayed with out special handling *}
											<td>{$request->$column}</td>
										{/if}
									{/foreach}
									{if $showExistingTitleInformation}
										<td id="existingTitleInformation{$request->id}">
											{if $request->hasExistingRecord == 0}
												<span class="badge badge-pill badge-no" title="{translate text="Checked %1%" 1=$request->lastCheckForExistingRecord|date_format:"%D %T" isAdminFacing=true inAttribute=true}">
													{translate text="No" isAdminFacing=true}
												</span>
											{else}
												<a href="{$request->existingRecordUrl}" target="_blank" class="badge badge-pill badge-yes" title="{translate text="Checked %1%" 1=$request->lastCheckForExistingRecord|date_format:"%D %T" isAdminFacing=true inAttribute=true}">
													{translate text="Yes" isAdminFacing=true}
												</a>
											{/if}
											{if $request->automaticCheckForExistingRecordNeedsToBeDone()}
												<script>
													{literal}
													$(document).ready(function (){
														AspenDiscovery.MaterialsRequest.checkRequestForExistingRecord({/literal}{$request->id}{literal});
													});
													{/literal}
												</script>
											{/if}
										</td>
									{/if}
									<td>
										<!-- Improved actions layout -->
										<div class="actions-container">
											<!-- Contextual Archive/Unarchive button -->
											{assign var="statusObj" value=$request->getStatus()}
											{if $statusObj && $statusObj->isArchived == 1}
												<button class="btn btn-sm btn-warning" type="button" title="{translate text="Unarchive this request" isAdminFacing=true inAttribute=true}" onclick="return AspenDiscovery.MaterialsRequest.updateRequestArchiveStatus('{$request->id}', 'unarchive');">
													<i class="fa fa-box-open"></i>
												</button>
											{else}
												<button class="btn btn-sm btn-outline-secondary" type="button" title="{translate text="Archive this request" isAdminFacing=true inAttribute=true}" onclick="return AspenDiscovery.MaterialsRequest.updateRequestArchiveStatus('{$request->id}', 'archive');">
													<i class="fa fa-archive"></i>
												</button>
											{/if}

											<!-- Action buttons -->
											<div class="btn-group btn-group-sm">
												{if $showExistingTitleInformation && !$request->hasExistingRecord}
													<button type="button" onclick="AspenDiscovery.MaterialsRequest.checkRequestForExistingRecord('{$request->id}')" class="btn btn-info" title="{translate text="Check for Existing Title" isAdminFacing=true inAttribute=true}">
														<i class="fa fa-search"></i>
													</button>
												{/if}
												<button type="button" onclick="AspenDiscovery.MaterialsRequest.showMaterialsRequestDetails('{$request->id}', true)" class="btn btn-info" title="{translate text="Details" isAdminFacing=true inAttribute=true}">
													<i class="fa fa-eye"></i>
												</button>
												<button type="button" onclick="AspenDiscovery.MaterialsRequest.updateMaterialsRequest('{$request->id}')" class="btn btn-primary" title="{translate text="Update Request" isAdminFacing=true inAttribute=true}">
													<i class="fa fa-edit"></i>
												</button>
											</div>
										</div>
									</td>
								</tr>
							{/foreach}
							</tbody>
						</table>
					</div>

					{if in_array('Manage Library Materials Requests', $userPermissions)}
						<div id="materialsRequestActions" class="mt-4">
							<div class="card mb-4">
								<div class="card-header bg-light">
									<h2 class="h4 mb-0 text-primary">{translate text="Bulk Actions" isAdminFacing=true}</h2>
								</div>
								<div class="card-body pb-2">
									<div class="row">
										<!-- Assignment Actions -->
										<div class="col-md-3 mb-3">
											<div class="form-group">
												<label for="newAssignee" class="control-label font-weight-bold">{translate text="Assign selected to" isAdminFacing=true}</label>
												{if !empty($assignees)}
													<div class="form-inline">
														<select name="newAssignee" id="newAssignee" class="form-control custom-select mr-3">
															<option value="unselected">{translate text="Select One" inAttribute=true isAdminFacing=true}</option>
															<option value="unassign">{translate text="Un-assign" inAttribute=true isAdminFacing=true}</option>
															{foreach from=$assignees item=displayName key=assigneeId}
																<option value="{$assigneeId}">{$displayName|escape}</option>
															{/foreach}
														</select>
														<button type="button" class="btn btn-primary action-button" onclick="return AspenDiscovery.MaterialsRequest.assignSelectedRequests();">
															<i class="fa fa-user-check mr-2"></i> {translate text="Assign" isAdminFacing=true}
														</button>
													</div>
												{else}
													<div class="alert alert-warning py-1">
														<small><i class="fa fa-exclamation-triangle"></i> {translate text="No Valid Assignees Found" isAdminFacing=true}</small>
													</div>
												{/if}
											</div>
										</div>

										<!-- Status Actions -->
										<div class="col-md-6 mb-3">
											<div class="form-group">
												<label for="newStatus" class="control-label font-weight-bold">{translate text="Update status to" isAdminFacing=true}</label>
												<div class="form-inline">
													<select name="newStatus" id="newStatus" class="form-control custom-select mr-3">
														<option value="unselected" selected>{translate text="Select status" isAdminFacing=true}</option>
														{foreach from=$availableStatuses item=statusLabel key=status}
															<option value="{$status}">{translate text=$statusLabel isAdminFacing=true isAdminEnteredData=true}</option>
														{/foreach}
													</select>
													<button type="button" class="btn btn-primary action-button-wide" onclick="return AspenDiscovery.MaterialsRequest.updateSelectedRequests();">
														<i class="fa fa-tasks mr-2"></i> {translate text="Update" isAdminFacing=true}
													</button>
												</div>
											</div>
										</div>

										<!-- Archive Actions -->
										<div class="col-md-3 mb-3">
											<div class="form-group">
												<label for="archiveAction" class="control-label font-weight-bold">{translate text="Archive/Unarchive" isAdminFacing=true}</label>
												<div class="form-inline">
													<select name="archiveAction" id="archiveAction" class="form-control custom-select mr-3">
														<option value="unselected" selected>{translate text="Select action" isAdminFacing=true}</option>
														<option value="archive">{translate text="Archive" isAdminFacing=true}</option>
														<option value="unarchive">{translate text="Unarchive" isAdminFacing=true}</option>
													</select>
													<button type="button" class="btn btn-primary action-button" onclick="return AspenDiscovery.MaterialsRequest.archiveSelectedRequests();">
														<i class="fa fa-archive mr-2"></i> {translate text="Apply" isAdminFacing=true}
													</button>
												</div>
											</div>
										</div>
									</div>
									<div class="row">
										<!-- Export Actions -->
										<div class="col-md-6 mb-3">
											<div class="form-group">
												<label class="control-label font-weight-bold">{translate text="Export Data" isAdminFacing=true}</label>
												<div>
													<button class="btn btn-outline-secondary mr-3" type="submit" name="exportSelected" onclick="return AspenDiscovery.MaterialsRequest.exportSelectedRequests();" title="{translate text="Export Selected To CSV" inAttribute=true isAdminFacing=true}">
														<i class="fa fa-file-export mr-2"></i> {translate text="Export Selected" isAdminFacing=true}
													</button>
													<button class="btn btn-outline-secondary action-button" type="submit" name="exportAll" title="{translate text="Export All To CSV" inAttribute=true isAdminFacing=true}">
														<i class="fa fa-file-export mr-2"></i> {translate text="Export All" isAdminFacing=true}
													</button>
												</div>
											</div>
										</div>
									</div>
								</div>
							</div>
						</div>
					{/if}

					{if !empty($pageLinks.all)}
						<div class="text-center mt-3">
							<nav aria-label="Pagination">
								<ul class="pagination justify-content-center">
									{$pageLinks.all}
								</ul>
							</nav>
						</div>
					{/if}

					{if !empty($page)}
						<input type="hidden" name="page" value="{$page}">
					{/if}
				</form>
			{else}
				<div class="alert alert-info">
					<i class="fa fa-info-circle"></i> {translate text="There are no materials requests that meet your criteria." isAdminFacing=true}
				</div>
			{/if}
		{/if}
	</div>
{/strip}

<script type="text/javascript">
	$(function () {ldelim}
		$("#requestedMaterials").tablesorter({ldelim}
			cssAsc: 'sortAscHeader',
			cssDesc: 'sortDescHeader',
			cssHeader: 'unsortedHeader',
			widgets: ['zebra', 'filter'],
			headers: {ldelim}
				0: {ldelim}sorter: false{rdelim},
				{foreach name=config from=$dateColumns item=columnNumber}
				{$columnNumber+1}: {ldelim}sorter : 'date'{rdelim}{if empty($smarty.foreach.config.last)}, {/if}
				{/foreach}

			}
		});

		// Update filter arrow when filter panel changes
		$('#filterPanel').on('shown.bs.collapse', function () {ldelim}
			$('.filter-header .fa-angle-down').css('transform', 'rotate(180deg)');
		});
		$('#filterPanel').on('hidden.bs.collapse', function () {ldelim}
			$('.filter-header .fa-angle-down').css('transform', 'rotate(0deg)');
		});
	});
</script>
