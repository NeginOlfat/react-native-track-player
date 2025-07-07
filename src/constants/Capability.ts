import TrackPlayer from '../TrackPlayerModule';

export enum Capability {
  Play = TrackPlayer.getConstants().CAPABILITY_PLAY,
  PlayFromId = TrackPlayer.getConstants().CAPABILITY_PLAY_FROM_ID,
  PlayFromSearch = TrackPlayer.getConstants().CAPABILITY_PLAY_FROM_SEARCH,
  Pause = TrackPlayer.getConstants().CAPABILITY_PAUSE,
  Stop = TrackPlayer.getConstants().CAPABILITY_STOP,
  SeekTo = TrackPlayer.getConstants().CAPABILITY_SEEK_TO,
  Skip = TrackPlayer.getConstants().CAPABILITY_SKIP,
  SkipToNext = TrackPlayer.getConstants().CAPABILITY_SKIP_TO_NEXT,
  SkipToPrevious = TrackPlayer.getConstants().CAPABILITY_SKIP_TO_PREVIOUS,
  JumpForward = TrackPlayer.getConstants().CAPABILITY_JUMP_FORWARD,
  JumpBackward = TrackPlayer.getConstants().CAPABILITY_JUMP_BACKWARD,
  SetRating = TrackPlayer.getConstants().CAPABILITY_SET_RATING,
  // Like = TrackPlayer.CAPABILITY_LIKE,
  // Dislike = TrackPlayer.CAPABILITY_DISLIKE,
  // Bookmark = TrackPlayer.CAPABILITY_BOOKMARK,
}
