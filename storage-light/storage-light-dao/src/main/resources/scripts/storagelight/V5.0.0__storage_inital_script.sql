create table t_cached_file (id int8 not null, checksum varchar(128), expiration timestamp, failure_cause varchar(512), fileName varchar(255), file_size int8, last_request_date timestamp, location varchar(255), mime_type varchar(255) not null, state varchar(255), primary key (id));
create table t_file_deletion_request (file_reference int8 not null, error_cause varchar(512), status varchar(255) not null, storage varchar(128) not null, force_delete boolean,primary key (file_reference));
create table t_file_reference (id int8 not null, storage varchar(128), url varchar(2048), algorithm varchar(16) not null, checksum varchar(128) not null, fileName varchar(256) not null, fileSize int8, height int4, mime_type varchar(255) not null, width int4, storageDate timestamp, primary key (id));
create table t_file_reference_request (id int8 not null, destination_storage varchar(255), destination_url varchar(255), error_cause varchar(512), algorithm varchar(16) not null, checksum varchar(128) not null, fileName varchar(256) not null, fileSize int8, height int4, mime_type varchar(255) not null, width int4, origin_storage varchar(255), origin_url varchar(255), status varchar(255) not null, primary key (id));
create table t_prioritized_storage (storage_conf_id int8 not null, storage_type varchar(255), priority int8, primary key (storage_conf_id));
create table t_storage_location (id int8 not null, allowed_size int8, last_update_date timestamp, name varchar(128), nb_ref_files int8, total_size int8, primary key (id));
create table t_storage_monitoring (id int8 not null, last_file_reference_id int8, last_monitoring_date timestamp, last_monitoring_duration int8, running boolean not null, primary key (id));
create table ta_file_ref_owners (file_ref_id int8 not null, owner varchar(255));
create table ta_file_ref_request_owners (file_ref_id int8 not null, owner varchar(255));
create table ta_file_ref_types (file_ref_id int8 not null, type varchar(255));
create index idx_cached_file_checksum on t_cached_file (checksum);
create index idx_cached_file_state on t_cached_file (state);
alter table t_cached_file add constraint uk_cached_file_checksum unique (checksum);
create index idx_file_deletion_request on t_file_deletion_request (storage);
create index idx_file_reference on t_file_reference (storage, checksum);
alter table t_file_reference add constraint uk_t_file_reference_checksum_storage unique (checksum, storage);
create index idx_file_reference_request on t_file_reference_request (destination_storage, checksum);
alter table t_file_reference_request add constraint t_file_reference_request_checksum_storage unique (checksum, destination_storage);
alter table t_prioritized_storage add constraint uk_priotitized_storage unique (storage_type, priority);
create index idx_storage_location on t_storage_location (name);
alter table t_storage_location add constraint uk_t_storage_location_name unique (name);
create sequence seq_cached_file start 1 increment 50;
create sequence seq_file_reference start 1 increment 50;
create sequence seq_file_reference_request start 1 increment 50;
create sequence seq_storage_location start 1 increment 50;
alter table t_file_deletion_request add constraint fk_t_file_deletion_request_t_file_reference foreign key (file_reference) references t_file_reference;
alter table ta_file_ref_owners add constraint fk_ta_file_ref_owners_t_file_reference foreign key (file_ref_id) references t_file_reference;
alter table ta_file_ref_request_owners add constraint fk_ta_file_ref_request_owners_t_file_reference_request foreign key (file_ref_id) references t_file_reference_request;
alter table ta_file_ref_types add constraint fk_ta_file_ref_types_t_file_reference foreign key (file_ref_id) references t_file_reference;
