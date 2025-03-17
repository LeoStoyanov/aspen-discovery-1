AspenDiscovery.Hoopla = (function(){
	return {
		checkOutHooplaTitle: function (hooplaId, patronId, hooplaType) {
			if (Globals.loggedIn) {
				if (typeof patronId === 'undefined') {
					patronId = $('#patronId', '#pickupLocationOptions').val();
				}
				if (typeof hooplaType === 'undefined') {
					hooplaType = 'INSTANT';
				}
				console.log(hooplaType);
				var url = Globals.path + '/Hoopla/'+ hooplaId + '/AJAX';
				var params = {
					'method' : 'checkOutHooplaTitle',
					patronId : patronId,
					hooplaType : hooplaType
				};
				if ($('#stopHooplaConfirmation').prop('checked')){
					params['stopHooplaConfirmation'] = true;
				}
				$.getJSON(url, params, function (data) {
					if (data.success) {
						AspenDiscovery.showMessageWithButtons(data.title, data.message, data.buttons);
						AspenDiscovery.Account.loadMenuData();

					} else if (data.noCopies) {
						AspenDiscovery.closeLightbox(function (){
							var ret = confirm(data.message);
							if (ret === true) {
								AspenDiscovery.Hoopla.placeHold(hooplaId);
							}
						});
					} else {
						AspenDiscovery.showMessage(data.title, data.message);
					}
				}).fail(AspenDiscovery.ajaxFail)
			}else{
				AspenDiscovery.Account.ajaxLogin(null, function(){
					AspenDiscovery.Hoopla.checkOutHooplaTitle(hooplaId, patronId, hooplaType);
				}, false);
			}
			return false;
		},

		getCheckOutPrompts: function (hooplaId) {
			if (Globals.loggedIn) {
				var url = Globals.path + "/Hoopla/" + hooplaId + "/AJAX?method=getCheckOutPrompts";
				$.getJSON(url, function (data) {
					AspenDiscovery.showMessageWithButtons(data.title, data.body, data.buttons);
				}).fail(AspenDiscovery.ajaxFail);
			} else {
				AspenDiscovery.Account.ajaxLogin(null, function () {
					AspenDiscovery.Hoopla.getCheckOutPrompts(hooplaId);
				}, false);
			}
			return false;
		},

		returnCheckout: function (patronId, hooplaId) {
			if (Globals.loggedIn) {
				if (confirm('Are you sure you want to return this title?')) {
					AspenDiscovery.showMessage("Returning Title", "Returning your title in Hoopla.");
					var url = Globals.path + "/Hoopla/" + hooplaId + "/AJAX",
						params = {
							'method': 'returnCheckout'
							,patronId: patronId
						};
					$.getJSON(url, params, function (data) {
						AspenDiscovery.showMessage(data.success ? 'Success' : 'Error', data.message, data.success, data.success);
					}).fail(AspenDiscovery.ajaxFail);
				}
			} else {
				AspenDiscovery.Account.ajaxLogin(null, function () {
					AspenDiscovery.Hoopla.returnCheckout(patronId, hooplaId);
					AspenDiscovery.Account.loadMenuData();
				}, false);
			}
			return false;
		},

		getLargeCover: function (id){
			var url = Globals.path + '/Hoopla/' + id + '/AJAX?method=getLargeCover';
			$.getJSON(url, function (data){
					AspenDiscovery.showMessageWithButtons(data.title, data.modalBody, data.modalButtons);
				}
			);
			return false;
		},

		getHoldPrompts: function(id) {
			if (Globals.loggedIn) {
				AspenDiscovery.loadingMessage();
				$.getJSON(Globals.path + "/Hoopla/AJAX?method=getHoldPrompts&id=" + id, function(data) {
					AspenDiscovery.showMessageWithButtons(data.title, data.body, data.buttons);
				}).fail(function(jqXHR, textStatus, errorThrown) {
					console.log('AJAX error:', textStatus, errorThrown);
					AspenDiscovery.showMessage('Error', 'Failed to load hold options');
				});
			} else {
				AspenDiscovery.Account.followupActions = {
					function: 'AspenDiscovery.Hoopla.getHoldPrompts',
					arguments: [id]
				};
				AspenDiscovery.Account.showLoginForm();
			}
			return false;
		},

		placeHold: function(id) {
			if (Globals.loggedIn) {
				var promptInfo = AspenDiscovery.Hoopla.getHoldPrompts(id);
				if (!promptInfo.promptNeeded) {
					AspenDiscovery.Hoopla.doHold(promptInfo.patronId, id);
				}
			} else {
				AspenDiscovery.Account.ajaxLogin(null, function() {
					AspenDiscovery.Hoopla.placeHold(id);
				});
			}
			return false;
		},

		getHoldPrompts: function(id) {
		var url = Globals.path + "/Hoopla/" + id + "/AJAX?method=getHoldPrompts";
		var result = false;
		$.ajax({
			url: url,
			cache: false,
			success: function(data) {
				result = data;
				if (data.promptNeeded) {
					AspenDiscovery.showMessageWithButtons(data.promptTitle, data.prompts, data.buttons);
				}
			},
			dataType: 'json',
			async: false,
			error: function() {
				AspenDiscovery.showMessage("Error", "An error occurred processing your request in Hoopla. Please try again in a few minutes.");
			}
		});
		return result;
	},

	doHold: function(patronId, id) {
		var url = Globals.path + "/Hoopla/AJAX?method=placeHold&patronId=" + patronId + "&id=" + id;
		$.ajax({
			url: url,
			cache: false,
			success: function(data) {
				AspenDiscovery.closeLightbox(function() {
					if (data.success) {
						AspenDiscovery.showMessage(data.title, data.message);
						AspenDiscovery.Account.loadMenuData();
					} else {
						AspenDiscovery.showMessage("Error", data.message);
					}
				});
			},
			dataType: 'json',
			error: function() {
				AspenDiscovery.showMessage("Error", "An error occurred placing your hold. Please try again in a few minutes.");
			}
		});
		return false;
	},

	cancelHold: function(patronId, recordId) {
		if (confirm('Are you sure you want to cancel this hold?')) {
			var url = Globals.path + "/Hoopla/AJAX?method=cancelHold&patronId=" + patronId + "&recordId=" + recordId;
			$.ajax({
				url: url,
				cache: false,
				success: function(data) {
					if (data.success) {
						AspenDiscovery.showMessage("Hold Cancelled", data.message, true);
						$(".hooplaHold_" + recordId + "_" + patronId).hide();
						AspenDiscovery.Account.loadMenuData();
					} else {
						AspenDiscovery.showMessage("Error Cancelling Hold", data.message, true);
					}
				},
				dataType: 'json',
				async: false,
				error: function() {
					AspenDiscovery.showMessage("Error Cancelling Hold", "An error occurred processing your request in Hoopla. Please try again in a few minutes.", false);
				}
			});
		}
		return false;
	}


}
}(AspenDiscovery.Hoopla || {}));