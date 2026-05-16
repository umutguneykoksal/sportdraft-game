import { useCallback, useEffect, useRef, useState } from "react";
import type { Formation, AuctionTeamCardResponse } from "../../../shared/api/contracts";
import { FORMATION_DOTS } from "../../../shared/formations";

// Pitch dimensions (normalized to 100x100 for percentage-based rendering)
const PITCH_WIDTH = 100;
const PITCH_HEIGHT = 100;

// Simulation parameters
const PLAYER_SPEED = 1.2; // Max movement per tick
const BALL_SPEED = 3; // Ball movement speed
const PASS_CHANCE = 0.03; // Chance to pass per tick when has ball
const SHOT_CHANCE = 0.01; // Chance to shoot when in attacking third
const TACKLE_RANGE = 5; // Distance for tackle attempt
const POSSESSION_CHANGE_CHANCE = 0.02; // Chance to lose ball

interface Player {
  id: string;
  name: string;
  position: string;
  rating: number;
  x: number;
  y: number;
  baseX: number;
  baseY: number;
  isHome: boolean;
  hasBall: boolean;
  targetX: number;
  targetY: number;
}

interface Ball {
  x: number;
  y: number;
  targetX: number;
  targetY: number;
  inFlight: boolean;
  targetPlayerId: string | null;
}

export interface PlayerPosition {
  id: string;
  name: string;
  x: number;
  y: number;
  isHome: boolean;
  hasBall: boolean;
  position: string;
}

export interface SimulationState {
  players: PlayerPosition[];
  ballX: number;
  ballY: number;
  homeScore: number;
  awayScore: number;
  lastAction: string;
  passLine: { fromX: number; fromY: number; toX: number; toY: number } | null;
}

// Seeded random number generator for deterministic simulation
class SeededRandom {
  private seed: number;

  constructor(seed: number) {
    this.seed = seed;
  }

  next(): number {
    this.seed = (this.seed * 1103515245 + 12345) & 0x7fffffff;
    return this.seed / 0x7fffffff;
  }

  nextInt(min: number, max: number): number {
    return Math.floor(this.next() * (max - min + 1)) + min;
  }
}

function createPlayers(
  formation: Formation,
  cards: AuctionTeamCardResponse[],
  isHome: boolean
): Player[] {
  const dots = FORMATION_DOTS[formation];
  const pool: Record<string, AuctionTeamCardResponse[]> = {
    GK: [], DEF: [], CDM: [], CM: [], CAM: [], FWD: [],
  };

  for (const c of cards) {
    pool[c.position]?.push(c);
  }

  return dots.map((dot, idx) => {
    const card = pool[dot.pos]?.shift();
    
    // Formation dots: GK at y=92 (bottom), FWD at y=16 (top)
    // Home team: keep as-is (attacking upward, GK at bottom)
    // Away team: flip (attacking downward, GK at top)
    const baseX = dot.x;
    const baseY = isHome ? dot.y : (100 - dot.y);

    return {
      id: `${isHome ? "home" : "away"}-${idx}`,
      name: card?.cardName ?? `Player ${idx + 1}`,
      position: dot.pos,
      rating: card?.rating ?? 50,
      x: baseX,
      y: baseY,
      baseX,
      baseY,
      isHome,
      hasBall: false,
      targetX: baseX,
      targetY: baseY,
    };
  });
}

function distance(x1: number, y1: number, x2: number, y2: number): number {
  return Math.sqrt((x2 - x1) ** 2 + (y2 - y1) ** 2);
}

function moveToward(current: number, target: number, speed: number): number {
  const diff = target - current;
  if (Math.abs(diff) <= speed) return target;
  return current + Math.sign(diff) * speed;
}

function clamp(value: number, min: number, max: number): number {
  return Math.max(min, Math.min(max, value));
}

