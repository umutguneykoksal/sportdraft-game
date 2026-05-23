import { getApiBase, getApiPrefix, parseOrThrow } from "./httpClient";

export type FeedbackCategory = "BUG" | "IDEA" | "OTHER";

export interface SubmitFeedbackRequest {
  category: FeedbackCategory;
  message: string;
  pagePath?: string;
  appVersion?: string;
}

export interface FeedbackSubmittedResponse {
  id: string;
}

export async function submitFeedback(payload: SubmitFeedbackRequest): Promise<FeedbackSubmittedResponse> {
  const response = await fetch(`${getApiBase()}${getApiPrefix()}/feedback`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  return parseOrThrow<FeedbackSubmittedResponse>(response, `submitFeedback failed: ${response.status}`);
}
