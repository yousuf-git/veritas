package com.reconengine.settlement;

import java.io.InputStream;
import java.util.List;

public interface SettlementParser {

    SettlementProvider provider();

    List<ParsedSettlementLine> parse(InputStream input);
}
