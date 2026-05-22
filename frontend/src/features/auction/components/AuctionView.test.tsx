import { render, screen } from "@testing-library/react";
import { AuctionView } from "./AuctionView";

describe("AuctionView", () => {
  it("rendersTitleAndChildren", () => {
    render(
      <AuctionView title="Live Auction">
        <p>Bid panel</p>
      </AuctionView>
    );

    expect(screen.getByRole("heading", { name: "Live Auction" })).toBeInTheDocument();
    expect(screen.getByText("Bid panel")).toBeInTheDocument();
  });

  it("usesDefaultTitle", () => {
    render(
      <AuctionView>
        <span>Content</span>
      </AuctionView>
    );

    expect(screen.getByRole("heading", { name: "Auction" })).toBeInTheDocument();
  });
});

export {};
