package com.meetingos.tduck;

record TduckFormDetails(
        String formKey,
        String formName,
        String status,
        boolean published,
        int itemCount
) {
}
