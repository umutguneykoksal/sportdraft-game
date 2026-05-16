import type { PlaybackSpeed } from "../hooks/useMatchPlayback";

interface PlaybackControlsProps {
  currentMinute: number;
  isPlaying: boolean;
  speed: PlaybackSpeed;
  isFinished: boolean;
  homeScore: number;
  awayScore: number;
  onTogglePlay: () => void;
  onSpeedChange: (speed: PlaybackSpeed) => void;
  onSeek: (minute: number) => void;
  onRestart: () => void;
}

const SPEED_OPTIONS: PlaybackSpeed[] = [1, 2, 4];

export function PlaybackControls({
  currentMinute,
  isPlaying,
  speed,
  isFinished,
  homeScore,
  awayScore,
  onTogglePlay,
  onSpeedChange,
  onSeek,
  onRestart,
}: PlaybackControlsProps) {
  const formatMinute = (m: number) => {
    if (m <= 45) return `${m}'`;
    if (m === 45) return "HT";
    return `${m}'`;
  };

  return (
    <div className="bg-gray-800/50 backdrop-blur-sm rounded-lg p-3 space-y-3">
      {/* Score and time */}
      <div className="flex items-center justify-between">
        {/* Home score */}
        <div className="flex items-center gap-2">
          <div className="w-3 h-3 rounded-full bg-blue-500" />
          <span className="text-2xl font-bold text-white">{homeScore}</span>
        </div>

        {/* Time display */}
        <div className="text-center">
          <div className="text-xl font-mono font-bold text-white">
            {formatMinute(currentMinute)}
          </div>
          <div className="text-xs text-gray-400">
            {currentMinute <= 45 ? "1st Half" : "2nd Half"}
          </div>
        </div>

        {/* Away score */}
        <div className="flex items-center gap-2">
          <span className="text-2xl font-bold text-white">{awayScore}</span>
          <div className="w-3 h-3 rounded-full bg-red-500" />
        </div>
      </div>

      {/* Timeline slider */}
      <div className="relative">
        <input
          type="range"
          min={0}
          max={90}
          value={currentMinute}
          onChange={(e) => onSeek(parseInt(e.target.value, 10))}
          className="w-full h-2 bg-gray-700 rounded-lg appearance-none cursor-pointer slider-thumb"
          style={{
            background: `linear-gradient(to right, #3b82f6 0%, #3b82f6 ${(currentMinute / 90) * 100}%, #374151 ${(currentMinute / 90) * 100}%, #374151 100%)`,
          }}
        />
        {/* Half-time marker */}
        <div
          className="absolute top-1/2 -translate-y-1/2 w-0.5 h-4 bg-yellow-500"
          style={{ left: "50%" }}
          title="Half-time"
        />
        {/* Time labels */}
        <div className="flex justify-between mt-1 text-xs text-gray-500">
          <span>0'</span>
          <span>45'</span>
          <span>90'</span>
        </div>
      </div>

      {/* Control buttons */}
      <div className="flex items-center justify-center gap-3">
        {/* Restart button */}
        <button
          onClick={onRestart}
          className="p-2 rounded-full bg-gray-700 hover:bg-gray-600 text-white transition-colors"
          title="Restart"
        >
          <svg
            className="w-5 h-5"
            fill="none"
            stroke="currentColor"
            viewBox="0 0 24 24"
          >
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeWidth={2}
              d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15"
            />
          </svg>
        </button>

        {/* Play/Pause button */}
        <button
          onClick={onTogglePlay}
          className={`p-3 rounded-full transition-colors ${
            isFinished
              ? "bg-green-600 hover:bg-green-500"
              : isPlaying
                ? "bg-yellow-600 hover:bg-yellow-500"
                : "bg-green-600 hover:bg-green-500"
          } text-white`}
          title={isFinished ? "Replay" : isPlaying ? "Pause" : "Play"}
        >
          {isPlaying ? (
            <svg
              className="w-6 h-6"
              fill="currentColor"
              viewBox="0 0 24 24"
            >
              <path d="M6 4h4v16H6V4zm8 0h4v16h-4V4z" />
            </svg>
          ) : (
            <svg
              className="w-6 h-6"
              fill="currentColor"
              viewBox="0 0 24 24"
            >
              <path d="M8 5v14l11-7z" />
            </svg>
          )}
        </button>

        {/* Speed selector */}
        <div className="flex items-center gap-1 bg-gray-700 rounded-full p-1">
          {SPEED_OPTIONS.map((s) => (
            <button
              key={s}
              onClick={() => onSpeedChange(s)}
              className={`px-2 py-1 text-xs font-bold rounded-full transition-colors ${
                speed === s
                  ? "bg-blue-600 text-white"
                  : "text-gray-400 hover:text-white"
              }`}
            >
              {s}x
            </button>
          ))}
        </div>
      </div>

      {/* Match status */}
      {isFinished && (
        <div className="text-center text-sm text-green-400 font-medium">
          Full Time - Match Complete
        </div>
      )}
    </div>
  );
}
