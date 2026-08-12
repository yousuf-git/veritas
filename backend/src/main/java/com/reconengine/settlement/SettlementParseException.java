package com.reconengine.settlement;

import com.reconengine.common.AppException;
import org.springframework.http.HttpStatus;

public class SettlementParseException extends AppException {

    public SettlementParseException(String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "SETTLEMENT_PARSE_ERROR", message);
    }

    public static SettlementParseException atLine(int lineNumber, String message) {
        return new SettlementParseException("line " + lineNumber + ": " + message);
    }
}
