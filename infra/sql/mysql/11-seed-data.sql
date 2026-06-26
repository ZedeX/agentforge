-- =====================================================================
-- File: 11-seed-data.sql
-- Purpose: 初始化种子数据
-- Source: docs/09-governance-and-deployment/governance-and-middleware.md
--         §3.1 模型选型 / §3.2.d 主流模型计费
-- Engine: InnoDB / Charset: utf8mb4
-- 注意: 多库种子数据, 需按库名 USE 切换
-- =====================================================================

-- =====================================================================
-- 1. agent_model 库: model_provider + model_route_rule
-- =====================================================================
USE agent_model;

-- ---------------------------------------------------------------------
-- 1.1 model_provider 初始化 (5 供应商, 14 模型)
-- 价格对齐 doc 09 §3.2.d:
--   海外: $/M Token (input_price_usd_per_m / output_price_usd_per_m)
--   国内: 元/千 Token (input_price_cny_per_k / output_price_cny_per_k)
--   统一换算为分/千 Token: input_price_cent_per_k / output_price_cent_per_k
--   汇率假设: $1 = 7.2 CNY = 720 cent; 1 元 = 100 cent
-- ---------------------------------------------------------------------
INSERT INTO `model_provider` (`id`, `provider_code`, `name`, `base_url`, `api_key_ref`, `protocol`, `supported_models`, `status`, `created_by`) VALUES
-- OpenAI: GPT-4o $5/$15, GPT-4o-mini $0.15/$0.60, GPT-3.5-turbo $0.5/$1.5
(1001, 'openai', 'OpenAI', 'https://api.openai.com/v1',
 'secret/data/agent-platform/model/openai#api_key', 'openai',
 JSON_ARRAY(
   JSON_OBJECT('model','gpt-4o','tier','strong','context',128000,'input_price_cent_per_k',36,'output_price_cent_per_k',108),
   JSON_OBJECT('model','gpt-4o-mini','tier','light','context',128000,'input_price_cent_per_k',11,'output_price_cent_per_k',43),
   JSON_OBJECT('model','gpt-3.5-turbo','tier','light','context',16385,'input_price_cent_per_k',36,'output_price_cent_per_k',108)
 ), 1, 'system'),

-- Anthropic: Claude 3.5 Sonnet $3/$15, Claude 3 Opus $15/$75, Claude 3 Haiku $0.25/$1.25
(1002, 'anthropic', 'Anthropic Claude', 'https://api.anthropic.com/v1',
 'secret/data/agent-platform/model/anthropic#api_key', 'anthropic',
 JSON_ARRAY(
   JSON_OBJECT('model','claude-3-5-sonnet','tier','strong','context',200000,'input_price_cent_per_k',22,'output_price_cent_per_k',108),
   JSON_OBJECT('model','claude-3-opus','tier','strong','context',200000,'input_price_cent_per_k',108,'output_price_cent_per_k',540),
   JSON_OBJECT('model','claude-3-haiku','tier','light','context',200000,'input_price_cent_per_k',18,'output_price_cent_per_k',90)
 ), 1, 'system'),

-- Google: Gemini 1.5 Pro $1.25/$5, Gemini 1.5 Flash $0.075/$0.30
(1003, 'google', 'Google Gemini', 'https://generativelanguage.googleapis.com/v1',
 'secret/data/agent-platform/model/google#api_key', 'custom',
 JSON_ARRAY(
   JSON_OBJECT('model','gemini-1.5-pro','tier','middle','context',1000000,'input_price_cent_per_k',9,'output_price_cent_per_k',36),
   JSON_OBJECT('model','gemini-1.5-flash','tier','light','context',1000000,'input_price_cent_per_k',5,'output_price_cent_per_k',22)
 ), 1, 'system'),

-- 通义千问: Max 0.04/0.12 元/K, Plus 0.008/0.02 元/K, Turbo 0.002/0.006 元/K
(1004, 'qwen', '通义千问 Qwen', 'https://dashscope.aliyuncs.com/api/v1',
 'secret/data/agent-platform/model/qwen#api_key', 'openai',
 JSON_ARRAY(
   JSON_OBJECT('model','qwen-max','tier','strong','context',32768,'input_price_cent_per_k',4,'output_price_cent_per_k',12),
   JSON_OBJECT('model','qwen-plus','tier','middle','context',131072,'input_price_cent_per_k',8,'output_price_cent_per_k',2),
   JSON_OBJECT('model','qwen-turbo','tier','light','context',8192,'input_price_cent_per_k',2,'output_price_cent_per_k',6)
 ), 1, 'system'),

