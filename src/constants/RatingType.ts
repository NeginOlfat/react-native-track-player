import TrackPlayer from '../TrackPlayerModule';

export enum RatingType {
  Heart = TrackPlayer.getConstants().RATING_HEART,
  ThumbsUpDown = TrackPlayer.getConstants().RATING_THUMBS_UP_DOWN,
  ThreeStars = TrackPlayer.getConstants().RATING_3_STARS,
  FourStars = TrackPlayer.getConstants().RATING_4_STARS,
  FiveStars = TrackPlayer.getConstants().RATING_5_STARS,
  Percentage = TrackPlayer.getConstants().RATING_PERCENTAGE,
}
