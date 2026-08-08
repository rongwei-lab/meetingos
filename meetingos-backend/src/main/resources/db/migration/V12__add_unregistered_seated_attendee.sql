alter table attendees
    add column unregistered boolean not null default false,
    modify column phone varchar(40) null;

create index idx_attendee_meeting_unregistered
    on attendees (meeting_id, unregistered);
