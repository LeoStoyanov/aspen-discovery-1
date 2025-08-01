{strip}
<div class="btn-toolbar">
	<div class="btn-group btn-group-vertical btn-block">
		{* actions *}
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
			{else}
				<a href="{$curAction.url}" {if !empty($curAction.target)}target="{$curAction.target}"{/if} {if !empty($curAction.id)}id="{$curAction.id}"{/if} {if !empty($curAction.onclick)}onclick="{$curAction.onclick}"{/if} class="btn btn-sm {if empty($curAction.btnType)}btn-action{else}{$curAction.btnType}{/if} btn-wrap{if !empty($curAction['class'])} {$curAction['class']}{/if}">{if !empty($curAction.target) && $curAction.target == "_blank"}<i class="fas fa-external-link-alt" role="presentation"></i> {/if}{$curAction.title}</a>
			{/if}
		{/foreach}
	</div>
</div>
{/strip}