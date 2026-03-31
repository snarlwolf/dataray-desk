-- public.message definition

-- Drop table

-- DROP TABLE public.message;

CREATE TABLE public.message (
	id int8 NOT NULL,
	conversation_id int8 NOT NULL,
	message_source varchar(32) NOT NULL,
	official_phone_number varchar(64) DEFAULT 'unknown'::character varying NOT NULL,
	official_account varchar(64) DEFAULT 'unknown'::character varying NOT NULL,
	source_message_id varchar(128) DEFAULT 'unknown'::character varying NOT NULL,
	message_type varchar(32) DEFAULT 'UNKNOWN'::character varying NOT NULL,
	message_status varchar(32) DEFAULT 'NORMAL'::character varying NOT NULL,
	send_time timestamptz NOT NULL,
	redis_conversation_id varchar(64) DEFAULT 'unknown'::character varying NOT NULL,
	client_id varchar(64) NULL,
	client_name varchar(200) NULL,
	message_language varchar(30) NULL,
	text_body text NULL,
	media_id varchar(128) NULL,
	media_url text NULL,
	media_mime_type varchar(128) NULL,
	media_sha256 bpchar(64) NULL,
	media_caption text NULL,
	referenced_message_id varchar(128) NULL,
	reaction_emoji varchar(16) NULL,
	latitude numeric(10, 7) NULL,
	longitude numeric(10, 7) NULL,
	is_staff int2 DEFAULT 0 NOT NULL,
	sys_user_id varchar(64) NULL,
	del_flag bpchar(1) DEFAULT '0'::bpchar NOT NULL,
	remarks text NULL,
	create_time timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL,
	create_by varchar(64) DEFAULT 'SYS'::character varying NOT NULL,
	update_time timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL,
	update_by varchar(64) DEFAULT 'SYS'::character varying NOT NULL,
	CONSTRAINT message_pkey PRIMARY KEY (id)
);
CREATE INDEX idx_msg_conv_time ON public.message (conversation_id,send_time);
CREATE INDEX idx_msg_is_staff ON public.message (is_staff);
CREATE INDEX idx_msg_official_account ON public.message (official_account);
CREATE INDEX idx_msg_redis_conv ON public.message (redis_conversation_id);
CREATE INDEX idx_msg_source_msg_id ON public.message (source_message_id);
CREATE INDEX idx_msg_sys_user ON public.message (sys_user_id);


-- public.conversation definition

-- Drop table

-- DROP TABLE public.conversation;

CREATE TABLE public.conversation (
	id int8 NOT NULL,
	user_id int8 NULL,
	channel varchar(30) NULL,
	status varchar(30) NULL,
	request_time timestamptz NULL,
	close_time timestamptz NULL,
	priority int2 NULL,
	agent_id int8 NULL,
	agent_name varchar(50) NULL,
	is_transfer_agent bool NULL,
	current_reply_status varchar(32) NULL,
	create_time timestamp NULL,
	create_by varchar(64) NULL,
	update_time timestamp NULL,
	update_by varchar(64) NULL,
	del_flag bpchar(1) NULL,
	remarks text NULL,
	session_id varchar(64) NULL,
	client_id varchar(64) NULL,
	official_account varchar(64) NULL,
	message_summary text NULL,
	CONSTRAINT pk_conversation PRIMARY KEY (id)
);