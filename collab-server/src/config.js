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
// 能连上评价表并写入的权限。权限拆分后管理侧归 interview:board:manage，
// 但阶段一签发的旧令牌里只有 resume:audit，故两者并存；旧令牌全部过期后可去掉 resume:audit。
export const REQUIRED_PERMISSIONS = ['interview:evaluate', 'interview:board:manage', 'resume:audit'];

/** 管理员权限码：可读全部、可配置维度、可锁定 */
// 管理级：能跨场次读写、能在锁定后仍写入。同上，过渡期两者任一即可。
// 注意这是协同服务侧的判定，后端的 @PreAuthorize 覆盖不到这里 ——
// 只改注解不改这两个常量，管理员会在评价表里失去管理级身份。
export const ADMIN_PERMISSIONS = ['interview:board:manage', 'resume:audit'];
