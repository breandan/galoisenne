module MapExperiment where

import Prelude hiding (map, Functor(..), Applicative(..), Monad(..))

class Functor f where
  map :: (a -> b) -> (f a -> f b)

instance Functor [] where
  map f [] = []
  map f (x:xs) = f x : map f xs

class Functor f => Applicative f where
  pure :: a -> f a
  ap :: f (a -> b) -> (f a -> f b)

class Monad m where
  return :: a -> m a -- must be == pure
  bind :: (a -> m b) -> (m a -> m b)

(>>=) :: Monad m => m a -> (a -> m b) -> m b

mapm :: Monad m => (a -> b) -> (m a -> m b)
mapm f ma = ma >>= (return . f)

apm :: Monad m => m (a -> b) -> (m a -> m b)
apm mf ma = mf >>= (\f -> mapm f ma)

instance Monad m => Functor m where map = mmap
instance Monad m => Applicative m where
  pure = return
  ap = apm


