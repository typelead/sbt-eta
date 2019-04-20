{-# LANGUAGE OverloadedStrings #-}
module Hello.Mod (helloText, helloByteString) where

import Data.Text (Text)
import qualified Data.Text as Text
import Data.ByteString (ByteString, append)
import Data.ByteString.Char8 (pack)

helloText :: Text
helloText = "Hello Text"

helloByteString :: ByteString
helloByteString = append "Hello ByteString " (pack myString)

foreign import java unsafe "@static com.example.MyText.myString" myString :: String

helloTextLength :: Int
helloTextLength = Text.length helloText

foreign export java "@static com.example.MyInt.myInt" helloTextLength :: Int

