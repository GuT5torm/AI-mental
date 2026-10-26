-- 管理员维护的全局大模型配置：单行表（id 固定 1），API Key AES-GCM 加密存储
-- 保存后对话链路自动启用真实模型（用户级自配 Key 仍优先），无需重启
-- 回退：DROP TABLE IF EXISTS ai_system_config;
CREATE TABLE IF NOT EXISTS ai_system_config (
    id smallint NOT NULL,
    api_key_cipher text NOT NULL,
    base_url character varying(255),
    model character varying(100),
    updated_by bigint,
    created_at timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ai_system_config_pkey PRIMARY KEY (id)
);

COMMENT ON TABLE ai_system_config IS '管理员维护的全局大模型配置（AES-GCM 加密存储，接口只回掩码）';
COMMENT ON COLUMN ai_system_config.id IS '固定为 1 的单行配置';
COMMENT ON COLUMN ai_system_config.api_key_cipher IS 'API Key 密文（AES-GCM，Base64(iv):Base64(密文+标签)）';
COMMENT ON COLUMN ai_system_config.base_url IS 'OpenAI 兼容端点，空则用服务端默认';
COMMENT ON COLUMN ai_system_config.model IS '模型名，空则用服务端默认';
COMMENT ON COLUMN ai_system_config.updated_by IS '最近一次保存的管理员用户ID';
COMMENT ON COLUMN ai_system_config.created_at IS '创建时间';
COMMENT ON COLUMN ai_system_config.updated_at IS '最近更新时间';
