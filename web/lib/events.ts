export const HIGH = ["protection_lost", "admin_disabled", "password_lockout", "clock_changed", "released"];
export const MEDIUM = ["password_wrong", "blocked_settings", "admin_disable_requested", "forgot_password_requested"];
export const SEVERE = [...HIGH, ...MEDIUM];
