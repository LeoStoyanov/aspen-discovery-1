{include file="GroupedWork/load-full-record-view-enrichment.tpl"}

{strip}
	<div class="col-xs-12">
		{* Search Navigation *}
		{include file="GroupedWork/search-results-navigation.tpl"}

		{* Display Title *}
		<h1>
			{$recordDriver->getTitle()|escape}
			{if $recordDriver->getSubtitle()}: {$recordDriver->getSubtitle()|escape}{/if}
			{if $recordDriver->getFormats()}
				<br/><small>({implode subject=$recordDriver->getFormats() glue=", " translate=true isPublicFacing=true})</small>
			{/if}
		</h1>

		<div class="row">
			<div class="col-xs-4 col-sm-5 col-md-4 col-lg-3 text-center">
				{if $disableCoverArt != 1}
					<div id="recordCover" class="text-center row">
						<a href="#" onclick="return AspenDiscovery.Hoopla.getLargeCover('{$recordDriver->getUniqueID()}')"><img alt="{translate text='Book Cover' isPublicFacing=true inAttribute=true}" class="img-thumbnail {$coverStyle}" src="{$recordDriver->getBookcoverUrl('medium')}"></a>
					</div>
				{/if}
				{if !empty($showRatings)}
					{include file="GroupedWork/title-rating-full.tpl" showFavorites=0 ratingData=$recordDriver->getRatingData() showNotInterested=false hideReviewButton=true}
				{/if}
			</div>

			<div id="main-content" class="col-xs-8 col-sm-7 col-md-8 col-lg-9">

				{if !empty($error)}
					<div class="row">
						<div class="alert alert-danger">
							{$error}
						</div>
					</div>
				{/if}

				<div class="row">

					<div id="record-details-column" class="col-xs-12 col-sm-12 col-md-9">
						{include file="Hoopla/view-title-details.tpl"}
					</div>

					<div id="recordTools" class="col-xs-12 col-sm-6 col-md-3">
						<div class="btn-toolbar">
							<div class="btn-group btn-group-vertical btn-block">
								{foreach from=$actions item=curAction}
									{if !empty($curAction['class']) && $curAction['class'] == 'lazy-load-circulation-action'}
										{* This is a placeholder for lazy loading circulation actions *}
										<a href="#" class="btn btn-sm {$curAction['btnType']} btn-wrap {$curAction['class']}" style="display: none;"
										   id="{$curAction['id']}"
										   data-user-id="{$curAction['data-user-id']}"
										   data-source="{$curAction['data-source']}"
										   data-record-id="{$curAction['data-record-id']}"
										   data-loading-linked-user="{$curAction['data-loading-linked-user']}"
										   data-show-user-name="{$curAction['data-show-user-name']}"
										   onclick="{$curAction['onclick']}">
											<i class="fas fa-spinner fa-spin" aria-hidden="true"></i> {$curAction['title']}
										</a>
										<script>
											AspenDiscovery.GroupedWork.loadCirculationAction(document.getElementById('{$curAction['id']}'));
										</script>
									{elseif !empty($curAction['staleCache'])}
										{* This action is from stale cache - display it but refresh after page load *}
										<a href="{$curAction.url}" 
										   {if !empty($curAction.target)}target="{$curAction.target}"{/if}
										   {if !empty($curAction.id)}id="{$curAction.id}"{/if}
										   {if !empty($curAction.onclick)}onclick="{$curAction.onclick}"{/if}
										   class="btn btn-sm {if empty($curAction.btnType)}btn-action{else}{$curAction.btnType}{/if} btn-wrap{if !empty($curAction['class'])} {$curAction['class']}{/if} stale-cache-action"
										   data-user-id="{$curAction['data-user-id']|default:''}"
										   data-source="{$curAction['data-source']|default:''}"
										   data-record-id="{$curAction['data-record-id']|default:''}"
										   data-loading-linked-user="{$curAction['data-loading-linked-user']|default:'0'}"
										   data-show-user-name="{$curAction['data-show-user-name']|default:'0'}"
										   data-stale-cache="1"
										   {if !empty($curAction.alt)}title="{translate text=$curAction.alt inAttribute=true isPublicFacing=true}"{/if}>
											{if !empty($curAction.target) && $curAction.target == "_blank"}<i class="fas fa-external-link-alt" role="presentation"></i> {/if}{translate text=$curAction.title isPublicFacing=true}
										</a>
										<script>
											document.addEventListener('DOMContentLoaded', function() {
												setTimeout(function() {
													AspenDiscovery.GroupedWork.refreshStaleCirculationAction(document.getElementById('{$curAction.id}'));
												}, 100);
											});
										</script>
									{elseif !empty($curAction.url) && strlen($curAction.url) > 0}
										<a href="{$curAction.url}" class="btn btn-sm {if empty($curAction.btnType)}btn-action{else}{$curAction.btnType}{/if} btn-wrap{if !empty($curAction['class'])} {$curAction['class']}{/if}" onclick="{if !empty($curAction.requireLogin)}return AspenDiscovery.Account.followLinkIfLoggedIn(this, '{$curAction.url}');{/if}" {if !empty($curAction.alt)}title="{translate text=$curAction.alt inAttribute=true isPublicFacing=true}"{/if}>{translate text=$curAction.title isPublicFacing=true}</a>
									{else}
										<a href="#" class="btn btn-sm {if empty($curAction.btnType)}btn-action{else}{$curAction.btnType}{/if} btn-wrap{if !empty($curAction['class'])} {$curAction['class']}{/if}" onclick="{$curAction.onclick}" {if !empty($curAction.alt)}title="{translate text=$curAction.alt inAttribute=true}"{/if}>{translate text=$curAction.title isPublicFacing=true}</a>
									{/if}
								{/foreach}
							</div>
						</div>
					</div>

					<div class="row">
						<div class="col-xs-12">
							{include file='GroupedWork/result-tools-horizontal.tpl' ratingData=$recordDriver->getRatingData() recordUrl=$recordDriver->getLinkUrl() showMoreInfo=false showNotInterested=false}
						</div>
					</div>

				</div>


			</div>
		</div>

		<div class="row">
			{include file=$moreDetailsTemplate}
		</div>
	</div>
{/strip}
