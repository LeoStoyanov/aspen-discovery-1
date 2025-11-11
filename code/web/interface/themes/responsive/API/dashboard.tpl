{strip}
	<div id="main-content" class="col-sm-12">
		<h1>{translate text="Aspen Discovery API Usage Dashboard" isAdminFacing=true}</h1>
		{include file="Admin/selectInterfaceForm.tpl"}

		{* Detailed UserAPI Logs Section *}
		<h2>{translate text="Detailed User API Logs" isAdminFacing=true}</h2>

		<div class="dashboardCategory" style="margin-bottom: 20px;">
			<div class="row">
				<div class="col-sm-12">
					<h3 class="dashboardCategoryLabel">{translate text="Filter Logs" isAdminFacing=true}</h3>
				</div>
			</div>
			<div class="row">
				<div class="col-sm-12">
					<form method="get" class="form-horizontal">
						<input type="hidden" name="instance" value="{$selectedInstance}">
						<div class="row" style="margin-bottom: 10px; margin-left: -5px; margin-right: -5px;">
							<div class="col-sm-2" style="padding-left: 5px; padding-right: 5px;">
								<label for="logMethod">{translate text="Method" isAdminFacing=true}</label>
								<select name="logMethod" id="logMethod" class="form-control">
									<option value="" {if $logMethod == ''}selected{/if}>{translate text="All Methods" isAdminFacing=true}</option>
									{foreach from=$availableMethods item=method}
										<option value="{$method|escape}" {if $logMethod == $method}selected{/if}>{$method|escape}</option>
									{/foreach}
								</select>
							</div>
							<div class="col-sm-3" style="padding-left: 5px; padding-right: 5px;">
								<label for="logStartDate">{translate text="Start Date" isAdminFacing=true}</label>
								<input type="date" name="logStartDate" id="logStartDate" value="{$logStartDate}" class="form-control">
							</div>
							<div class="col-sm-3" style="padding-left: 5px; padding-right: 5px;">
								<label for="logEndDate">{translate text="End Date" isAdminFacing=true}</label>
								<input type="date" name="logEndDate" id="logEndDate" value="{$logEndDate}" class="form-control">
							</div>
							<div class="col-sm-2" style="padding-left: 5px; padding-right: 5px;">
								<label for="logLimit">{translate text="Limit" isAdminFacing=true}</label>
								<input type="number" name="logLimit" id="logLimit" value="{$logLimit}" class="form-control" min="10" max="1000">
							</div>
							<div class="col-sm-2" style="padding-left: 5px; padding-right: 5px;">
								<label>&nbsp;</label>
								<button type="submit" class="btn btn-primary btn-block"><i class="fas fa-filter"></i> {translate text="Filter" isAdminFacing=true}</button>
							</div>
						</div>
					</form>
				</div>
			</div>
		</div>

		{if $userStats}
			<div class="dashboardCategory" style="margin-bottom: 20px;">
				<div class="row">
					<div class="col-sm-12">
						<h3 class="dashboardCategoryLabel">{translate text="Top Users by Call Count" isAdminFacing=true}</h3>
					</div>
				</div>
				<div class="row">
					<div class="col-sm-12">
						<table class="table table-striped table-condensed table-hover">
							<thead>
								<tr>
									<th>{translate text="Display Name" isAdminFacing=true}</th>
									<th>{translate text="Barcode" isAdminFacing=true}</th>
									<th>{translate text="Call Count" isAdminFacing=true}</th>
								</tr>
							</thead>
							<tbody>
								{foreach from=$userStats item=stat}
									<tr>
										<td>{$stat.displayName|escape}</td>
										<td>{$stat.userBarcode|escape}</td>
										<td><strong>{$stat.callCount}</strong></td>
									</tr>
								{/foreach}
							</tbody>
						</table>
					</div>
				</div>
			</div>

			{if $detailedLogs}
				<div class="dashboardCategory">
					<div class="row">
						<div class="col-sm-12">
							<h3 class="dashboardCategoryLabel">{translate text="Recent API Calls" isAdminFacing=true}</h3>
						</div>
					</div>
					<div class="row">
						<div class="col-sm-12" style="max-height: 500px; overflow-y: auto;">
							<table class="table table-striped table-condensed table-hover">
								<thead>
									<tr>
										<th>{translate text="Timestamp" isAdminFacing=true}</th>
										<th>{translate text="Method" isAdminFacing=true}</th>
										<th>{translate text="Display Name" isAdminFacing=true}</th>
										<th>{translate text="Barcode" isAdminFacing=true}</th>
									</tr>
								</thead>
								<tbody>
									{foreach from=$detailedLogs item=log}
										<tr>
											<td>{$log.timestamp}</td>
											<td>{$log.method|escape}</td>
											<td>{$log.displayName|escape}</td>
											<td>{$log.userBarcode|escape}</td>
										</tr>
									{/foreach}
								</tbody>
							</table>
						</div>
					</div>
				</div>
			{/if}
		{else}
			<div class="alert alert-info">
				{translate text="No API usage logs found for the selected date range and method." isAdminFacing=true}
			</div>
		{/if}

		{foreach from=$statsByModule key=moduleName item=moduleStats}
			<h2>{$moduleName}</h2> {* No translation needed *}
			<div class="row">
				{foreach from=$moduleStats key=method item=methodStats}
					<div class="dashboardCategory col-sm-6">
						<div class="row">
							<div class="col-sm-10 col-sm-offset-1">
								<h3 class="dashboardCategoryLabel">{$method}{' '} {* No translation needed *}
									<a href="/API/UsageGraphs?stat={$method}&instance={$selectedInstance}" title="{translate text="{$moduleName}: {$method} Graph" inAttribute="true" isAdminFacing=true}">
										<i class="fas fa-chart-line"></i>
									</a>
								</h3>
							</div>
						</div>
						<div class="row">
							<div class="col-tn-6">
								<div class="dashboardLabel">{translate text="This Month" isAdminFacing=true}</div>
								<div class="dashboardValue">{$methodStats.usageThisMonth|number_format}</div>
							</div>
							<div class="col-tn-6">
								<div class="dashboardLabel">{translate text="Last Month" isAdminFacing=true}</div>
								<div class="dashboardValue">{$methodStats.usageLastMonth|number_format}</div>
							</div>
							<div class="col-tn-6">
								<div class="dashboardLabel">{translate text="This Year" isAdminFacing=true}</div>
								<div class="dashboardValue">{$methodStats.usageThisYear|number_format}</div>
							</div>
							<div class="col-tn-6">
								<div class="dashboardLabel">{translate text="All Time" isAdminFacing=true}</div>
								<div class="dashboardValue">{$methodStats.usageAllTime|number_format}</div>
							</div>
						</div>
					</div>
				{/foreach}
			</div>
		{/foreach}
	</div>
{/strip}