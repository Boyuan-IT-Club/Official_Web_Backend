import { config } from './config.js';
import { ping } from './backend-api.js';
import { pingDb } from './db.js';

/**
 * 自检信息。
 *
 * 存在的理由：Hocuspocus 把 onAuthenticate / onLoadDocument 的任何失败都统一回报成
 * `permission-denied`，错误原文不会传到浏览器。于是"后端容器少配了服务令牌"这类配置问题，
 * 在界面上长得和"你没有评价权限"一模一样，只能翻服务端日志才知道真相。
 * 本模块把最近一次失败按类型记下来，并通过 GET /collab/diag 暴露给前端，
 * 让界面能显示真正的原因与下一步动作。
 */

/** documentName -> { code, detail, at } */
const docErrors = new Map();

export const DIAG_CODES = {
  BOARD_NOT_OPENED: {
    title: '该周期的评价表尚未开启',
    hint: '请管理员在「面试评价表」页点「开启评价表」后重试。',
  },
  NOT_CYCLE_INTERVIEWER: {
    title: '你不是该招募周期的面试官',
    hint: '评价表按周期隔离。请管理员在「面试场次」里把你绑定到该周期的某个场次后重试。',
  },
    BACKEND_UNAUTHORIZED: {
    title: '协同服务未通过后端的服务间认证',
    hint: '后端容器缺少 COLLAB_SERVICE_TOKEN，或与协同服务的取值不一致。检查两个容器的该环境变量后重启后端。',
  },
  BACKEND_UNREACHABLE: {
    title: '协同服务连不上后端',
    hint: '检查 BACKEND_BASE_URL 指向的后端容器是否在运行。',
  },
  BACKEND_ERROR: {
    title: '后端返回了错误',
    hint: '详见协同服务日志与后端日志。',
  },
  DB_ERROR: {
    title: '协同服务无法访问数据库',
    hint: '检查协同服务的 DB_* 环境变量与数据库容器状态。',
  },
  NO_PERMISSION: {
    title: '当前账号没有面试评价权限',
    hint: '需要 interview:evaluate 或 resume:audit 权限，请管理员在「用户与角色」中分配。',
  },
  UNKNOWN: {
    title: '未归类的错误',
    hint: '详见协同服务日志。',
  },
};

/**
 * 把异常归类成上面的 code。backend-api 会在 error 上打 kind 标记，
 * 其余靠约定字段（boardNotOpened / noPermission）判断，避免用文案匹配。
 */
export function classify(error) {
  if (!error) return 'UNKNOWN';
  if (error.boardNotOpened) return 'BOARD_NOT_OPENED';
  if (error.notCycleInterviewer) return 'NOT_CYCLE_INTERVIEWER';
  if (error.noPermission) return 'NO_PERMISSION';
  switch (error.kind) {
    case 'unauthorized':
      return 'BACKEND_UNAUTHORIZED';
    case 'unreachable':
      return 'BACKEND_UNREACHABLE';
    case 'http-error':
      return 'BACKEND_ERROR';
    case 'db':
      return 'DB_ERROR';
    default:
      return 'UNKNOWN';
  }
}

export function recordDocError(documentName, error) {
  const code = classify(error);
  docErrors.set(documentName, {
    code,
    detail: error?.message ?? String(error),
    at: new Date().toISOString(),
  });
  return code;
}

export function clearDocError(documentName) {
  docErrors.delete(documentName);
}

async function probeBackend() {
  try {
    await ping();
    return { status: 'ok' };
  } catch (error) {
    return { status: classify(error) === 'BACKEND_UNAUTHORIZED' ? 'unauthorized' : 'unreachable' };
  }
}

async function probeDb() {
  try {
    await pingDb();
    return { status: 'ok' };
  } catch (error) {
    return { status: 'error' };
  }
}

/**
 * 组装自检结果。刻意只给粗粒度状态与固定文案，不回传后端原始响应体，
 * 避免这个无需鉴权的端点变成信息泄漏面。
 */
export async function buildDiag() {
  const [backend, db] = await Promise.all([probeBackend(), probeDb()]);
  const docs = {};
  for (const [name, info] of docErrors.entries()) {
    const meta = DIAG_CODES[info.code] ?? DIAG_CODES.UNKNOWN;
    docs[name] = { code: info.code, title: meta.title, hint: meta.hint, at: info.at };
  }
  return {
    ok: backend.status === 'ok' && db.status === 'ok' && docErrors.size === 0,
    backendBaseUrl: config.backendBaseUrl,
    backend,
    db,
    docs,
  };
}
