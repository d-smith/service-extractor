create table service_call_dump
(
  txn_timestamp number not null,
  service_name varchar2(100 char) not null,
  request xmltype,
  response xmltype
);