-- DeepSeek: V3 (strong) 0.002/0.006, Chat (middle) 0.001/0.002, Lite (light) 0.0005/0.001
(1005, 'deepseek', 'DeepSeek', 'https://api.deepseek.com/v1',
 'secret/data/agent-platform/model/deepseek#api_key', 'openai',
 JSON_ARRAY(
   JSON_OBJECT('model','deepseek-v3','tier','strong','context',65536,'input_price_cent_per_k',2,'output_price_cent_per_k',6),
   JSON_OBJECT('model','deepseek-chat','tier','middle','context',65536,'input_price_cent_per_k',1,'output_price_cent_per_k',2),
   JSON_OBJECT('model','deepseek-lite','tier','light','context',32768,'input_price_cent_per_k',1,'output_price_cent_per_k',1)
 ), 1, 'system');

-- ---------------------------------------------------------------------
-- 1.2 model_route_rule 初始化 (5 场景, 对齐 doc 09 §3.1 + §3.3)
-- ---------------------------------------------------------------------
INSERT INTO `model_route_rule` (`id`, `rule_id`, `scene`, `tier`, `preferred_model`, `fallback_models`, `priority`, `condition`, `status`, `created_by`) VALUES
-- 意图识别: 低成本、低延迟、高并发
(2001, 'rl_intent_light_default', 'intent', 'light', 'qwen-turbo',
 JSON_ARRAY('deepseek-chat','gpt-3.5-turbo'), 100, JSON_OBJECT(), 1, 'system'),
-- 任务规划: 高准确率、低幻觉、强逻辑
(2002, 'rl_planning_strong_default', 'planning', 'strong', 'gpt-4o',
 JSON_ARRAY('claude-3-5-sonnet','qwen-max'), 100, JSON_OBJECT(), 1, 'system'),
-- 工具调用: 平衡效果与成本
(2003, 'rl_tool_call_middle_default', 'tool_call', 'middle', 'qwen-plus',
 JSON_ARRAY('gpt-4o-mini','claude-3-haiku'), 100, JSON_OBJECT(), 1, 'system'),
-- 结果汇总: 低成本、格式合规
(2004, 'rl_summary_middle_default', 'summary', 'middle', 'qwen-plus',
 JSON_ARRAY('gpt-4o-mini'), 100, JSON_OBJECT(), 1, 'system'),
-- 质量终审: 零容错、效果兜底
(2005, 'rl_audit_strong_default', 'audit', 'strong', 'claude-3-5-sonnet',
 JSON_ARRAY('gpt-4o'), 100, JSON_OBJECT(), 1, 'system'),
-- 金融域终审特例 (condition 匹配 domain=finance, 优先级更高)
(2006, 'rl_audit_strong_fin', 'audit', 'strong', 'claude-3-5-sonnet',
 JSON_ARRAY('gpt-4o'), 50, JSON_OBJECT('domain','finance'), 1, 'system');

-- =====================================================================
-- 2. agent_task 库: task_template 初始化 (3 模板)
-- =====================================================================
USE agent_task;

INSERT INTO `task_template` (`id`, `template_id`, `name`, `scene_tags`, `dag_template`, `param_schema`, `usage_count`, `success_rate`, `status`, `created_by`) VALUES
-- 行业调研模板
(3001, 'tpl_industry_research', '行业调研任务模板',
 JSON_ARRAY('research','industry'),
 JSON_OBJECT(
   'nodes', JSON_ARRAY(
     JSON_OBJECT('nodeId','n1','title','收集行业公开数据','agentId',1001,'abilityTags',JSON_ARRAY('search','web_crawl')),
     JSON_OBJECT('nodeId','n2','title','整理市场规模与竞争格局','agentId',1001,'abilityTags',JSON_ARRAY('analyze','summarize'),'dependsOn',JSON_ARRAY('n1')),
     JSON_OBJECT('nodeId','n3','title','输出调研报告','agentId',1001,'abilityTags',JSON_ARRAY('write','report'),'dependsOn',JSON_ARRAY('n2'))
   ),
   'edges', JSON_ARRAY(
     JSON_OBJECT('from','n1','to','n2','depType','data'),
     JSON_OBJECT('from','n2','to','n3','depType','data')
   )
 ),
 JSON_OBJECT('industry',JSON_OBJECT('type','string','required',true),'depth',JSON_OBJECT('type','string','enum',JSON_ARRAY('overview','deep'),'default','overview')),
 0, 0.9200, 2, 'system'),

