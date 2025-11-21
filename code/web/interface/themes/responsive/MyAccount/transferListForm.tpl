{strip}
	<form method="post" action="" name="transferListForm" class="form form-horizontal" id="transferListForm">
		<div class="form-group">
			<label for="recipientIdentifier" class="col-sm-3 control-label">{translate text="Recipient Barcode or Username" isPublicFacing=true}</label>
			<div class="col-sm-9">
				<input type="text" id="recipientIdentifier" name="identifier" value="" size="50" class="form-control required" placeholder="{translate text='Enter barcode or username...' isPublicFacing=true}" autofocus>
				<span class="help-block" style="margin-top:0"><small><i class="fas fa-info-circle"></i> {translate text="Enter the library barcode or username of the person you want to transfer this list to." isPublicFacing=true}</small></span>
			</div>
		</div>
		{if $canForceTransfer}
			<div class="form-group">
				<div class="col-sm-offset-3 col-sm-9">
					<div class="checkbox">
						<label>
							<input type="checkbox" id="forceTransfer" name="forceTransfer" value="1">
							{translate text="Force Transfer" isAdminFacing=true}
						</label>
					</div>
					<span class="help-block" style="margin-top:0"><small><i class="fas fa-info-circle"></i> {translate text="When checked, the list will be transferred immediately without requiring recipient approval." isAdminFacing=true}</small></span>
				</div>
			</div>
		{/if}
		<input type="hidden" name="listId" value="{$listId}">
	</form>
{/strip}
<script type="text/javascript">
	{literal}
	$("#transferListForm").validate({
		submitHandler: () => {
			AspenDiscovery.Account.doTransferList();
		}
	});
	{/literal}
</script>

