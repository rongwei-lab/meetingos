alter table tduck_form_bindings
  add column registration_enabled boolean not null default true after registration_url;
