package com.regivolley.api.domain.model.valueobject;

import com.regivolley.api.domain.shared.ValueObject;

/** How a manually recorded payment was made (RN-17): cash, bank transfer or MB WAY (marked by hand; online payment is out of the MVP). */
public enum PaymentMethod implements ValueObject {
    CASH,
    TRANSFER,
    MB_WAY
}
