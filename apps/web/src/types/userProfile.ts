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
