import { apiClient } from '../utils/apiClient';

// POST /interview — 1 lượt phỏng vấn thử. history rỗng = bắt đầu buổi mới (stateless,
// client giữ và gửi lại toàn bộ lịch sử câu hỏi-trả lời mỗi lần gọi).
export const runInterviewTurn = async ({ targetRole, targetCompany, history = [] }) => {
    return await apiClient('/interview', {
        method: 'POST',
        body: JSON.stringify({
            target_role: targetRole,
            target_company: targetCompany || null,
            history: history.map(h => ({ question: h.question, answer: h.answer })),
        }),
    });
};
