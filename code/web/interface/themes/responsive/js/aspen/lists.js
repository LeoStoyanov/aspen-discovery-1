AspenDiscovery.Lists = (function(){
	// noinspection JSUnusedGlobalSymbols
	return {
		addToHomePage: function(listId){
			AspenDiscovery.Account.ajaxLightbox(Globals.path + '/MyAccount/AJAX?method=getAddBrowseCategoryFromListForm&listId=' + listId, true);
			return false;
		},

		editListAction: function (){
			$('#listDescription,#listTitle,#FavEdit,.listViewButton').hide();
			$('#listEditControls,#FavSave,.listEditButton').show();
			return false;
		},

		cancelEditListAction: function (){
			$('#listDescription,#listTitle,#FavEdit,.listViewButton').show();
			$('#listEditControls,#FavSave,.listEditButton').hide();
			return false;
		},

		submitListForm: function(action){
			$('#myListActionHead').val(action);
			$('#myListFormHead').submit();
			AspenDiscovery.Account.loadListData();
			return false;
		},

		makeListPublicAction: function (){
			return this.submitListForm('makePublic');
		},

		makeListPrivateAction: function (){
			return this.submitListForm('makePrivate');
		},

		deleteListAction(){
			const messageTitle = "{translate text=\"Delete List?\" isPublicFacing=true}";
			const messageBody = "{translate text=\"Are you sure you want to delete this entire list? The list and all titles within it will be soft-deleted and can be restored within 30 days.\" isPublicFacing=true}<br/><br/>" +
				"<div>" +
				"<input type='checkbox' id='optOutSoftDeletion' style='margin-right: 5px;'>" +
				"<label class='form-check-label' for='optOutSoftDeletion'>{translate text=\"Opt Out of Soft Deletion\" isPublicFacing=true}</label>" +
				"</div>";

			let buttons = "<button id='confirmDeleteList' class='tool btn btn-danger' onclick='AspenDiscovery.Lists.doDeleteList()'><span class='fas fa-spinner fa-spin' style='display:none; margin-right: 4px;'></span>{translate text=\"Yes\" isPublicFacing=true}</button>";
			buttons += "<button id='cancelDeleteList' class='tool btn btn-default' onclick='AspenDiscovery.closeLightbox()'>{translate text=\"No\" isPublicFacing=true}</button>";
			AspenDiscovery.showMessageWithButtons(messageTitle, messageBody, buttons, false, '', false, false, true);
			return false;
		},

		deleteEntryFromList: function (listId, listEntryId){
			window.location.href = Globals.path + '/MyAccount/MyList/' + listId + '?delete=' + listEntryId;
		},

		doDeleteList() {
			$('#confirmDeleteList .fa-spinner').show();
			$('#confirmDeleteList').prop('disabled', true);
			const hardDelete = $('#optOutSoftDeletion').is(':checked');

			if (hardDelete) {
				this.submitListForm('deleteListHard');
			} else {
				this.submitListForm('deleteList');
			}
		},

		updateListAction: function (){
			return this.submitListForm('saveList');
		},

		emailListAction: function (listId) {
			var urlToDisplay = Globals.path + '/MyAccount/AJAX';
			AspenDiscovery.loadingMessage();
			$.getJSON(urlToDisplay, {
					method  : 'getEmailMyListForm'
					,listId : listId
				},
				function(data){
					AspenDiscovery.showMessageWithButtons(data.title, data.modalBody, data.modalButtons);
				}
			);
			return false;
		},

		sendMyListEmail: function () {
			var url = Globals.path + "/MyAccount/AJAX";

			$.getJSON(url,
				{ // form inputs passed as data
					listId   : $('#emailListForm input[name="listId"]').val()
					,to      : $('#emailListForm input[name="to"]').val()
					,from    : $('#emailListForm input[name="from"]').val()
					,message : $('#emailListForm textarea[name="message"]').val()
					,method  : 'sendMyListEmail'
				},
				function(data) {
					if (data.result) {
						AspenDiscovery.showMessage("Success", data.message);
					} else {
						AspenDiscovery.showMessage("Error", data.message);
					}
				}
			);
		},

		citeListAction: function (id) {
			return AspenDiscovery.Account.ajaxLightbox(Globals.path + '/MyAccount/AJAX?method=getCitationFormatsForm&listId=' + id, false);
			//return false;
			//TODO: ajax call not working
		},

		processCiteListForm: function(){
			$("#citeListForm").submit();
		},

		batchAddToListAction: function (id){
			return AspenDiscovery.Account.ajaxLightbox(Globals.path + '/MyAccount/AJAX/?method=getBulkAddToListForm&listId=' + id);
			//return false;
		},

		processBulkAddForm: function(){
			$("#bulkAddToList").submit();
		},

		changeList: function (){
			var availableLists = $("#availableLists");
			window.location = Globals.path + "/MyAccount/MyList/" + availableLists.val();
		},

		printListAction: function (){
			window.print();
			return false;
		},

		printListWithDescriptions: function (){
			// Ensure descriptions are shown.
			$('body').removeClass('no-print-descriptions');
			window.print();
			return false;
		},

		printListWithoutDescriptions: function (){
			// Hide descriptions during print.
			$('body').addClass('no-print-descriptions');
			window.print();
			return false;
		},

		importListsFromClassic: function (){
			if (confirm("This will import any lists you had defined in the old catalog.  This may take several minutes depending on the size of your lists. Are you sure you want to continue?")){
				window.location = Globals.path + "/MyAccount/ImportListsFromClassic";
			}
			return false;
		},
		getUploadListCoverForm: function (id){
			var url = Globals.path + '/MyAccount/AJAX?id=' + id + '&method=getUploadListCoverForm';
			$.getJSON(url, function (data){
					AspenDiscovery.showMessageWithButtons(data.title, data.modalBody, data.modalButtons);
				}
			);
			return false;
		},

		uploadListCover: function (id){
			var url = Globals.path + '/MyAccount/AJAX?id=' + id + '&method=uploadListCover';
			var uploadCoverData = new FormData($("#uploadListCoverForm")[0]);
			$.ajax({
				url: url,
				type: 'POST',
				data: uploadCoverData,
				dataType: 'json',
				success: function(data) {
					AspenDiscovery.showMessage(data.title, data.message, true, data.success);
				},
				async: false,
				contentType: false,
				processData: false
			});
			return false;
		},

		getUploadListCoverFormByURL: function (id){
			var url = Globals.path + '/MyAccount/AJAX?id=' + id + '&method=getUploadListCoverFormByURL';
			$.getJSON(url, function (data){
					AspenDiscovery.showMessageWithButtons(data.title, data.modalBody, data.modalButtons);
				}
			);
			return false;
		},

		uploadListCoverByURL: function (id){
			var url = Globals.path + '/MyAccount/AJAX?id=' + id + '&method=uploadListCoverByURL';
			var uploadCoverData = new FormData($("#uploadListCoverFormByURL")[0]);
			$.ajax({
				url: url,
				type: 'POST',
				data: uploadCoverData,
				dataType: 'json',
				success: function(data) {
					AspenDiscovery.showMessage(data.title, data.message, true, data.success);
				},
				async: false,
				contentType: false,
				processData: false
			});
			return false;
		},

		removeUploadedListCover: function (id){
			var url = Globals.path + '/MyAccount/AJAX?listId=' + id + '&method=removeUploadedListCover';
			$.getJSON(url, function (data){
				if (data.success) {
					$("#removeUploadedListCover").hide();
				}
				AspenDiscovery.showMessage(data.title, data.message);
			});
			return false;
		},

		changeWeight: function(listEntryId, direction) {
			var url = Globals.path + '/MyAccount/AJAX';
			var params = {
				method: 'updateWeight',
				listEntryId: listEntryId,
				direction: direction
			};
			$.getJSON(url, params, function (data) {
				if (data.success){
					var entry1 = $(listEntryId);
					var entry2 = $(data.swappedWithId);
					if (direction === 'up'){
						entry2.before(entry1);
					}else{
						entry1.before(entry2);
					}
					location.reload();
				} else {
					AspenDiscovery.showMessage('An error occurred', data.message);
				}
			});
			return false;
		}
	};
}(AspenDiscovery.Lists || {}));