-- 代码生成模板
(3002, 'tpl_code_generation', '代码生成任务模板',
 JSON_ARRAY('code','development'),
 JSON_OBJECT(
   'nodes', JSON_ARRAY(
     JSON_OBJECT('nodeId','n1','title','检索相关代码上下文','agentId',1002,'abilityTags',JSON_ARRAY('code_search','retrieve')),
     JSON_OBJECT('nodeId','n2','title','生成代码实现','agentId',1002,'abilityTags',JSON_ARRAY('code_generate'),'dependsOn',JSON_ARRAY('n1')),
     JSON_OBJECT('nodeId','n3','title','代码审查与测试','agentId',1002,'abilityTags',JSON_ARRAY('code_review','test'),'dependsOn',JSON_ARRAY('n2'))
   ),
   'edges', JSON_ARRAY(
     JSON_OBJECT('from','n1','to','n2','depType','data'),
     JSON_OBJECT('from','n2','to','n3','depType','data')
   )
 ),
 JSON_OBJECT('language',JSON_OBJECT('type','string','required',true),'task_desc',JSON_OBJECT('type','string','required',true)),
 0, 0.8800, 2, 'system'),

-- 数据分析模板
(3003, 'tpl_data_analysis', '数据分析任务模板',
 JSON_ARRAY('data','analysis'),
 JSON_OBJECT(
   'nodes', JSON_ARRAY(
     JSON_OBJECT('nodeId','n1','title','查询数据源','agentId',1003,'abilityTags',JSON_ARRAY('query','sql')),
     JSON_OBJECT('nodeId','n2','title','数据清洗与统计','agentId',1003,'abilityTags',JSON_ARRAY('clean','stats'),'dependsOn',JSON_ARRAY('n1')),
     JSON_OBJECT('nodeId','n3','title','可视化与结论','agentId',1003,'abilityTags',JSON_ARRAY('visualize','conclude'),'dependsOn',JSON_ARRAY('n2'))
   ),
   'edges', JSON_ARRAY(
     JSON_OBJECT('from','n1','to','n2','depType','data'),
     JSON_OBJECT('from','n2','to','n3','depType','data')
   )
 ),
 JSON_OBJECT('data_source',JSON_OBJECT('type','string','required',true),'metrics',JSON_OBJECT('type','array','items',JSON_OBJECT('type','string'))),
 0, 0.9000, 2, 'system');

-- =====================================================================
-- 3. agent_risk 库: role + permission_policy + role_permission 初始化
-- =====================================================================
USE agent_risk;

-- ---------------------------------------------------------------------
-- 3.1 role 初始化 (4 角色)
-- ---------------------------------------------------------------------
INSERT INTO `role` (`id`, `role_id`, `role_code`, `name`, `description`, `permissions`, `status`, `created_by`) VALUES
(4001, 'r_admin',     'admin',     '系统管理员', '全量权限, 含工具/模型/Agent/知识库/审计/配额管理',
 JSON_ARRAY('*:*:*'), 1, 'system'),
(4002, 'r_developer', 'developer', '开发者',    '工具/模型/Agent 配置与发布, 不含审计删除权限',
 JSON_ARRAY('tool:*:*','model:*:*','agent:*:*','knowledge:*:*'), 1, 'system'),
(4003, 'r_operator',  'operator',  '运营人员',  '工具/Agent 执行与监控, 不可修改配置',
 JSON_ARRAY('tool:execute:*','agent:execute:*','tool:read:*','agent:read:*','model:read:*'), 1, 'system'),
(4004, 'r_user',      'user',      '普通用户',  '仅可执行已授权工具与 Agent, 不可读取配置',
 JSON_ARRAY('tool:execute:*','agent:execute:*'), 1, 'system');

