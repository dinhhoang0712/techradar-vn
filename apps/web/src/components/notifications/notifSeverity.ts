export type NotifSeverity = 'error' | 'success' | 'info';

// Admin job-completion types all follow a *_DONE / *_FAILED (or *_REPEATED_FAILURE) naming
// convention (see JobCompletionNotifier.java / AnalyticsAdminController.java on the backend) —
// deriving severity from the name means a future *_DONE/*_FAILED type gets correct styling for
// free, without editing an icon/color map every time a new admin job gains a notification.
export function severityOf(type: string): NotifSeverity {
    if (type.includes('FAIL')) return 'error';
    if (type.includes('DONE')) return 'success';
    return 'info';
}
