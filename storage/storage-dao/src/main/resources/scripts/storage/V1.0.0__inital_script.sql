create table t_cache_file (id int8 not null, checksum varchar(128), expiration_date timestamp, filename varchar(255), file_size int8, location varchar(255), mime_type varchar(255) not null, primary key (id));
create table t_donwload_token (id int8 not null, checksum varchar(255) not null, expirationDate timestamp not null, token varchar(255) not null, primary key (id));
create table t_file_cache_request (id int8 not null, checksum varchar(128) not null, creation_date timestamp, error_cause varchar(512), expiration_date timestamp, file_size int8 not null, group_id varchar(128) not null, job_id varchar(255), destination_path varchar(2048) not null, status varchar(255) not null, storage varchar(128) not null, file_ref_id int8 not null, primary key (id));
create table t_file_copy_request (id int8 not null, creation_date timestamp, error_cause varchar(512), cache_group_id varchar(128), storage_group_id varchar(128), group_id varchar(128) not null, algorithm varchar(16) not null, checksum varchar(128) not null, fileName varchar(256) not null, fileSize int8, height int4, mime_type varchar(255) not null, type varchar(256), width int4, status varchar(255) not null, storage varchar(128), storage_subdirectory varchar(2048), primary key (id));
create table t_file_deletion_request (file_reference int8 not null, creation_date timestamp, error_cause varchar(512), force_delete boolean, group_id varchar(128) not null, job_id varchar(255), status varchar(255) not null, storage varchar(128) not null, primary key (file_reference));
create table t_file_reference (id int8 not null, storage varchar(128), url varchar(2048), algorithm varchar(16) not null, checksum varchar(128) not null, fileName varchar(256) not null, fileSize int8, height int4, mime_type varchar(255) not null, type varchar(256), width int4, owners jsonb, storageDate timestamp, primary key (id));
create table t_file_storage_request (id int8 not null, creation_date timestamp, error_cause varchar(512), job_id varchar(255), algorithm varchar(16) not null, checksum varchar(128) not null, fileName varchar(256) not null, fileSize int8, height int4, mime_type varchar(255) not null, type varchar(256), width int4, origin_url varchar(2048), status varchar(255) not null, storage varchar(128), storage_subdirectory varchar(2048), primary key (id));
create table t_request_group (id varchar(255) not null, creation_date timestamp not null, expiration_date timestamp not null, type varchar(255) not null, primary key (id));
create table t_request_result_info (id int8 not null, error boolean, error_cause varchar(512), group_id varchar(128) not null, request_checksum varchar(128) not null, request_owners jsonb, request_storage varchar(128), request_store_path varchar(2048), request_type varchar(255) not null, result_file_ref_id int8, primary key (id));
create table t_storage_location (id int8 not null, last_update_date timestamp, name varchar(128), nb_ref_files int8, total_size_ko int8, primary key (id));
create table t_storage_location_conf (id int8 not null, allocated_size_ko int8, name varchar(128), priority int8, storage_type varchar(255), plugin_conf_id int8, primary key (id));
create table t_storage_monitoring_process (id int8 not null, last_file_reference_id int8, last_monitoring_date timestamp, last_monitoring_duration int8, running boolean not null, primary key (id));
create table ta_cache_file_group_ids (cache_file_id int8 not null, group_id varchar(128) not null, primary key (cache_file_id, group_id));
create table ta_file_storage_request_owners (file_storage_request_id int8 not null, owner varchar(255));
create table ta_storage_request_group_ids (file_storage_request_id int8 not null, group_id varchar(128) not null, primary key (file_storage_request_id, group_id));
create index idx_cache_file_checksum on t_cache_file (checksum);
alter table t_cache_file add constraint uk_cache_file_checksum unique (checksum);
create index idx_download_token on t_donwload_token (token, checksum);
create index idx_file_cache_request_grp on t_file_cache_request (group_id, status);
create index idx_file_cache_request_cs on t_file_cache_request (checksum);
create index idx_file_cache_request_storage on t_file_cache_request (storage);
create index idx_file_cache_file_ref on t_file_cache_request (file_ref_id);
alter table t_file_cache_request add constraint uk_t_file_cache_request_checksum unique (checksum);
create index idx_file_copy_request on t_file_copy_request (storage, checksum);
create index idx_file_copy_request_grp on t_file_copy_request (group_id, status);
create index idx_file_copy_request_cache_grp on t_file_copy_request (cache_group_id);
create index idx_file_copy_request_storage_grp on t_file_copy_request (storage_group_id);
alter table t_file_copy_request add constraint t_file_copy_request_checksum_storage unique (checksum, storage);
create index idx_file_deletion_request on t_file_deletion_request (storage);
create index idx_file_deletion_grp on t_file_deletion_request (group_id, status);
create index idx_file_deletion_file_ref on t_file_deletion_request (file_reference);
create index idx_file_reference_checksum on t_file_reference (checksum);
create index idx_file_reference_storage on t_file_reference (storage);
create index idx_file_reference_storage_checksum on t_file_reference (checksum, storage);
create index idx_file_reference_type on t_file_reference (type);
alter table t_file_reference add constraint uk_t_file_reference_checksum_storage unique (checksum, storage);
create index idx_file_storage_request on t_file_storage_request (storage, checksum);
create index idx_file_storage_request_cs on t_file_storage_request (checksum);
create index idx_file_storage_request_storage on t_file_storage_request (storage);
create index idx_t_request_group on t_request_group (id);
create index idx_group_id on t_request_result_info (group_id);
create index idx_group_file_ref_id on t_request_result_info (result_file_ref_id);
create index idx_storage_location on t_storage_location (name);
alter table t_storage_location add constraint uk_t_storage_location_name unique (name);
alter table t_storage_location_conf add constraint uk_storage_loc_name unique (name);
alter table t_storage_location_conf add constraint uk_storage_loc_conf_type_priority unique (storage_type, priority);
create sequence seq_cache_file start 1 increment 50;
create sequence seq_download_token start 1 increment 50;
create sequence seq_file_cache_request start 1 increment 50;
create sequence seq_file_reference start 1 increment 50;
create sequence seq_file_storage_request start 1 increment 50;
create sequence seq_groups_requests_info start 1 increment 50;
create sequence seq_storage_location start 1 increment 50;
create sequence seq_storage_location_conf start 1 increment 50;
alter table t_file_cache_request add constraint FKmhrrlwenlm8bmtwy5laku0jvu foreign key (file_ref_id) references t_file_reference;
alter table t_file_deletion_request add constraint FK8iyuxn10e6gx9ybs6i92mxdgq foreign key (file_reference) references t_file_reference;
alter table t_request_result_info add constraint FKc9bxmi7kd5qm75tg4tmxfflc8 foreign key (result_file_ref_id) references t_file_reference;
alter table t_storage_location_conf add constraint fk_prioritized_storage_plugin_conf foreign key (plugin_conf_id) references t_plugin_configuration;
alter table ta_cache_file_group_ids add constraint fk_ta_cache_file_request_ids_t_file_cache foreign key (cache_file_id) references t_cache_file;
alter table ta_file_storage_request_owners add constraint fk_ta_file_storage_request_owners_t_file_storage_request foreign key (file_storage_request_id) references t_file_storage_request;
alter table ta_storage_request_group_ids add constraint fk_ta_storage_request_group_ids_t_file_storage_request foreign key (file_storage_request_id) references t_file_storage_request;
