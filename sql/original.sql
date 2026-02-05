-- 添加用户名检查约束
ALTER TABLE sys_user 
	ADD CONSTRAINT chk_status_invalid
	CHECK (user_name NOT IN ('AI','AISYSTEM','TRANSFERING',''));

-- 一些关于能否收发消息的配置数据
INSERT INTO sys_global_property
(belong_sys, system_name, category, prop_key, prop_value, remark)
VALUES
('skydawn_desk','message','enable','TEXT','1','是否接收文字消息，0否，1是'),
('skydawn_desk','message','enable','IMAGE','0','是否接收图片消息，0否，1是'),
('skydawn_desk','message','enable','STICKER','0','是否接收贴图消息，0否，1是'),
('skydawn_desk','message','enable','VIDEO','0','是否接收视频消息，0否，1是'),
('skydawn_desk','message','enable','AUDIO','0','是否接收音频消息，0否，1是'),
('skydawn_desk','message','enable','DOCUMENT','0','是否接收文件消息，0否，1是'),
('skydawn_desk','message','enable','LOCATION','1','是否接收位置消息，0否，1是'),
('skydawn_desk','message','enable','CONTACTS','1','是否接收名片消息，0否，1是'),
('skydawn_desk','message','enable','STATUS','1','是否接收状态消息，0否，1是'),
('skydawn_desk','message','enable','REACTION','1','是否接收回复消息，0否，1是'),
('skydawn_desk','message','enable','UNSUPPORTED','1','是否接收不支持消息，0否，1是'),
('skydawn_desk','message','enable','UNKNOWN','1','是否接收未知消息，0否，1是');