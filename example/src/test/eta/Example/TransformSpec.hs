import Example.Transform
import Test.Hspec

main = hspec $ do
         describe "testBinomial" $ do
           it "returns 126" $ do
             testBinomial `shouldBe` (126 :: Int)