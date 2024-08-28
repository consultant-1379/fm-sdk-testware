package com.ericsson.oss.mediation.sdk.sdktestware;

import java.util.Arrays;

public class PipOperator {

    public static void version() throws OperatorException {
        final LocalProcessOperator p = new LocalProcessOperator();
        p.execute(Arrays.asList("pip", "list"), true);
    }

}
