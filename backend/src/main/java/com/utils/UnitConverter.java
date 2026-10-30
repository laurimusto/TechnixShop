package com.utils;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class UnitConverter {

    public static BigDecimal kgToLbs(BigDecimal kg) {
        if(kg == null) return null;
        return kg.multiply(BigDecimal.valueOf(2.20462)).setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal cmToInch(BigDecimal cm) {
        if(cm == null) return null;
        return cm.divide(BigDecimal.valueOf(2.54), 2, RoundingMode.HALF_UP);
    }
}
