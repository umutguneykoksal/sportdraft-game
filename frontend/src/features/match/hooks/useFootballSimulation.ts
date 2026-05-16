import { useCallback, useEffect, useRef, useState } from "react";
import type { Formation, AuctionTeamCardResponse, MatchEvent } from "../../../shared/api/contracts";
import { FORMATION_DOTS } from "../../../shared/formations";

// Goal event from backend timeline
interface ScheduledGoal {
  minute: number;
  isHome: boolean;
}

// Pitch dimensions (normalized to 100x100 for percentage-based rendering)
const PITCH_WIDTH = 100;
const PITCH_HEIGHT = 100;

// Simulation parameters
const PLAYER_SPEED = 1.4; // Max movement per tick
const BALL_SPEED = 3.5; // Ball movement speed
const PASS_CHANCE = 0.04; // Chance to pass per tick when has ball
const SHOT_CHANCE = 0.015; // Chance to shoot when in attacking third
const TACKLE_RANGE = 8; // Distance for tackle attempt
const POSSESSION_CHANGE_CHANCE = 0.08; // Chance to lose ball when pressed
const INTERCEPTION_RANGE = 8; // Distance for interception attempt
const INTERCEPTION_CHANCE = 0.5; // Chance to intercept when in range

// Check if a player is in offside position
function isOffside(
  targetPlayer: Player,
  allPlayers: Player[],
  ballY: number
): boolean {
  // Offside only applies in opponent's half
  const inOpponentHalf = targetPlayer.isHome ? targetPlayer.y < 50 : targetPlayer.y > 50;
  if (!inOpponentHalf) return false;

  // Ball must also be in opponent's half for offside
  const ballInOpponentHalf = targetPlayer.isHome ? ballY < 50 : ballY > 50;
  if (!ballInOpponentHalf) return false;

  // Find the second-to-last defender (excluding GK, who is usually last)
  const opponents = allPlayers
    .filter(p => p.isHome !== targetPlayer.isHome)
    .sort((a, b) => {
      // Sort by defensive position (home defends high y, away defends low y)
      return targetPlayer.isHome 
        ? a.y - b.y  // For home attack, lower y = more defensive
        : b.y - a.y; // For away attack, higher y = more defensive
    });

  // Need at least 2 defenders (usually GK + 1 outfield)
  if (opponents.length < 2) return false;

  // Second-to-last defender's position (index 1, since GK is usually index 0)
  const secondLastDefender = opponents[1];
  
  // Check if target is beyond second-to-last defender
  if (targetPlayer.isHome) {
    // Home attacking upward: offside if target.y < secondLastDefender.y
    return targetPlayer.y < secondLastDefender.y;
  } else {
    // Away attacking downward: offside if target.y > secondLastDefender.y
    return targetPlayer.y > secondLastDefender.y;
  }
}

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
  currentMinute: number,
  timeline: MatchEvent[],
  homeTeamId: string | null,
  seed: number = 12345
) {
  // Extract scheduled goals from backend timeline
  const scheduledGoals = useRef<ScheduledGoal[]>([]);
  const processedGoalsRef = useRef<Set<number>>(new Set());
  const [state, setState] = useState<SimulationState>({
    players: [],
    ballX: 50,
    ballY: 50,
    lastAction: "",
    passLine: null,
  });

  const playersRef = useRef<Player[]>([]);
  const ballRef = useRef<Ball>({ x: 50, y: 50, targetX: 50, targetY: 50, inFlight: false, targetPlayerId: null });
  const rngRef = useRef(new SeededRandom(seed));
  const lastActionRef = useRef("");
  const tickRef = useRef(0);
  const passLineRef = useRef<{ fromX: number; fromY: number; toX: number; toY: number } | null>(null);
  const intervalRef = useRef<number | null>(null);

  // Extract goals from timeline
  useEffect(() => {
    scheduledGoals.current = timeline
      .filter(e => e.type === "GOAL")
      .map(e => ({
        minute: e.minute,
        isHome: e.teamId === homeTeamId,
      }));
    processedGoalsRef.current = new Set();
  }, [timeline, homeTeamId]);

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
      lastAction: lastActionRef.current,
      passLine: passLineRef.current,
    });
  }, []);

  // Track current minute for goal synchronization
  const lastMinuteRef = useRef(-1);

  const simulateTick = useCallback(() => {
    const players = playersRef.current;
    const ball = ballRef.current;
    const rng = rngRef.current;
    tickRef.current++;

    // Clear pass line after a few ticks
    if (passLineRef.current && tickRef.current % 5 === 0) {
      passLineRef.current = null;
    }

    // Check for scheduled goals from backend timeline
    const pendingGoal = scheduledGoals.current.find(
      g => g.minute === currentMinute && !processedGoalsRef.current.has(g.minute)
    );

    // Find player with ball
    let playerWithBall = players.find(p => p.hasBall);
    
    // If no one has the ball and ball is not in flight, give it to nearest player
    if (!playerWithBall && !ball.inFlight) {
      const nearest = players
        .filter(p => p.position !== "GK")
        .sort((a, b) => distance(a.x, a.y, ball.x, ball.y) - distance(b.x, b.y, ball.x, ball.y))[0];
      if (nearest && distance(nearest.x, nearest.y, ball.x, ball.y) < 5) {
        nearest.hasBall = true;
        ball.x = nearest.x;
        ball.y = nearest.y;
        lastActionRef.current = `${nearest.name} picks up the ball`;
        playerWithBall = nearest;
      }
    }
    
    const attackingTeamIsHome = playerWithBall?.isHome ?? (ball.y > 50);

    // Force goal when scheduled by backend timeline
    if (pendingGoal && !ball.inFlight) {
      processedGoalsRef.current.add(pendingGoal.minute);
      
      // Find a forward from the scoring team
      const scoringTeamPlayers = players.filter(p => p.isHome === pendingGoal.isHome);
      const striker = scoringTeamPlayers.find(p => p.position === "FWD") || 
                      scoringTeamPlayers.find(p => p.position === "CAM") ||
                      scoringTeamPlayers[0];
      
      if (striker) {
        // Clear ball from current holder
        players.forEach(p => p.hasBall = false);
        
        // Move striker to attacking position and shoot
        striker.x = 50 + (rng.next() - 0.5) * 20;
        striker.y = pendingGoal.isHome ? 20 : 80;
        striker.hasBall = true;
        
        ball.x = striker.x;
        ball.y = striker.y;
        ball.inFlight = true;
        ball.targetX = 50 + (rng.next() - 0.5) * 10;
        ball.targetY = pendingGoal.isHome ? 2 : 98;
        ball.targetPlayerId = null;
        
        passLineRef.current = { fromX: ball.x, fromY: ball.y, toX: ball.targetX, toY: ball.targetY };
        lastActionRef.current = `${striker.name} shoots!`;
        
        updateState();
        return; // Let the shot animation play
      }
    }

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
          // Check if this is a scheduled goal (was just processed)
          const justProcessedGoal = scheduledGoals.current.find(
            g => processedGoalsRef.current.has(g.minute) && 
                 g.minute >= currentMinute - 1 && g.minute <= currentMinute
          );
          
          if (justProcessedGoal) {
            // GOAL! (from backend timeline)
            lastActionRef.current = `GOAL! ${justProcessedGoal.isHome ? "Home" : "Away"} team scores!`;
            // Reset to center
            ball.x = 50;
            ball.y = 50;
            const centerPlayer = players.find(p => p.position === "CM" && p.isHome !== justProcessedGoal.isHome);
            if (centerPlayer) {
              players.forEach(p => p.hasBall = false);
              centerPlayer.hasBall = true;
              ball.x = centerPlayer.x;
              ball.y = centerPlayer.y;
            }
          } else {
            // Shot saved (not a scheduled goal)
            lastActionRef.current = "Shot saved!";
            // Give ball to goalkeeper
            const gk = players.find(p => p.position === "GK" && (ball.targetY < 5 ? !p.isHome : p.isHome));
            if (gk) {
              players.forEach(p => p.hasBall = false);
              gk.hasBall = true;
              ball.x = gk.x;
              ball.y = gk.y;
            }
          }
        } else {
          // Check for offside
          const isOffsidePass = ball.targetPlayerId?.startsWith("offside-");
          const actualPlayerId = isOffsidePass 
            ? ball.targetPlayerId?.replace("offside-", "") 
            : ball.targetPlayerId;
          
          if (isOffsidePass) {
            // OFFSIDE! Free kick to defending team
            const offsidePlayer = players.find(p => p.id === actualPlayerId);
            lastActionRef.current = `OFFSIDE! ${offsidePlayer?.name ?? "Player"} was in offside position`;
            
            // Give ball to a defender of the opposing team
            const defendingTeam = offsidePlayer?.isHome === false;
            const defender = players.find(p => 
              p.isHome === defendingTeam && (p.position === "DEF" || p.position === "CDM")
            );
            if (defender) {
              players.forEach(p => p.hasBall = false);
              defender.hasBall = true;
              // Place ball where offside occurred
              ball.x = offsidePlayer?.x ?? ball.x;
              ball.y = offsidePlayer?.y ?? ball.y;
            }
          } else {
            // Normal pass
            const targetPlayer = players.find(p => p.id === ball.targetPlayerId);
            if (targetPlayer) {
              // Interception chance - check all nearby opponents
              const nearbyOpponents = players
                .filter(p => p.isHome !== targetPlayer.isHome)
                .map(p => ({ player: p, dist: distance(p.x, p.y, ball.x, ball.y) }))
                .filter(({ dist }) => dist < INTERCEPTION_RANGE)
                .sort((a, b) => a.dist - b.dist);
              
              let intercepted = false;
              for (const { player: opp, dist } of nearbyOpponents) {
                // Closer = higher interception chance
                const interceptChance = INTERCEPTION_CHANCE * (1 - dist / INTERCEPTION_RANGE);
                if (rng.next() < interceptChance) {
                  // Intercepted!
                  opp.hasBall = true;
                  ball.x = opp.x;
                  ball.y = opp.y;
                  lastActionRef.current = `Intercepted by ${opp.name}!`;
                  intercepted = true;
                  break;
                }
              }
              
              if (!intercepted) {
                targetPlayer.hasBall = true;
                ball.x = targetPlayer.x;
                ball.y = targetPlayer.y;
                lastActionRef.current = `${targetPlayer.name} receives the ball`;
              }
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
            // Filter out offside players
            const onsideTeammates = teammates.filter(p => !isOffside(p, players, ball.y));
            
            // Prefer forward passes among onside players
            const forwardTeammates = onsideTeammates.filter(p => 
              player.isHome ? p.y < player.y : p.y > player.y
            );
            
            // Fall back to any onside teammate, then any teammate if all offside
            let passTargets = forwardTeammates.length > 0 ? forwardTeammates : 
                              onsideTeammates.length > 0 ? onsideTeammates : teammates;
            const target = passTargets[rng.nextInt(0, passTargets.length - 1)];
            
            // Check if the pass would be offside
            const isOffsidePass = isOffside(target, players, ball.y);
            
            player.hasBall = false;
            ball.inFlight = true;
            ball.targetX = target.x + (rng.next() - 0.5) * 5;
            ball.targetY = target.y + (rng.next() - 0.5) * 5;
            ball.targetPlayerId = target.id;
            passLineRef.current = { fromX: ball.x, fromY: ball.y, toX: ball.targetX, toY: ball.targetY };
            
            if (isOffsidePass) {
              // Mark pass as offside - will be caught when ball arrives
              ball.targetPlayerId = `offside-${target.id}`;
              lastActionRef.current = `${player.name} passes to ${target.name}`;
            } else {
              lastActionRef.current = `${player.name} passes to ${target.name}`;
            }
          }
        }
        // Dribble forward
        else {
          player.targetX = player.x + (rng.next() - 0.5) * 3;
          player.targetY = player.isHome ? player.y - 2 : player.y + 2;
          
          // Chance to lose ball - check multiple defenders
          const nearbyDefenders = players
            .filter(p => p.isHome !== player.isHome)
            .map(p => ({ player: p, dist: distance(p.x, p.y, player.x, player.y) }))
            .filter(({ dist }) => dist < TACKLE_RANGE)
            .sort((a, b) => a.dist - b.dist);
          
          for (const { player: defender, dist } of nearbyDefenders) {
            // Closer defender = higher tackle chance
            const tackleChance = POSSESSION_CHANGE_CHANCE * (1 + (TACKLE_RANGE - dist) / TACKLE_RANGE);
            if (rng.next() < tackleChance) {
              player.hasBall = false;
              defender.hasBall = true;
              ball.x = defender.x;
              ball.y = defender.y;
              lastActionRef.current = `${defender.name} wins the ball!`;
              break;
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

        // Chase ball behavior
        const myDistToBall = distance(player.x, player.y, ball.x, ball.y);
        
        // Find closest teammates to ball (excluding GK)
        const teammatesByDist = players
          .filter(p => p.isHome === player.isHome && !p.hasBall && p.position !== "GK")
          .map(p => ({ player: p, dist: distance(p.x, p.y, ball.x, ball.y) }))
          .sort((a, b) => a.dist - b.dist);
        
        const isClosest = teammatesByDist[0]?.player.id === player.id;
        const isSecondClosest = teammatesByDist[1]?.player.id === player.id;
        
        // When defending or ball is loose, chase it
        if (!isAttacking || ball.inFlight) {
          // Closest player chases ball directly
          if (isClosest && player.position !== "GK") {
            targetX = ball.inFlight ? ball.targetX : ball.x;
            targetY = ball.inFlight ? ball.targetY : ball.y;
          }
          // Second closest provides cover
          else if (isSecondClosest && myDistToBall < 30) {
            targetX = ball.x + (player.x > ball.x ? 5 : -5);
            targetY = ball.y + (player.isHome ? -5 : 5);
          }
          // Others press if ball is in their zone
          else if (!isAttacking && myDistToBall < 20) {
            targetX = player.x + (ball.x - player.x) * 0.4;
            targetY = player.y + (ball.y - player.y) * 0.4;
          }
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