export function useFootballSimulation(
  homeFormation: Formation,
  awayFormation: Formation,
  homeCards: AuctionTeamCardResponse[],
  awayCards: AuctionTeamCardResponse[],
  isPlaying: boolean,
  seed: number = 12345
) {
  const [state, setState] = useState<SimulationState>({
    players: [],
    ballX: 50,
    ballY: 50,
    homeScore: 0,
    awayScore: 0,
    lastAction: "",
    passLine: null,
  });

  const playersRef = useRef<Player[]>([]);
  const ballRef = useRef<Ball>({ x: 50, y: 50, targetX: 50, targetY: 50, inFlight: false, targetPlayerId: null });
  const rngRef = useRef(new SeededRandom(seed));
  const scoreRef = useRef({ home: 0, away: 0 });
  const lastActionRef = useRef("");
  const tickRef = useRef(0);
  const passLineRef = useRef<{ fromX: number; fromY: number; toX: number; toY: number } | null>(null);
  const intervalRef = useRef<number | null>(null);

  // Initialize players
  useEffect(() => {
    const homePlayers = createPlayers(homeFormation, homeCards, true);
    const awayPlayers = createPlayers(awayFormation, awayCards, false);
    playersRef.current = [...homePlayers, ...awayPlayers];
    
    // Give ball to a home midfielder
    const homeMidfielder = playersRef.current.find(p => p.isHome && (p.position === "CM" || p.position === "CAM"));
    if (homeMidfielder) {
      homeMidfielder.hasBall = true;
      ballRef.current.x = homeMidfielder.x;
      ballRef.current.y = homeMidfielder.y;
    }

    rngRef.current = new SeededRandom(seed);
    scoreRef.current = { home: 0, away: 0 };
    lastActionRef.current = "Kick off!";
    
    updateState();
  }, [homeFormation, awayFormation, homeCards, awayCards, seed]);

  const updateState = useCallback(() => {
    setState({
      players: playersRef.current.map(p => ({
        id: p.id,
        name: p.name,
        x: p.x,
        y: p.y,
        isHome: p.isHome,
        hasBall: p.hasBall,
        position: p.position,
      })),
      ballX: ballRef.current.x,
      ballY: ballRef.current.y,
      homeScore: scoreRef.current.home,
      awayScore: scoreRef.current.away,
      lastAction: lastActionRef.current,
      passLine: passLineRef.current,
    });
  }, []);

  const simulateTick = useCallback(() => {
    const players = playersRef.current;
    const ball = ballRef.current;
    const rng = rngRef.current;
    tickRef.current++;

    // Clear pass line after a few ticks
    if (passLineRef.current && tickRef.current % 5 === 0) {
      passLineRef.current = null;
    }

    // Find player with ball
    const playerWithBall = players.find(p => p.hasBall);
    const attackingTeamIsHome = playerWithBall?.isHome ?? true;

    // Move ball if in flight
    if (ball.inFlight) {
      ball.x = moveToward(ball.x, ball.targetX, BALL_SPEED);
      ball.y = moveToward(ball.y, ball.targetY, BALL_SPEED);

      // Check if ball reached target
      if (distance(ball.x, ball.y, ball.targetX, ball.targetY) < 2) {
        ball.inFlight = false;
        
        // Check if it was a shot (target near goal)
        const isShot = ball.targetY < 5 || ball.targetY > 95;
        if (isShot) {
          // Goal chance based on position
          const goalChance = rng.next();
          if (goalChance < 0.3) {
            // GOAL!
            if (ball.targetY < 5) {
              scoreRef.current.home++;
              lastActionRef.current = "GOAL! Home team scores!";
            } else {
              scoreRef.current.away++;
              lastActionRef.current = "GOAL! Away team scores!";
            }
            // Reset to center
            ball.x = 50;
            ball.y = 50;
            const centerPlayer = players.find(p => p.position === "CM" && p.isHome !== attackingTeamIsHome);
            if (centerPlayer) {
              centerPlayer.hasBall = true;
              ball.x = centerPlayer.x;
              ball.y = centerPlayer.y;
            }
          } else {
            lastActionRef.current = "Shot saved!";
            // Give ball to goalkeeper
            const gk = players.find(p => p.position === "GK" && (ball.targetY < 5 ? !p.isHome : p.isHome));
            if (gk) {
              gk.hasBall = true;
              ball.x = gk.x;
              ball.y = gk.y;
            }
          }
        } else {
          // Pass received
          const targetPlayer = players.find(p => p.id === ball.targetPlayerId);
          if (targetPlayer) {
            // Interception chance
            const nearestOpponent = players
              .filter(p => p.isHome !== targetPlayer.isHome)
              .sort((a, b) => distance(a.x, a.y, ball.x, ball.y) - distance(b.x, b.y, ball.x, ball.y))[0];
            
            const interceptDist = nearestOpponent ? distance(nearestOpponent.x, nearestOpponent.y, ball.x, ball.y) : 100;
            
            if (interceptDist < 5 && rng.next() < 0.4) {
              // Intercepted!
              nearestOpponent.hasBall = true;
              ball.x = nearestOpponent.x;
              ball.y = nearestOpponent.y;
              lastActionRef.current = `Intercepted by ${nearestOpponent.name}!`;
            } else {
              targetPlayer.hasBall = true;
              ball.x = targetPlayer.x;
              ball.y = targetPlayer.y;
              lastActionRef.current = `${targetPlayer.name} receives the ball`;
            }
          }
        }
        ball.targetPlayerId = null;
      }
    }

    // Calculate targets for all players
    for (const player of players) {
      const isAttacking = player.isHome === attackingTeamIsHome;
      const distToBall = distance(player.x, player.y, ball.x, ball.y);

      if (player.hasBall) {
        // Player with ball - decide action
        const inAttackingThird = player.isHome ? player.y < 35 : player.y > 65;
        
        // Shooting
        if (inAttackingThird && rng.next() < SHOT_CHANCE) {
          player.hasBall = false;
          ball.inFlight = true;
          ball.targetX = 50 + (rng.next() - 0.5) * 20;
          ball.targetY = player.isHome ? 2 : 98;
          passLineRef.current = { fromX: ball.x, fromY: ball.y, toX: ball.targetX, toY: ball.targetY };
          lastActionRef.current = `${player.name} shoots!`;
        }
        // Passing
        else if (rng.next() < PASS_CHANCE) {
          const teammates = players.filter(p => p.isHome === player.isHome && p.id !== player.id);
          if (teammates.length > 0) {
            // Prefer forward passes
            const forwardTeammates = teammates.filter(p => 
              player.isHome ? p.y < player.y : p.y > player.y
            );
            const passTargets = forwardTeammates.length > 0 ? forwardTeammates : teammates;
            const target = passTargets[rng.nextInt(0, passTargets.length - 1)];
            
            player.hasBall = false;
            ball.inFlight = true;
            ball.targetX = target.x + (rng.next() - 0.5) * 5;
            ball.targetY = target.y + (rng.next() - 0.5) * 5;
            ball.targetPlayerId = target.id;
            passLineRef.current = { fromX: ball.x, fromY: ball.y, toX: ball.targetX, toY: ball.targetY };
            lastActionRef.current = `${player.name} passes to ${target.name}`;
          }
        }
        // Dribble forward
        else {
          player.targetX = player.x + (rng.next() - 0.5) * 3;
          player.targetY = player.isHome ? player.y - 2 : player.y + 2;
          
          // Chance to lose ball
          const nearestDefender = players
            .filter(p => p.isHome !== player.isHome)
            .sort((a, b) => distance(a.x, a.y, player.x, player.y) - distance(b.x, b.y, player.x, player.y))[0];
          
          if (nearestDefender && distance(nearestDefender.x, nearestDefender.y, player.x, player.y) < TACKLE_RANGE) {
            if (rng.next() < POSSESSION_CHANGE_CHANCE * 2) {
              player.hasBall = false;
              nearestDefender.hasBall = true;
              ball.x = nearestDefender.x;
              ball.y = nearestDefender.y;
              lastActionRef.current = `${nearestDefender.name} wins the ball!`;
            }
          }
        }
      } else {
        // Player without ball - positional play
        let targetX = player.baseX;
        let targetY = player.baseY;

        // Shift based on ball position
        const ballShiftX = (ball.x - 50) * 0.3;
        const ballShiftY = (ball.y - 50) * 0.2;

        if (isAttacking) {
          // Attacking movement
          if (player.position === "FWD") {
            // Forwards make runs
            targetY = player.isHome ? player.baseY - 20 : player.baseY + 20;
            targetX = player.baseX + ballShiftX + (rng.next() - 0.5) * 15;
          } else if (player.position === "CAM" || player.position === "CM") {
            // Midfielders support
            targetY = player.isHome ? player.baseY - 12 : player.baseY + 12;
            targetX = player.baseX + ballShiftX;
          } else if (player.position === "DEF" || player.position === "CDM") {
            // Defenders hold position, shift horizontally
            targetX = player.baseX + ballShiftX * 0.5;
            targetY = player.isHome ? player.baseY - 5 : player.baseY + 5;
          }
        } else {
          // Defending movement
          if (player.position === "GK") {
            // Goalkeeper covers angle
            targetX = 50 + (ball.x - 50) * 0.2;
            targetY = player.baseY;
          } else if (player.position === "DEF" || player.position === "CDM") {
            // Defenders track ball
            targetX = player.baseX + ballShiftX * 0.6;
            targetY = player.baseY + ballShiftY * 0.3;
            
            // If ball is close, press
            if (distToBall < 20) {
              targetX = player.x + (ball.x - player.x) * 0.3;
              targetY = player.y + (ball.y - player.y) * 0.3;
            }
          } else if (player.position === "CM" || player.position === "CAM") {
            // Midfielders drop back
            targetY = player.isHome ? player.baseY + 8 : player.baseY - 8;
            targetX = player.baseX + ballShiftX * 0.5;
          } else if (player.position === "FWD") {
            // Forwards light press
            targetY = player.isHome ? player.baseY + 5 : player.baseY - 5;
            if (distToBall < 25) {
              targetX = player.x + (ball.x - player.x) * 0.2;
              targetY = player.y + (ball.y - player.y) * 0.2;
            }
          }
        }

        // If closest to ball and defending, chase it
        const closestToBall = players
          .filter(p => p.isHome === player.isHome && !p.hasBall)
          .sort((a, b) => distance(a.x, a.y, ball.x, ball.y) - distance(b.x, b.y, ball.x, ball.y))[0];

        if (closestToBall?.id === player.id && !isAttacking && !ball.inFlight) {
          targetX = ball.x;
          targetY = ball.y;
        }

        player.targetX = clamp(targetX, 5, 95);
        player.targetY = clamp(targetY, 5, 95);
      }
    }

    // Move all players toward their targets
    for (const player of players) {
      if (player.hasBall) {
        // Move with ball
        player.x = clamp(moveToward(player.x, player.targetX, PLAYER_SPEED * 0.7), 5, 95);
        player.y = clamp(moveToward(player.y, player.targetY, PLAYER_SPEED * 0.7), 5, 95);
        ball.x = player.x;
        ball.y = player.y;
      } else {
        player.x = clamp(moveToward(player.x, player.targetX, PLAYER_SPEED), 5, 95);
        player.y = clamp(moveToward(player.y, player.targetY, PLAYER_SPEED), 5, 95);
      }
    }

    updateState();
  }, [updateState]);

  // Animation loop
  useEffect(() => {
    if (!isPlaying) {
      if (intervalRef.current) {
        clearInterval(intervalRef.current);
        intervalRef.current = null;
      }
      return;
    }

    intervalRef.current = window.setInterval(simulateTick, 80); // ~12 FPS

    return () => {
      if (intervalRef.current) {
        clearInterval(intervalRef.current);
        intervalRef.current = null;
      }
    };
  }, [isPlaying, simulateTick]);

  return state;
}
