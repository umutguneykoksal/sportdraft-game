import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { CompetitionMatchPage } from "./CompetitionMatchPage";
import {
  ackCompetitionMatchWatch,
  openCompetitionMatch,
} from "../../shared/api";

jest.mock("../../shared/api", () => ({
  openCompetitionMatch: jest.fn(),
  ackCompetitionMatchWatch: jest.fn(),
}));

jest.mock("../match/components/MatchCommentary", () => ({
  MatchCommentary: ({ onComplete }: { onComplete: () => void }) => (
    <button type="button" onClick={onComplete}>
      Finish watching
    </button>
  ),
}));

const mockedOpen = openCompetitionMatch as jest.MockedFunction<typeof openCompetitionMatch>;
const mockedAck = ackCompetitionMatchWatch as jest.MockedFunction<typeof ackCompetitionMatchWatch>;

const pageConfig = {
  hubPath: (sessionId: string) => `/tournament/${sessionId}`,
  fallbackPath: "/",
  backLabel: "Back to hub",
};

function renderPage(sessionId = "session-1", matchId = "match-1") {
  return render(
    <MemoryRouter initialEntries={[`/tournament/${sessionId}/match/${matchId}`]}>
      <Routes>
        <Route
          path="/tournament/:sessionId/match/:matchId"
          element={<CompetitionMatchPage config={pageConfig} />}
        />
        <Route path="/tournament/:sessionId" element={<div>Hub</div>} />
      </Routes>
    </MemoryRouter>
  );
}

describe("CompetitionMatchPage", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it("shows loading then match payload", async () => {
    mockedOpen.mockResolvedValue({
      matchId: "match-1",
      roundKey: "SEMI_1",
      homeUserId: "home-1",
      awayUserId: "away-1",
      homeNickname: "Home",
      awayNickname: "Away",
      homeScore: 1,
      awayScore: 0,
      timeline: [{ minute: 10, type: "GOAL", teamId: "home-1", playerName: "Striker", detail: "Goal" }],
    });

    renderPage();

    expect(screen.getByText(/Preparing match simulation/i)).toBeInTheDocument();

    expect(await screen.findByText("Home vs Away")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Finish watching/i })).toBeInTheDocument();
    expect(mockedOpen).toHaveBeenCalledWith("session-1", "match-1", false);
  });

  it("shows error when open fails", async () => {
    mockedOpen.mockRejectedValue(new Error("Network down"));

    renderPage();

    expect(await screen.findByText("Network down")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Back to hub/i })).toBeInTheDocument();
  });

  it("acks watch and navigates to hub", async () => {
    mockedOpen.mockResolvedValue({
      matchId: "match-1",
      roundKey: "SEMI_1",
      homeUserId: "home-1",
      awayUserId: "away-1",
      homeNickname: "Home",
      awayNickname: "Away",
      homeScore: 2,
      awayScore: 1,
      timeline: [{ minute: 55, type: "GOAL", teamId: "home-1", playerName: "Striker", detail: "Goal" }],
    });
    mockedAck.mockResolvedValue({
      competitionId: "comp-1",
      sessionId: "session-1",
      matches: [],
    });

    renderPage();

    await userEvent.click(await screen.findByRole("button", { name: /Finish watching/i }));

    await waitFor(() => {
      expect(mockedAck).toHaveBeenCalledWith("session-1", "match-1");
      expect(screen.getByText("Hub")).toBeInTheDocument();
    });
  });
});
