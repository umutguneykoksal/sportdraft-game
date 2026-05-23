import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { MatchPage } from "./MatchPage";
import { useMatchFlow } from "./hooks/useMatchFlow";
import { REACT_ROUTER_FUTURE_FLAGS } from "../../test/reactRouterFuture";
import type { Formation } from "../../shared/api/contracts";

jest.mock("./hooks/useMatchFlow");
jest.mock("../../shared/api", () => ({
  getClientUserId: jest.fn(() => "11111111-1111-1111-1111-111111111111"),
  getMatchTimeline: jest.fn().mockResolvedValue([]),
}));

const mockedUseMatchFlow = useMatchFlow as jest.MockedFunction<typeof useMatchFlow>;

function defaultMatchFlow(overrides: Partial<ReturnType<typeof useMatchFlow>> = {}) {
  return {
    ready: false,
    loadError: null,
    homeScore: null,
    awayScore: null,
    myTeam: [],
    opponentTeam: [],
    myFormation: "FOUR_FOUR_TWO" as Formation,
    opponentFormation: "FOUR_THREE_THREE" as Formation,
    myNickname: "You",
    opponentNickname: "Opponent",
    homeUserId: null,
    awayUserId: null,
    sessionHomeNickname: "Home",
    sessionAwayNickname: "Away",
    sessionHomeScore: 0,
    sessionAwayScore: 0,
    sessionHomePenaltyScore: 0,
    sessionAwayPenaltyScore: 0,
    myPenaltyScore: 0,
    opponentPenaltyScore: 0,
    winnerUserId: null,
    myShots: 0,
    opponentShots: 0,
    myFouls: 0,
    opponentFouls: 0,
    ...overrides,
  };
}

function renderPage(sessionId = "session-1") {
  return render(
    <MemoryRouter
      initialEntries={[`/match/${sessionId}`]}
      future={REACT_ROUTER_FUTURE_FLAGS}
    >
      <Routes>
        <Route path="/match/:sessionId" element={<MatchPage />} />
        <Route path="/lobby" element={<div>Lobby home</div>} />
      </Routes>
    </MemoryRouter>
  );
}

describe("MatchPage", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    localStorage.clear();
    localStorage.setItem("activeSessionId", "session-1");
  });

  it("showsLoadingWhileWaitingForResult", () => {
    mockedUseMatchFlow.mockReturnValue(defaultMatchFlow());

    renderPage();

    expect(screen.getByText("Waiting for match result...")).toBeInTheDocument();
  });

  it("showsErrorStateWithBackToLobby", async () => {
    mockedUseMatchFlow.mockReturnValue(
      defaultMatchFlow({
        loadError: "Match result is taking longer than expected.",
      })
    );

    renderPage();

    expect(screen.getByText("Could not load match result")).toBeInTheDocument();
    expect(screen.getByText("Match result is taking longer than expected.")).toBeInTheDocument();
    expect(screen.queryByText("Waiting for match result...")).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "Back to Lobby" }));

    expect(screen.getByText("Lobby home")).toBeInTheDocument();
    expect(localStorage.getItem("activeSessionId")).toBeNull();
  });
});

export {};
