{if is_array($campaign)}
	{if $campaign.rewardType == 1}
		{if $displayPlaceholderImage}
			{if $placeholderImage}
				<img src="/files/original/{$placeholderImage}" alt="Reward Placeholder Image" style="width:100px; height:100px; padding:10px;" />
			{else}
				{include file="MyAccount/digital-reward-placeholder.tpl"}
			{/if}
		{else}
			{if $campaign.rewardExists}
				<img src="{$campaign.$imageProperty}" alt="{$campaign.rewardName}" style="max-width:100px; max-height:100px; padding:10px;" />
			{/if}
		{/if}
	{/if}
{else}
	{if $campaign->rewardType == 1}
		{if $displayPlaceholderImage}
			{if $placeholderImage}
				<img src="/files/original/{$placeholderImage}" alt="Reward Placeholder Image" style="width:100px; height:100px; padding:10px;" />
			{else}
				{include file="MyAccount/digital-reward-placeholder.tpl"}
			{/if}
		{else}
			{if $campaign->rewardExists}
				<img src="{$campaign->$imageProperty}" alt="{$campaign->rewardName}" style="max-width:100px; max-height:100px; padding:10px;" />
			{/if}
		{/if}
	{/if}
{/if}