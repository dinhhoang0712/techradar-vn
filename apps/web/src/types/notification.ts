// Domain type cho NotificationsPage/NotificationBell.
export interface Notification {
    id: string;
    type: string;
    title: string;
    body?: string;
    link?: string;
    read: boolean;
    created_at: string;
}
