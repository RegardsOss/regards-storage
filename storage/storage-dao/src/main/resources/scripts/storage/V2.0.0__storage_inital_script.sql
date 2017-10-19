create sequence seq_aip start 1 increment 50;
create sequence seq_cached_file start 1 increment 50;
create sequence seq_data_file start 1 increment 50;
create sequence storage_parameter_sequence start 1 increment 50;
create table t_aip (id int8 not null, json_aip jsonb, ip_id varchar(128), comment text, date timestamp, type varchar(255), sip_id varchar(128), state varchar(255), submissionDate timestamp, primary key (id));
create table t_aip_tag (aip_id int8 not null, tags varchar(255));
create table t_cached_file (id int8 not null, checksum varchar(255), expiration timestamp, failureCause varchar(512), fileSize int8, lastRequestDate timestamp, location varchar(255), state varchar(255), primary key (id));
create table t_data_file (id int8 not null, algorithm varchar(255) not null, checksum varchar(128) not null, dataType varchar(255), fileSize int8, mimeType varchar(255) not null, name varchar(255), state varchar(255), url varchar(255), aip_ip_id int8, data_storage_plugin_configuration int8, primary key (id));
create table t_storage_parameter (id int8 not null, name varchar(255), value text, primary key (id));
create index idx_aip_ip_id on t_aip (ip_id);
create index idx_aip_state on t_aip (state);
create index idx_aip_submission_date on t_aip (submissionDate);
create index idx_aip_last_event_date on t_aip (date);
alter table t_aip add constraint uk_aip_ipId unique (ip_id);
create index idx_cached_file_checksum on t_cached_file (checksum);
create index idx_cached_file_state on t_cached_file (state);
alter table t_cached_file add constraint uk_cached_file_checksum unique (checksum);
create index idx_data_file_checksum on t_data_file (checksum);
create index idx_storage_parameter_name on t_storage_parameter (name);
alter table t_storage_parameter add constraint uk_storage_parameter_name unique (name);
alter table t_aip_tag add constraint fk_aip_tag_aip_id foreign key (aip_id) references t_aip;
alter table t_data_file add constraint fk_aip_data_file foreign key (aip_ip_id) references t_aip;
alter table t_data_file add constraint fk_data_file_data_storage_plugin_configuration foreign key (data_storage_plugin_configuration) references t_plugin_configuration;
