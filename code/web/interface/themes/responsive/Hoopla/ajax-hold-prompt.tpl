<div>
	{if count($hooplaUsers) > 1}
		<div class="form-group">
			<label class="control-label" for="patronId">{translate text="Place hold for account" isPublicFacing=true}</label>
			<div class="controls">
				<select name="patronId" id="patronId" class="form-control">
					{foreach from=$hooplaUsers item=tmpUser}
						<option value="{$tmpUser->id}">{$tmpUser->getNameAndLibraryLabel()|escape}</option>
					{/foreach}
				</select>
			</div>
		</div>
	{/if}
</div>
