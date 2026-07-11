package com.meetingos.common;

import java.io.IOException;

@FunctionalInterface
public interface ExportRowProducer {

    void produce(ExportRowConsumer consumer) throws IOException;
}
