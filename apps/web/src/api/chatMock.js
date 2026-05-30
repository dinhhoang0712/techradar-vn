// Mock streaming chat responses
const MOCK_RESPONSES = {
    default: [
        'Dựa trên dữ liệu từ **120+ job postings** trên TopDev, VietnamWorks và ITviec (tháng 9/2025 – 3/2026)...\n\n',
        '📊 **Kết quả phân tích:**\n\n',
        '**Golang** đang tăng trưởng mạnh với **+38%** so với năm ngoái. Các công ty tuyển dụng Golang nhiều nhất:\n- 🏢 **Grab Vietnam** – 62 jobs, lương TB ~60 triệu\n- 🏢 **VNG** – 45 jobs, lương TB ~45 triệu  \n- 🏢 **Shopee Vietnam** – 70 jobs, lương TB ~55 triệu\n- 🏢 **MoMo** – 38 jobs, lương TB ~50 triệu\n\n',
        '💡 **Kỹ năng đi kèm cần có:** Docker, Kubernetes, gRPC, Microservices, PostgreSQL\n\n',
        '📈 **Mức lương:** Fresher 20–30tr | Junior 30–45tr | Senior 50–70tr\n\n',
        '*Nguồn: TopDev/VietnamWorks, dữ liệu cập nhật 3/2026*',
    ],
    golang: [
        '🔍 **Phân tích Golang tại Việt Nam** *(dữ liệu 9/2025–3/2026)*\n\n',
        '**Tổng quan:** Golang là ngôn ngữ có tốc độ tăng trưởng đứng thứ 3 trong thị trường Việt Nam với +38% YoY.\n\n',
        '**Top công ty tuyển Golang:**\n| Công ty | Số job | Lương TB |\n|---|---|---|\n| Shopee VN | 70 | ~55tr |\n| Grab VN | 62 | ~60tr |\n| VNG | 45 | ~45tr |\n| MoMo | 38 | ~50tr |\n| Zalo | 30 | ~46tr |\n\n',
        '**Kỹ năng cần có:** Docker (85%), Kubernetes (78%), gRPC (72%), Microservices (80%)\n\n',
        '**Lộ trình học:** Go basics → Goroutines/Channels → REST API → Docker → K8s → Microservices (~6 tháng)\n\n',
        '*📌 Nguồn: 245 job postings từ TopDev + VietnamWorks, cập nhật 3/2026*',
    ],
    roadmap: [
        '🗺️ **Lộ trình cá nhân hóa cho bạn:**\n\n',
        'Với nền tảng **React + Node.js 2 năm**, bạn đang ở vị trí rất tốt để phát triển theo 2 hướng:\n\n',
        '**Hướng 1: Senior Frontend/Fullstack (6–9 tháng)**\n- ✅ TypeScript (nâng cao)\n- ✅ System Design\n- ✅ Performance Optimization\n- ✅ Testing (Jest, Cypress)\n- 🎯 Target: Senior Dev, 45–60tr\n\n',
        '**Hướng 2: AI Engineer (9–12 tháng)**\n- 📚 Python cơ bản → nâng cao (2 tháng)\n- 📚 ML basics: scikit-learn, pandas (2 tháng)\n- 📚 LangChain + RAG + Prompt Engineering (2 tháng)\n- 📚 Deploy với FastAPI + Docker (1 tháng)\n- 🎯 Target: AI/ML Engineer, 50–80tr\n\n',
        '**Skill gap quan trọng nhất:** Python (thiếu), ML foundations (thiếu), LangChain (thiếu)\n\n',
        '💼 **Job phù hợp ngay bây giờ:** Grab (AI team), VNG (Backend/AI), Techcombank (Digital)\n\n',
        '*📌 Dựa trên 89 job phù hợp profile của bạn trên TopDev/VietnamWorks*',
    ],
    ai: [
        '🤖 **AI/ML tại Việt Nam – Báo cáo xu hướng 2025–2026**\n\n',
        '**Tăng trưởng:** +65% YoY – cao nhất trong tất cả tech stacks!\n\n',
        '**Kỹ năng hot nhất trong AI jobs:**\n- Python (95% job yêu cầu)\n- TensorFlow / PyTorch (80%)\n- LangChain / RAG (70% – đang tăng mạnh)\n- Prompt Engineering (65%)\n- MLOps / Docker / K8s (60%)\n\n',
        '**Lương AI Engineer tại VN:**\n- Junior (0–1 năm): 25–40tr\n- Mid (1–3 năm): 40–65tr\n- Senior (3+ năm): 65–100tr+\n\n',
        '**Công ty tuyển AI nhiều nhất:** Grab (48 jobs), VNG (31 jobs), Techcombank (20 jobs)\n\n',
        '*📌 Nguồn: 180 job AI/ML từ TopDev, ITviec, VietnamWorks, 3/2026*',
    ],
};

function detectIntent(message) {
    const m = message.toLowerCase();
    if (m.includes('golang') || m.includes('go lang')) return 'golang';
    if (m.includes('lộ trình') || m.includes('học gì') || m.includes('chuyển hướng') || m.includes('roadmap')) return 'roadmap';
    if (m.includes('ai') || m.includes('ml') || m.includes('machine learning') || m.includes('trí tuệ')) return 'ai';
    return 'default';
}

export async function streamChatResponse(message, onChunk, onDone) {
    const intent = detectIntent(message);
    const chunks = MOCK_RESPONSES[intent] || MOCK_RESPONSES.default;

    for (let i = 0; i < chunks.length; i++) {
        await new Promise(r => setTimeout(r, 180 + Math.random() * 220));
        onChunk(chunks[i]);
    }
    onDone();
}

/* =========================================================================
   THỰC TẾ API /api/v1/chat (Tạm thời được comment vì chưa dùng đến)
========================================================================= */
/*
export const createChatSession = async (token) => {
    // Endpoint: POST /api/v1/chat/session
    // Description: Tạo phiên chat trả về ID phiên
    // Auth: Yes
    const response = await fetch('/api/v1/chat/session', {
        method: 'POST',
        headers: { 'Authorization': `Bearer ${token}` }
    });
    return response.json();
};

export const getChatHistory = async (token, sessionId) => {
    // Endpoint: GET /api/v1/chat/session/{session_id}/messages
    // Description: Lấy lịch sử trò chuyện của phiên hiện tại (khi reload lại)
    // Auth: Yes
    const response = await fetch(`/api/v1/chat/session/${sessionId}/messages`, {
        method: 'GET',
        headers: { 'Authorization': `Bearer ${token}` }
    });
    return response.json();
};

export const sendChatMessageSSE = async (token, sessionId, messagePayload) => {
    // Endpoint: POST /api/v1/chat/session/{session_id}/messages
    // Description: Gửi tin nhắn mới đến chatbot và nhận phản hồi (trả về SSE)
    // Auth: Yes
    const response = await fetch(`/api/v1/chat/session/${sessionId}/messages`, {
        method: 'POST',
        headers: { 
            'Authorization': `Bearer ${token}`,
            'Content-Type': 'application/json'
        },
        body: JSON.stringify(messagePayload)
    });
    
    // Vì đây là luồng SSE, ta sẽ lấy stream từ response thay vì `.json()`
    return response; 
};
*/
