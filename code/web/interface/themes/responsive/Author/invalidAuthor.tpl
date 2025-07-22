<h1>{translate text=$pageTitleShort isPublicFacing=true}</h1>

{if !empty($solrSearchDebug)}
	<div id="solrSearchOptionsToggle" onclick="$('#solrSearchOptions').toggle()">{translate text="Show Search Options" isAdminFacing=true}</div>
	<div id="solrSearchOptions" style="display:none">
		<pre>{translate text="Search options" isPublicFacing=true} {$solrSearchDebug}</pre>
	</div>
{/if}

{if !empty($solrLinkDebug)}
	<div id='solrLinkToggle' onclick='$("#solrLink").toggle()'>{translate text="Show Solr Link" isAdminFacing=true}</div>
	<div id='solrLink' style='display:none'>
		<pre>{$solrLinkDebug}</pre>
	</div>
{/if}

{if !empty($authorName)}
	<p class="alert alert-warning">{translate text='Sorry, we could not find the author <strong>%1%</strong> in our catalog. Please try your search again.' 1=$authorName isPublicFacing=true}</p>
{else}
	<p class="alert alert-warning">{translate text='No author was provided, please try your search again.' isPublicFacing=true}</p>
{/if}

{if $loggedIn && $user->canSuggestMaterials()}
	{if $materialRequestType == 1 && $displayMaterialsRequest}
		<p class="alert alert-info materialsRequestLink">
			{translate text="Can't find what you are looking for? Try our Materials Request Service." isPublicFacing=true} <a href="/MaterialsRequest/NewRequest" class="btn btn-sm btn-info" onclick="return AspenDiscovery.Account.followLinkIfLoggedIn(this);">{translate text='Submit Request' isPublicFacing=true}</a>
		</p>
	{elseif $materialRequestType == 2 && $displayMaterialsRequest}
		<p class="alert alert-info materialsRequestLink">
			{translate text="Can't find what you are looking for? Try our Materials Request Service." isPublicFacing=true} <a href="/MaterialsRequest/NewRequestIls" class="btn btn-sm btn-info" onclick="return AspenDiscovery.Account.followLinkIfLoggedIn(this);">{translate text='Submit Request' isPublicFacing=true}</a>
		</p>
	{elseif $materialRequestType == 3 && $displayMaterialsRequest}
		<p class="alert alert-info materialsRequestLink">
			{translate text="Can't find what you are looking for? Try our Materials Request Service." isPublicFacing=true} <a href="{$externalMaterialsRequestUrl}" class="btn btn-sm btn-info">{translate text='Submit Request' isPublicFacing=true}</a>
		</p>
	{/if}
{/if}