-- ---------------------------------------------------------------------
-- 3.2 permission_policy 初始化 (按角色配置 tool/model/agent 权限)
-- ---------------------------------------------------------------------
INSERT INTO `permission_policy` (`id`, `policy_id`, `subject_type`, `subject_id`, `resource_type`, `resource_id`, `action`, `effect`, `conditions`, `expire_at`, `created_by`) VALUES
-- admin: 全量 allow
(5001, 'p_admin_tool_all',     'role','r_admin','tool','*','execute','allow',NULL,NULL,'system'),
(5002, 'p_admin_tool_manage',   'role','r_admin','tool','*','write',  'allow',NULL,NULL,'system'),
(5003, 'p_admin_model_all',    'role','r_admin','model','*','execute','allow',NULL,NULL,'system'),
(5004, 'p_admin_model_manage', 'role','r_admin','model','*','write',  'allow',NULL,NULL,'system'),
(5005, 'p_admin_agent_all',    'role','r_admin','agent','*','execute','allow',NULL,NULL,'system'),
(5006, 'p_admin_agent_manage', 'role','r_admin','agent','*','write',  'allow',NULL,NULL,'system'),
(5007, 'p_admin_kb_all',       'role','r_admin','knowledge','*','execute','allow',NULL,NULL,'system'),
(5008, 'p_admin_kb_manage',    'role','r_admin','knowledge','*','write',  'allow',NULL,NULL,'system'),

-- developer: tool/model/agent/knowledge 读写
(5011, 'p_dev_tool_read',    'role','r_developer','tool','*','read',   'allow',NULL,NULL,'system'),
(5012, 'p_dev_tool_write',   'role','r_developer','tool','*','write',  'allow',NULL,NULL,'system'),
(5013, 'p_dev_tool_exec',    'role','r_developer','tool','*','execute','allow',NULL,NULL,'system'),
(5014, 'p_dev_model_read',   'role','r_developer','model','*','read',   'allow',NULL,NULL,'system'),
(5015, 'p_dev_model_write',  'role','r_developer','model','*','write',  'allow',NULL,NULL,'system'),
(5016, 'p_dev_model_exec',   'role','r_developer','model','*','execute','allow',NULL,NULL,'system'),
(5017, 'p_dev_agent_read',   'role','r_developer','agent','*','read',   'allow',NULL,NULL,'system'),
(5018, 'p_dev_agent_write',  'role','r_developer','agent','*','write',  'allow',NULL,NULL,'system'),
(5019, 'p_dev_agent_exec',   'role','r_developer','agent','*','execute','allow',NULL,NULL,'system'),
(5020, 'p_dev_kb_read',      'role','r_developer','knowledge','*','read',   'allow',NULL,NULL,'system'),
(5021, 'p_dev_kb_write',     'role','r_developer','knowledge','*','write',  'allow',NULL,NULL,'system'),
(5022, 'p_dev_kb_exec',      'role','r_developer','knowledge','*','execute','allow',NULL,NULL,'system'),

-- operator: 工具/Agent 执行 + 配置只读
(5031, 'p_op_tool_read',  'role','r_operator','tool','*','read',   'allow',NULL,NULL,'system'),
(5032, 'p_op_tool_exec',  'role','r_operator','tool','*','execute','allow',NULL,NULL,'system'),
(5033, 'p_op_model_read', 'role','r_operator','model','*','read',   'allow',NULL,NULL,'system'),
(5034, 'p_op_agent_read', 'role','r_operator','agent','*','read',   'allow',NULL,NULL,'system'),
(5035, 'p_op_agent_exec', 'role','r_operator','agent','*','execute','allow',NULL,NULL,'system'),
(5036, 'p_op_kb_read',    'role','r_operator','knowledge','*','read',   'allow',NULL,NULL,'system'),
(5037, 'p_op_kb_exec',    'role','r_operator','knowledge','*','execute','allow',NULL,NULL,'system'),

-- user: 仅执行已授权工具与 Agent
(5041, 'p_user_tool_exec', 'role','r_user','tool','*','execute','allow',JSON_OBJECT('risk_level_max',2),NULL,'system'),
(5042, 'p_user_agent_exec','role','r_user','agent','*','execute','allow',NULL,NULL,'system'),
(5043, 'p_user_kb_read',   'role','r_user','knowledge','*','read','allow',NULL,NULL,'system'),

-- 显式 deny: user 禁止写工具/模型/Agent
(5051, 'p_user_tool_write_deny',  'role','r_user','tool','*','write','deny',NULL,NULL,'system'),
(5052, 'p_user_model_write_deny', 'role','r_user','model','*','write','deny',NULL,NULL,'system'),
(5053, 'p_user_agent_write_deny', 'role','r_user','agent','*','write','deny',NULL,NULL,'system');

