import { ReactNode } from "react";

interface AuctionViewProps {
  title?: string;
  children: ReactNode;
}

export function AuctionView({ title = "Auction", children }: AuctionViewProps) {
  return (
    <div style={{ padding: 16, maxWidth: 900, margin: "0 auto", fontFamily: "Arial, sans-serif" }}>
      <h2>{title}</h2>
      {children}
    </div>
  );
}
