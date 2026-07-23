// Small shared helper next to the token handling in apiClient.js.
// Centralizes the "is the user logged in" check so callers don't each
// read localStorage directly (and so the check has one place to update
// if the token storage mechanism ever changes).
export const isAuthenticated = (): boolean => Boolean(localStorage.getItem('access_token'));