-- ---------------------------------------------------------------------
-- 3.3 role_permission 关联初始化
-- ---------------------------------------------------------------------
INSERT INTO `role_permission` (`id`, `role_id`, `policy_id`, `effect`, `created_by`) VALUES
-- admin
(6001,'r_admin','p_admin_tool_all','allow','system'),
(6002,'r_admin','p_admin_tool_manage','allow','system'),
(6003,'r_admin','p_admin_model_all','allow','system'),
(6004,'r_admin','p_admin_model_manage','allow','system'),
(6005,'r_admin','p_admin_agent_all','allow','system'),
(6006,'r_admin','p_admin_agent_manage','allow','system'),
(6007,'r_admin','p_admin_kb_all','allow','system'),
(6008,'r_admin','p_admin_kb_manage','allow','system'),
-- developer
(6011,'r_developer','p_dev_tool_read','allow','system'),
(6012,'r_developer','p_dev_tool_write','allow','system'),
(6013,'r_developer','p_dev_tool_exec','allow','system'),
(6014,'r_developer','p_dev_model_read','allow','system'),
(6015,'r_developer','p_dev_model_write','allow','system'),
(6016,'r_developer','p_dev_model_exec','allow','system'),
(6017,'r_developer','p_dev_agent_read','allow','system'),
(6018,'r_developer','p_dev_agent_write','allow','system'),
(6019,'r_developer','p_dev_agent_exec','allow','system'),
(6020,'r_developer','p_dev_kb_read','allow','system'),
(6021,'r_developer','p_dev_kb_write','allow','system'),
(6022,'r_developer','p_dev_kb_exec','allow','system'),
-- operator
(6031,'r_operator','p_op_tool_read','allow','system'),
(6032,'r_operator','p_op_tool_exec','allow','system'),
(6033,'r_operator','p_op_model_read','allow','system'),
(6034,'r_operator','p_op_agent_read','allow','system'),
(6035,'r_operator','p_op_agent_exec','allow','system'),
(6036,'r_operator','p_op_kb_read','allow','system'),
(6037,'r_operator','p_op_kb_exec','allow','system'),
-- user
(6041,'r_user','p_user_tool_exec','allow','system'),
(6042,'r_user','p_user_agent_exec','allow','system'),
(6043,'r_user','p_user_kb_read','allow','system'),
(6051,'r_user','p_user_tool_write_deny','deny','system'),
(6052,'r_user','p_user_model_write_deny','deny','system'),
(6053,'r_user','p_user_agent_write_deny','deny','system');

-- =====================================================================
-- 4. agent_tool 库: tool_registry 初始化 (示例工具)
-- =====================================================================
USE agent_tool;

INSERT INTO `tool_registry` (`id`, `tool_id`, `name`, `display_name`, `description`, `scene_tags`, `ability_tags`, `tool_type`, `risk_level`, `input_schema`, `output_schema`, `error_codes`, `executor_type`, `endpoint`, `timeout_ms`, `avg_cost_cent`, `avg_duration_ms`, `undo_action`, `prompt_cache_key`, `status`, `version`, `created_by`) VALUES
(7001, 't_web_search', 'web_search', '网页搜索', '搜索引擎查询, 返回相关网页摘要',
 JSON_ARRAY('research','general'), JSON_ARRAY('search','web_crawl'),
 'atomic', 1,
 JSON_OBJECT('type','object','properties',JSON_OBJECT('query',JSON_OBJECT('type','string'),'top_k',JSON_OBJECT('type','integer','default',5)),'required',JSON_ARRAY('query')),
 JSON_OBJECT('type','object','properties',JSON_OBJECT('results',JSON_OBJECT('type','array'))),
 JSON_ARRAY(JSON_OBJECT('code','TIMEOUT','msg','请求超时')),
 'general', 'tool-engine.WebSearch/Search', 10000, 5, 800, NULL, 'tool:web_search:v1', 2, 1, 'system'),

