import 'dotenv/config';

/**
 * 读取必填环境变量，缺失时直接退出——协同服务是面试现场的关键路径，
 * 与其带着半截配置跑起来、在有人编辑时才失败，不如启动阶段就暴露问题。
 */
function required(name) {
  const value = process.env[name];
  if (!value) {
    throw new Error(`缺少必填环境变量 ${name}`);
  }
  return value;
}

export const config = {
  port: Number(process.env.COLLAB_PORT ?? 3100),

  /** 与 Java 后端共享的 JWT 密钥（HS256，原始 UTF-8 字节），用于连接鉴权 */
  jwtSecret: required('JWT_SECRET'),

  /** 调用 Java /api/internal/** 的服务令牌 */
  serviceToken: required('COLLAB_SERVICE_TOKEN'),

  /** Java 后端基地址，如 http://official-backend:8080 */
  backendBaseUrl: required('BACKEND_BASE_URL'),

  db: {
    host: required('DB_HOST'),
    port: Number(process.env.DB_PORT ?? 3306),
    user: required('DB_USER'),
    password: required('DB_PASSWORD'),
    database: required('DB_NAME'),
    connectionLimit: Number(process.env.DB_POOL_SIZE ?? 5),
  },

  /** 文档快照落库的防抖窗口（毫秒） */
  storeDebounceMs: Number(process.env.STORE_DEBOUNCE_MS ?? 2000),

  /** 物化回写业务库的防抖窗口（毫秒） */
  materializeDebounceMs: Number(process.env.MATERIALIZE_DEBOUNCE_MS ?? 30000),

  /** 名单对账周期（毫秒）：人工调剂/改期后把新增行补进文档 */
  reconcileIntervalMs: Number(process.env.RECONCILE_INTERVAL_MS ?? 300000),
};

/** 进入评价表所需的权限码，任一即可 */
export const REQUIRED_PERMISSIONS = ['interview:evaluate', 'resume:audit'];

/** 管理员权限码：可读全部、可配置维度、可锁定 */
export const ADMIN_PERMISSION = 'resume:audit';
