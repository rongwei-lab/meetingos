package com.meetingos.importtask;

import java.nio.file.Path;

public record ImportFailureFile(String filename, Path path, long size) {
}
