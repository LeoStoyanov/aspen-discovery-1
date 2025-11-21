AspenDiscovery.Notifications = function () {
    let unreadCount = 0;

    return {
		init: function () {
            this.fetchUnreadCount();
            this.listenToSSE();
            this.bindEvents();
        },

        listenToSSE() {
            const connect = () => {
                // Re-establish SSE if the connection resets; retries every 5s until success.
                const eventSource = new EventSource('/MyAccount/AJAX?method=UserNotificationsSSE');

                eventSource.addEventListener('list_transfer', () => {
                    this.incrementUnreadCount();
                    this.refreshDropdown(true);
                });

                eventSource.onerror = () => {
                    eventSource.close();
                    setTimeout(connect, 5000);
                };
            };

            connect();
        },

        fetchUnreadCount() {
            $.getJSON('/MyAccount/AJAX?method=getUnreadNotificationsCount', (data) => {
                if (data.success) {
                    unreadCount = data.count;
                    this.updateBellIcon();
                }
            });
        },

        incrementUnreadCount() {
            unreadCount++;
            this.updateBellIcon();
        },

        updateBellIcon() {
            const badge = $('#notification-bell-badge');
            if (unreadCount > 0) {
                badge.text(unreadCount).show();
            } else {
                badge.hide();
            }
        },

        refreshDropdown(showLoader = false) {
            const notificationMenu = $('#notification-menu');
            if (showLoader) {
                const loadingTemplate = notificationMenu.data('loader-template');
                if (loadingTemplate) {
                    notificationMenu.html(loadingTemplate);
                    notificationMenu.scrollTop(0);
                }
            }
            $.getJSON('/MyAccount/AJAX?method=getNotificationDropdown', (data) => {
                if (data.success) {
                    notificationMenu.html(data.html);
                } else {
                    notificationMenu.html('<div class="header-menu-option text-center">No notifications loaded</div>');
                }
            }).fail(function (jqXHR, textStatus, errorThrown) {
                console.error("Failed to load notifications:", textStatus, errorThrown);
                notificationMenu.html('<div class="header-menu-option text-center text-danger">Error loading notifications</div>');
            });
        },

        bindEvents() {
            const $notiMenu = $('#notification-menu');
            $('#notification-bell-dropdown').on('show.bs.dropdown', () => {
                this.refreshDropdown(true);
            });
            $('#notification-menu-trigger').on('click', () => {
                // Bootstrap handles the toggle, just need to ensure data is loaded.
                if (!$('#notification-bell-dropdown').hasClass('open')) {
                    this.refreshDropdown(true);
                }
            });
            $notiMenu.on('click', '.notification-action', (event) => {
                event.preventDefault();
                const button = $(event.currentTarget);
                const notificationId = button.data('notification-id');
                const action = button.data('notification-action');
                this.executeAction(action, event, button[0]);
                this.markAsRead(notificationId, button[0]);
            });
            $notiMenu.on('click', '.notification-dismiss', (event) => {
                event.preventDefault();
                event.stopPropagation();
                const button = $(event.currentTarget);
                const notificationId = button.data('notification-id');
                this.markAsRead(notificationId, button[0]);
            });
        },

        executeAction(action, event, element) {
            if (!action) {
                return;
            }
            try {
                const fn = new Function('event', 'element', action);
                return fn.call(element, event, element);
            } catch (e) {
                console.error('Failed to execute notification action', e, action);
            }
        },

        markAsRead(notificationId, element) {
            const dismissAnimationMs = 360;
            $.post('/MyAccount/AJAX?method=markNotificationRead', { id: notificationId }, (data) => {
                if (data.success) {
                    const $item = $(element).closest('.notification-item');
                    $item.addClass('notification-item--dismissing');
                    const raf = window.requestAnimationFrame || function (cb) { return setTimeout(cb, 16); };
                    raf(() => {
                        $item.addClass('notification-item--collapsed');
                    });
                    setTimeout(() => {
                        $item.remove();
                        this.ensureEmptyState();
                    }, dismissAnimationMs);
                    unreadCount = Math.max(0, unreadCount - 1);
                    this.updateBellIcon();
                }
            });
        },

        ensureEmptyState() {
            const notificationMenu = $('#notification-menu');
            if (!notificationMenu.find('.notification-item').length) {
                const emptyText = notificationMenu.data('empty-text') || 'No new notifications';
                notificationMenu.html('<div class="header-menu-option text-center" style="padding: 10px;">' + emptyText + '</div>');
            }
        }
    }
}(AspenDiscovery.Notifications || {});

document.addEventListener('DOMContentLoaded', () => {
    if (AspenDiscovery.Notifications) {
        AspenDiscovery.Notifications.init();
    }
});
