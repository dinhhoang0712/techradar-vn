// Domain type cho UserProfile — backend trả về dạng lồng { user, profile } hoặc phẳng tuỳ endpoint,
// nên các field ở đây đều optional và UserProfile.tsx tự dò cả 2 dạng khi map.
export interface UserProfileData {
    id?: string;
    user?: {
        id?: string;
        full_name?: string;
        email?: string;
        role?: string;
    };
    profile?: {
        job_role?: string;
        bio?: string;
        location?: string;
        technologies?: string[];
        avatar_url?: string;
        notify_inapp?: boolean;
        notify_email?: boolean;
    };
    full_name?: string;
    email?: string;
    role?: string;
    job_role?: string;
    bio?: string;
    location?: string;
    technologies?: string[];
    avatar_url?: string;
    notify_inapp?: boolean;
    notify_email?: boolean;
}

export interface UpdateProfilePayload {
    full_name?: string;
    bio?: string;
    job_role?: string;
    location?: string;
    password?: string;
    technologies?: string[];
    notify_inapp?: boolean;
    notify_email?: boolean;
}

// GET /user/data-export — GDPR data portability. Không gồm chat/messaging/notification/follows
// (xem UserDataExport.java bên backend).
export interface UserDataExport {
    account: {
        id: string;
        email: string;
        full_name?: string;
        role?: string;
        status?: string;
        subscription_tier?: string;
        created_at?: string;
    };
    profile: {
        job_role?: string;
        bio?: string;
        location?: string;
        avatar_url?: string;
        technologies?: string[];
    };
    posts: { id: string; content: string; created_at?: string }[];
    comments: { id: string; content: string; created_at?: string }[];
}
