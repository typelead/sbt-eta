module Main where

import Hello.Mod

import Data.Text (Text, append)
import Data.Text.Encoding (decodeUtf8)
import Java

combineTextByteString :: Text
combineTextByteString = append (decodeUtf8 helloByteString) helloText

main :: IO ()
main = do
  print combineTextByteString
  print "Hello Again!"
  print (fromJava myBytes :: [Byte])

foreign import java unsafe "@static com.example.MyCombined.myBytes" myBytes :: JByteArray
