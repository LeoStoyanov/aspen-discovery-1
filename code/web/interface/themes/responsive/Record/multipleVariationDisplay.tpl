{strip}
	<div class="col-sm-12">
		{* These are all of the grouping records for the given MARC record*}
		{foreach from=$recordDriver->getRecordVariations() item=record}
			<div class="row related-manifestation grouped" >
				{* Display Format Button (does nothing) *}
				<div class="col-tn-4 col-xs-4 col-md-3 manifestation-format">
					<a class="btn btn-primary btn-wrap">
						{translate text=$record->variationFormat isPublicFacing=true}
					</a>
				</div>
				{* Display Item Status and Info *}
				<div class="col-tn-8 col-xs-8 col-md-5 col-lg-6">
					{include file='GroupedWork/statusIndicator.tpl' statusInformation=$record->getStatusInformation() viewingIndividualRecord=0}
					{if $record->showCopySummary()}
						{include file='GroupedWork/copySummary.tpl' summary=$record->getItemsDisplayedByDefault($record->variationId) totalCopies=$record->getCopies() itemSummaryId=$workId recordViewUrl=$record->getUrl() format=$record->variationFormat isEContent=$record->isEContent()}
					{/if}
				</div>
				{* Display Hold/Action Button *}
				<div class="col-tn-8 col-tn-offset-4 col-xs-8 col-xs-offset-4 col-md-4 col-md-offset-0 col-lg-3 manifestation-actions">
					<div class="btn-toolbar">
						<div class="btn-group btn-group-vertical btn-block">
							{if $record->isHoldable() || $record->isEContent()}
							{* actions *}
							{foreach from=$record->getActions($record->variationId) item=curAction}
								{if !empty($curAction['class']) && $curAction['class'] == 'lazy-load-circulation-action'}
									<a href="#" class="btn btn-sm {if empty($curAction['btnType'])}btn-action{else}{$curAction['btnType']}{/if} btn-wrap {$curAction['class']}" style="display: none;"
									   {if !empty($curAction['id'])}id="{$curAction['id']}"{/if}
									   data-user-id="{$curAction['data-user-id']|default:0}"
									   data-source="{$curAction['data-source']|default:''}"
									   data-record-id="{$curAction['data-record-id']|default:''}"
									   data-loading-linked-user="{$curAction['data-loading-linked-user']|default:0}"
									   onclick="{$curAction['onclick']}"
											{if !empty($curAction['alt'])}title="{translate text=$curAction['alt'] inAttribute=true}"{/if}>
										{$curAction['title']}
									</a>
									<script>
										AspenDiscovery.GroupedWork.loadCirculationAction(document.getElementById('{$curAction['id']}'));
									</script>
								{else}
									<a href="{$curAction.url}" {if !empty($curAction.target)}target="{$curAction.target}"{/if} {if !empty($curAction.onclick)}onclick="{$curAction.onclick}"{/if} class="btn btn-sm {if empty($curAction.btnType)}btn-action{else}{$curAction.btnType}{/if} btn-wrap">{if !empty($curAction.target) && $curAction.target == "_blank"}<i class="fas fa-external-link-alt" role="presentation"></i> {/if}{$curAction.title}</a>
								{/if}
							{/foreach}
							{/if}
						</div>
					</div>
				</div>
			</div>
		{/foreach}
	</div>
{/strip}