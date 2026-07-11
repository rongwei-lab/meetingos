package com.meetingos.common;

import java.io.IOException;
import java.util.List;

@FunctionalInterface
public interface ExportRowConsumer {

    void accept(List<?> row) throws IOException;
}