(7002, 't_query_order_db', 'query_order_db', '订单查询', '查询订单数据库, 返回订单详情',
 JSON_ARRAY('order','query'), JSON_ARRAY('query','order'),
 'atomic', 2,
 JSON_OBJECT('type','object','properties',JSON_OBJECT('order_id',JSON_OBJECT('type','string')),'required',JSON_ARRAY('order_id')),
 JSON_OBJECT('type','object','properties',JSON_OBJECT('order',JSON_OBJECT('type','object'))),
 JSON_ARRAY(JSON_OBJECT('code','NOT_FOUND','msg','订单不存在')),
 'proxy', 'tool-engine.OrderQuery/Query', 5000, 8, 200, NULL, 'tool:order_query:v1', 2, 1, 'system'),

(7003, 't_code_search', 'code_search', '代码检索', '检索代码库, 返回符号与片段',
 JSON_ARRAY('code','development'), JSON_ARRAY('code_search','retrieve'),
 'atomic', 1,
 JSON_OBJECT('type','object','properties',JSON_OBJECT('keyword',JSON_OBJECT('type','string'),'language',JSON_OBJECT('type','string')),'required',JSON_ARRAY('keyword')),
 JSON_OBJECT('type','object','properties',JSON_OBJECT('snippets',JSON_OBJECT('type','array'))),
 JSON_ARRAY(JSON_OBJECT('code','EMPTY','msg','无匹配结果')),
 'general', 'tool-engine.CodeSearch/Search', 8000, 3, 500, NULL, 'tool:code_search:v1', 2, 1, 'system'),

(7004, 't_send_email', 'send_email', '发送邮件', '发送通知邮件 (写操作, 含补偿)',
 JSON_ARRAY('notification'), JSON_ARRAY('send','email'),
 'atomic', 3,
 JSON_OBJECT('type','object','properties',JSON_OBJECT('to',JSON_OBJECT('type','string'),'subject',JSON_OBJECT('type','string'),'body',JSON_OBJECT('type','string')),'required',JSON_ARRAY('to','subject','body')),
 JSON_OBJECT('type','object','properties',JSON_OBJECT('message_id',JSON_OBJECT('type','string'))),
 JSON_ARRAY(JSON_OBJECT('code','SEND_FAIL','msg','发送失败')),
 'general', 'tool-engine.EmailSend/Send', 15000, 20, 1500,
 JSON_OBJECT('compensate','recall_email','endpoint','tool-engine.EmailSend/Recall'), 'tool:email_send:v1', 2, 1, 'system'),

(7005, 't_sql_exec', 'sql_exec', 'SQL 执行', '执行只读 SQL 查询 (R3 高危, 需审批)',
 JSON_ARRAY('data','analysis'), JSON_ARRAY('query','sql'),
 'atomic', 3,
 JSON_OBJECT('type','object','properties',JSON_OBJECT('sql',JSON_OBJECT('type','string'),'db',JSON_OBJECT('type','string')),'required',JSON_ARRAY('sql','db')),
 JSON_OBJECT('type','object','properties',JSON_OBJECT('rows',JSON_OBJECT('type','array'))),
 JSON_ARRAY(JSON_OBJECT('code','SQL_INVALID','msg','SQL 语法错误'),JSON_OBJECT('code','WRITE_BLOCKED','msg','禁止写操作')),
 'sandbox', 'tool-engine.SqlExec/Exec', 30000, 15, 2000, NULL, 'tool:sql_exec:v1', 2, 1, 'system');

-- =====================================================================
-- 5. agent_knowledge 库: knowledge_base 初始化 (默认知识库)
-- =====================================================================
USE agent_knowledge;

INSERT INTO `knowledge_base` (`id`, `kb_id`, `name`, `domain`, `description`, `milvus_collection`, `embedding_model`, `chunk_strategy`, `status`, `created_by`) VALUES
(8001, 'kb_default', '平台默认知识库', 'general', '平台共享知识库, 含产品文档/FAQ/政策',
 'knowledge_chunk', 'bge-large-zh-v1.5',
 JSON_OBJECT('strategy','recursive','chunk_size',512,'overlap',64), 1, 'system'),
(8002, 'kb_code', '代码知识库', 'code', '代码符号与片段知识库',
 'code_symbol', 'bge-large-zh-v1.5',
 JSON_OBJECT('strategy','symbol','chunk_size',1024,'overlap',0), 1, 'system');

-- =====================================================================
-- 6. agent_repo 库: agent_definition 初始化 (示例 Agent)
-- =====================================================================
USE agent_repo;

