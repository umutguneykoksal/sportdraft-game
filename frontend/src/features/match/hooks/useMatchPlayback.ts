import { useCallback, useEffect, useRef, useState } from "react";
import { MatchEvent } from "../../../shared/api";

export type PlaybackSpeed = 1 | 2 | 4;

export interface PlaybackState {
  currentMinute: number;
  isPlaying: boolean;
  speed: PlaybackSpeed;
  currentEvent: MatchEvent | null;
  pastEvents: MatchEvent[];
  homeScore: number;
  awayScore: number;
  isFinished: boolean;
}

const TICK_INTERVAL_MS = 1000; // Base: 1 second per minute at 1x speed

export function useMatchPlayback(
  timeline: MatchEvent[],
  homeTeamId: string | null
) {
  const [currentMinute, setCurrentMinute] = useState(0);
  const [isPlaying, setIsPlaying] = useState(false);
  const [speed, setSpeed] = useState<PlaybackSpeed>(1);
  const [currentEvent, setCurrentEvent] = useState<MatchEvent | null>(null);
  const [homeScore, setHomeScore] = useState(0);
  const [awayScore, setAwayScore] = useState(0);

  const intervalRef = useRef<number | null>(null);
  const lastProcessedMinuteRef = useRef(-1);

  // Events that have occurred up to current minute
  const pastEvents = timeline.filter((e) => e.minute <= currentMinute);

  // Check if playback is finished
  const isFinished = currentMinute >= 90;

  // Process events at current minute
  useEffect(() => {
    if (lastProcessedMinuteRef.current >= currentMinute) return;

    const eventsAtMinute = timeline.filter(
      (e) =>
        e.minute === currentMinute &&
        e.minute > lastProcessedMinuteRef.current
    );

    if (eventsAtMinute.length > 0) {
      // Show the most significant event (GOAL > SHOT > FOUL > others)
      const priorityOrder = ["GOAL", "SHOT_SAVED", "SHOT_MISSED", "FOUL"];
      const sorted = [...eventsAtMinute].sort((a, b) => {
        const aIdx = priorityOrder.indexOf(a.type);
        const bIdx = priorityOrder.indexOf(b.type);
        return (aIdx === -1 ? 99 : aIdx) - (bIdx === -1 ? 99 : bIdx);
      });
      setCurrentEvent(sorted[0]);

      // Update score for goals
      for (const event of eventsAtMinute) {
        if (event.type === "GOAL") {
          if (event.teamId === homeTeamId) {
            setHomeScore((s) => s + 1);
          } else {
            setAwayScore((s) => s + 1);
          }
        }
      }

      // Clear current event after 2 seconds
      setTimeout(() => setCurrentEvent(null), 2000);
    }

    lastProcessedMinuteRef.current = currentMinute;
  }, [currentMinute, timeline, homeTeamId]);

  // Playback timer
  useEffect(() => {
    if (!isPlaying || isFinished) {
      if (intervalRef.current) {
        clearInterval(intervalRef.current);
        intervalRef.current = null;
      }
      return;
    }

    const tickMs = TICK_INTERVAL_MS / speed;
    intervalRef.current = window.setInterval(() => {
      setCurrentMinute((m) => {
        if (m >= 90) {
          setIsPlaying(false);
          return 90;
        }
        return m + 1;
      });
    }, tickMs);

    return () => {
      if (intervalRef.current) {
        clearInterval(intervalRef.current);
        intervalRef.current = null;
      }
    };
  }, [isPlaying, speed, isFinished]);

  // Controls
  const play = useCallback(() => {
    if (currentMinute >= 90) {
      // Restart from beginning
      setCurrentMinute(0);
      setHomeScore(0);
      setAwayScore(0);
      lastProcessedMinuteRef.current = -1;
    }
    setIsPlaying(true);
  }, [currentMinute]);

  const pause = useCallback(() => setIsPlaying(false), []);

  const togglePlay = useCallback(() => {
    if (isPlaying) {
      pause();
    } else {
      play();
    }
  }, [isPlaying, play, pause]);

  const setPlaybackSpeed = useCallback((s: PlaybackSpeed) => setSpeed(s), []);

  const seekTo = useCallback((minute: number) => {
    const clamped = Math.max(0, Math.min(90, minute));
    setCurrentMinute(clamped);
    
    // Recalculate score up to this minute
    let home = 0;
    let away = 0;
    for (const event of timeline) {
      if (event.minute <= clamped && event.type === "GOAL") {
        if (event.teamId === homeTeamId) {
          home++;
        } else {
          away++;
        }
      }
    }
    setHomeScore(home);
    setAwayScore(away);
    lastProcessedMinuteRef.current = clamped;
    setCurrentEvent(null);
  }, [timeline, homeTeamId]);

  const restart = useCallback(() => {
    setCurrentMinute(0);
    setHomeScore(0);
    setAwayScore(0);
    lastProcessedMinuteRef.current = -1;
    setCurrentEvent(null);
    setIsPlaying(true);
  }, []);

  return {
    // State
    currentMinute,
    isPlaying,
    speed,
    currentEvent,
    pastEvents,
    homeScore,
    awayScore,
    isFinished,
    // Controls
    play,
    pause,
    togglePlay,
    setPlaybackSpeed,
    seekTo,
    restart,
  };
}
