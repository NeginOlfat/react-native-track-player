import { TurboModule, TurboModuleRegistry } from "react-native";

export interface Spec extends TurboModule {

  getConstants(): {
    // Capabilities
    CAPABILITY_PLAY: number;
    CAPABILITY_PLAY_FROM_ID: number;
    CAPABILITY_PLAY_FROM_SEARCH: number;
    CAPABILITY_PAUSE: number;
    CAPABILITY_STOP: number;
    CAPABILITY_SEEK_TO: number;
    CAPABILITY_SKIP: number;
    CAPABILITY_SKIP_TO_NEXT: number;
    CAPABILITY_SKIP_TO_PREVIOUS: number;
    CAPABILITY_SET_RATING: number;
    CAPABILITY_JUMP_FORWARD: number;
    CAPABILITY_JUMP_BACKWARD: number;

    // States
    STATE_NONE: string;
    STATE_READY: string;
    STATE_PLAYING: string;
    STATE_PAUSED: string;
    STATE_STOPPED: string;
    STATE_BUFFERING: string;
    STATE_LOADING: string;

    // Rating Types
    RATING_HEART: number;
    RATING_THUMBS_UP_DOWN: number;
    RATING_3_STARS: number;
    RATING_4_STARS: number;
    RATING_5_STARS: number;
    RATING_PERCENTAGE: number;

    // Repeat Modes
    REPEAT_OFF: number;
    REPEAT_TRACK: number;
    REPEAT_QUEUE: number;
  };


  // Core Methods
  setupPlayer(data: Object | null): Promise<void>;
  isServiceRunning(): Promise<boolean>;
  updateOptions(data: Object | null): Promise<void>;

  // Track Management
  add(tracks: Object[], insertBeforeIndex: number): Promise<number>;
  load(data: Object | null): Promise<void>;
  move(fromIndex: number, toIndex: number): Promise<void>;
  remove(data: number[] | null | undefined): Promise<void>;
  updateMetadataForTrack(index: number, map: Object | null): Promise<void>;
  updateNowPlayingMetadata(map: Object | null): Promise<void>;
  clearNowPlayingMetadata(): Promise<void>;
  removeUpcomingTracks(): Promise<void>;

  // Playback Control
  skip(index: number, initialTime: number): Promise<void>;
  skipToNext(initialTime: number): Promise<void>;
  skipToPrevious(initialTime: number): Promise<void>;
  reset(): Promise<void>;
  play(): Promise<void>;
  pause(): Promise<void>;
  stop(): Promise<void>;
  seekTo(seconds: number): Promise<void>;
  seekBy(offset: number): Promise<void>;
  retry(): Promise<void>;

  // Volume/Rate
  setVolume(volume: number): Promise<void>;
  getVolume(): Promise<number>;
  setRate(rate: number): Promise<void>;
  getRate(): Promise<number>;

  // Repeat Mode
  setRepeatMode(mode: number): Promise<number>;
  getRepeatMode(): Promise<number>;

  // Queue Management
  setPlayWhenReady(playWhenReady: boolean): Promise<boolean>;
  getPlayWhenReady(): Promise<boolean>;
  getTrack(index: number): Promise<any | null>;
  setQueue(data: Object[] | null | undefined): Promise<void>;
  getQueue(): Promise<Object[]>;

  // Playback State
  getActiveTrackIndex(): Promise<number | null>;
  getActiveTrack(): Promise<any | null>;
  getDuration(): Promise<number>;
  getBufferedPosition(): Promise<number>;
  getPosition(): Promise<number>;
  getProgress(): Promise<{
    duration: number;
    position: number;
    buffered: number;
  }>;
  getPlaybackState(): Promise<any>;
}

export default TurboModuleRegistry.getEnforcing<Spec>('TrackPlayerModule');
