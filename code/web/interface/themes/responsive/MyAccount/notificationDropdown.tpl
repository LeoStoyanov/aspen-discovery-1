{strip}
	{if $userMessages|@count == 0}
		<div class="header-menu-option text-center" style="padding: 10px;">{translate text="No new notifications" isPublicFacing=true}</div>
	{else}
		{foreach from=$userMessages item="userMessage"}
			<div class="notification-item header-menu-option" data-notification-id="{$userMessage->id}">
				<div class="notification-item-title">
					{if $userMessage->messageType == 'list_transfer'}
						{translate text="List Transfer" isPublicFacing=true}
					{elseif $userMessage->messageType|substr:0:11 == 'linked_acct' || $userMessage->messageType == 'confirm_linked_accts'}
						{translate text="Linked Accounts" isPublicFacing=true}
					{else}
						{translate text="Notification" isPublicFacing=true}
					{/if}
				</div>
				<div class="notification-item-body">{$userMessage->message nofilter}</div>
				{if !empty($userMessage->addendum)}
					<div class="notification-item-link">
						<a href="/MyAccount/LinkedAccounts">{translate text=$userMessage->addendum isPublicFacing=true}</a>
					</div>
				{/if}
				<div class="notification-actions">
					{if !empty($userMessage->action1Title) && !empty($userMessage->action1)}
						<button class="btn btn-xs btn-primary notification-action"
								data-notification-id="{$userMessage->id}"
								data-notification-action="{$userMessage->action1|escape:'htmlall'}">
							{translate text=$userMessage->action1Title isPublicFacing=true}
						</button>
					{/if}
					{if !empty($userMessage->action2Title) && !empty($userMessage->action2)}
						<button class="btn btn-xs btn-default notification-action"
								data-notification-id="{$userMessage->id}"
								data-notification-action="{$userMessage->action2|escape:'htmlall'}">
							{translate text=$userMessage->action2Title isPublicFacing=true}
						</button>
					{/if}
					<button class="btn btn-xs btn-link notification-dismiss" data-notification-id="{$userMessage->id}">
						{translate text="Dismiss" isPublicFacing=true}
					</button>
				</div>
			</div>
		{/foreach}
	{/if}
{/strip}
