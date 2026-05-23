import { render, screen } from "@testing-library/react";
import { AuctionRoundOutcomeToast } from "./AuctionRoundOutcomeToast";

describe("AuctionRoundOutcomeToast", () => {
  it("rendersUnsoldBannerInline", () => {
    render(
      <AuctionRoundOutcomeToast
        notice={{
          roundId: "r1",
          kind: "passed",
          cardName: "Livramento",
          cardRating: 78,
          detail: "no one bought this card",
        }}
      />
    );

    expect(screen.getByTestId("auction-round-outcome")).toBeInTheDocument();
    expect(screen.getByText("Unsold")).toBeInTheDocument();
    expect(screen.getByText(/Livramento/)).toBeInTheDocument();
    expect(screen.queryByText(/no one bought this card/i)).not.toBeInTheDocument();
  });

  it("rendersSoldBannerInline", () => {
    render(
      <AuctionRoundOutcomeToast
        notice={{
          roundId: "r2",
          kind: "sold",
          cardName: "Saka",
          cardRating: 86,
          amountCents: 12,
          winnerLabel: "Alice",
          isMine: false,
        }}
      />
    );

    expect(screen.getByText("Sold")).toBeInTheDocument();
    expect(screen.getByText(/Alice/)).toBeInTheDocument();
    expect(screen.getByText(/12 cr/)).toBeInTheDocument();
  });
});

export {};
