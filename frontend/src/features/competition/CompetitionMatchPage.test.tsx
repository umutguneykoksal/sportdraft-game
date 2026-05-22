import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import type { CompetitionMatchOpenResponse, CompetitionResponse, MatchEvent } from "../../shared/api/contracts";
import { CompetitionMatchPage } from "./CompetitionMatchPage";
import { REACT_ROUTER_FUTURE_FLAGS } from "../../test/reactRouterFuture";
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

const goalEvent: MatchEvent = {
  minute: 10,
  type: "GOAL",
  teamId: "home-1",
  playerId: "player-1",
  playerName: "Striker",
  detail: "Goal",
};

function openMatchResponse(overrides: Partial<CompetitionMatchOpenResponse> = {}): CompetitionMatchOpenResponse {
  return {
    matchId: "match-1",
    roundKey: "SEMI_1",
    homeUserId: "home-1",
    awayUserId: "away-1",
    homeNickname: "Home",
    awayNickname: "Away",
    homeFormation: "FOUR_FOUR_TWO",
    awayFormation: "FOUR_THREE_THREE",
    timeline: [goalEvent],
    homeScore: 1,
    awayScore: 0,
    homePenaltyScore: null,
    awayPenaltyScore: null,
    watchedByMe: false,
    replay: false,
    allPlayersWatched: false,
    watchCount: 0,
    requiredWatchers: 2,
    ...overrides,
  };
}

const ackResponse: CompetitionResponse = {
  competitionId: "comp-1",
  sessionId: "session-1",
  type: "TOURNAMENT",
  status: "ACTIVE",
  tournamentPhase: "SEMIS",
  intermissionEndsAt: null,
  replaysUnlocked: false,
  championUserId: null,
  championNickname: null,
  matches: [],
};

function renderPage(sessionId = "session-1", matchId = "match-1") {
  return render(
    <MemoryRouter
      initialEntries={[`/tournament/${sessionId}/match/${matchId}`]}
      future={REACT_ROUTER_FUTURE_FLAGS}
    >
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
    mockedOpen.mockResolvedValue(openMatchResponse());

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

  it("blocks navigation until mandatory watch completes", async () => {
    mockedOpen.mockResolvedValue(openMatchResponse());

    renderPage();

    expect(await screen.findByRole("button", { name: /Finish watching/i })).toBeInTheDocument();
    expect(screen.queryByText("Hub")).not.toBeInTheDocument();
    expect(mockedAck).not.toHaveBeenCalled();
  });

  it("acks watch and navigates to hub", async () => {
    mockedOpen.mockResolvedValue(
      openMatchResponse({
        homeScore: 2,
        awayScore: 1,
        timeline: [{ ...goalEvent, minute: 55 }],
      })
    );
    mockedAck.mockResolvedValue(ackResponse);

    renderPage();

    await userEvent.click(await screen.findByRole("button", { name: /Finish watching/i }));

    await waitFor(() => {
      expect(mockedAck).toHaveBeenCalledWith("session-1", "match-1");
      expect(screen.getByText("Hub")).toBeInTheDocument();
    });
  });
});