INSERT INTO `agent_definition` (`id`, `agent_id`, `name`, `description`, `ability_tags`, `scene_tags`, `system_prompt`, `core_constraints`, `business_config`, `model_tier`, `max_steps`, `max_token`, `bound_tools`, `bound_knowledge_ids`, `reflection_mode`, `status`, `version`, `created_by`) VALUES
(9001, 'ag_general_assistant', '通用助手', '通用问答与任务执行 Agent',
 JSON_ARRAY('chat','qa','general'), JSON_ARRAY('general','chat'),
 '你是 Agent 平台的通用助手, 负责理解用户意图并调用工具完成任务。',
 '# 核心约束 (不可省略)\n1. 涉及金额、订单号等事实性数据, 必须通过工具查询获取, 禁止基于上下文推断。\n2. 输出中所有事实陈述必须标注 [来源:知识库/工具名/任务输入], 无来源视为疑似幻觉。\n3. 信息不足以回答时, 必须输出 NEED_MORE_INFO 并说明缺失项, 禁止编造。\n4. 工具调用入参必须严格匹配 inputSchema 类型与必填项, 禁止使用未知工具名。\n5. 输出结构必须符合约定 Schema, 缺失字段判定为格式幻觉。',
 JSON_OBJECT('defaultPageSize',20,'outputLang','zh-CN'), 'middle', 10, 32768,
 JSON_ARRAY('t_web_search','t_query_order_db','t_code_search'),
 JSON_ARRAY('kb_default'), 'single', 2, 1, 'system'),

(9002, 'ag_code_engineer', '代码工程师', '代码生成与调试 Agent',
 JSON_ARRAY('code','debug','review'), JSON_ARRAY('code','development'),
 '你是 Agent 平台的代码工程师, 负责代码生成、调试与审查。',
 '# 核心约束 (不可省略)\n1. 代码改动必须基于检索到的真实代码上下文, 禁止凭空生成不存在的 API。\n2. 生成的代码必须通过编译/语法校验, 输出完整可运行片段。\n3. 涉及破坏性变更 (删除/重构) 必须明确标注影响范围。\n4. 工具调用必须先检索后生成, 召回为空则拒答并要求补充上下文。',
 JSON_OBJECT('defaultLang','python','maxLoops',3), 'strong', 15, 65536,
 JSON_ARRAY('t_code_search','t_sql_exec'),
 JSON_ARRAY('kb_code'), 'multi', 2, 1, 'system'),

(9003, 'ag_data_analyst', '数据分析师', '数据分析与可视化 Agent',
 JSON_ARRAY('analyze','stats','visualize'), JSON_ARRAY('data','analysis'),
 '你是 Agent 平台的数据分析师, 负责数据查询、统计与可视化。',
 '# 核心约束 (不可省略)\n1. 数据查询必须通过 sql_exec 工具执行, 禁止编造数据。\n2. SQL 必须为只读查询, 禁止任何写操作。\n3. 统计结论必须标注样本量与时间范围, 样本不足时说明。\n4. 可视化结果必须基于真实查询结果, 禁止伪造数据点。',
 JSON_OBJECT('defaultChartType','bar','maxRows',10000), 'middle', 12, 32768,
 JSON_ARRAY('t_sql_exec','t_web_search'),
 JSON_ARRAY('kb_default'), 'single', 2, 1, 'system');

-- =====================================================================
-- 7. agent_quality 库: eval_baseline 初始化 (基准集)
-- =====================================================================
USE agent_quality;

INSERT INTO `eval_baseline` (`id`, `baseline_id`, `name`, `baseline_type`, `agent_id`, `version`, `sample_count`, `golden_metrics`, `created_by`) VALUES
(11001, 'bl_behavior_default', '默认行为基准集', 'behavior', NULL, 1, 200,
 JSON_OBJECT('tool_call_rate',JSON_ARRAY(0.3,0.5),'avg_steps',JSON_ARRAY(3,6),'refusal_rate',0.05), 'system'),
(11002, 'bl_effect_default', '默认效果基准集', 'effect', NULL, 1, 100,
 JSON_OBJECT('success_rate',0.92,'accuracy',0.88,'hallucination_rate',0.05), 'system'),
(11003, 'bl_alignment_default', '默认对齐基准集', 'alignment', NULL, 1, 50,
 JSON_OBJECT('role_consistency',0.95,'soft_violation_rate',0.02), 'system');
