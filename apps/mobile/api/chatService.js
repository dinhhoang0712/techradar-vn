/**
 * chatService.js (api/)
 * Re-export từ services/chatService.js để giữ tương thích
 * với các import pattern dùng `api/chatService`
 */
export {
    checkChatHealth,
    createChatSession,
    getChatHistory,
    getChatSessions,
    sendChatMessage,
    streamChatMessage,
} from '../services/chatService';
