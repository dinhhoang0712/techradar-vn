// Domain types cho InterviewPage: luyện phỏng vấn thử theo lượt (stateless — client giữ history).
export interface InterviewTurn {
    question: string;
    answer: string;
    feedback?: string;
}

export interface InterviewFinalSummary {
    score?: number;
    summary?: string;
}

export interface InterviewTurnResult {
    next_question?: string;
    turn?: number;
    feedback_on_last_answer?: string;
    is_final?: boolean;
    final_summary?: InterviewFinalSummary;
}
