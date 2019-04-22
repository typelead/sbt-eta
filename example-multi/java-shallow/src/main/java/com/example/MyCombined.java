package com.example;

import com.example.MyInt;
import com.google.common.primitives.Ints;

public class MyCombined {
    public static byte[] myBytes() {
        return Ints.toByteArray(MyInt.myInt());
    }
}
