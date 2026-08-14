import { config } from './config.js';

/**
 * 给异常打上 kind 标记，供 diag.js 归类。
 * 不靠匹配错误文案来判断原因——文案会变，标记不会。
 */
function tagged(message, kind, extra = {}) {
  const error = new Error(message);
  error.kind = kind;
  Object.assign(error, extra);
  return error;
}

async function call(path, init = {}) {
  let response;
  try {
    response = await fetch(`${config.backendBaseUrl}${path}`, {
      ...init,
      headers: {
        'Content-Type': 'application/json',
        'X-Service-Token': config.serviceToken,
        ...(init.headers ?? {}),
      },
    });
  } catch (cause) {
    throw tagged(`调用 ${path} 失败：连不上后端（${cause.message}）`, 'unreachable');
  }

  const text = await response.text();
  if (!response.ok) {
    // 401/403 基本只有一种成因：后端没配 COLLAB_SERVICE_TOKEN 或两侧取值不一致，
    // 单独归类才能在界面上把它和"用户没权限"区分开
    const kind = response.status === 401 || response.status === 403 ? 'unauthorized' : 'http-error';
    throw tagged(`调用 ${path} 失败：HTTP ${response.status} ${text}`, kind, {
      httpStatus: response.status,
    });
  }

  const body = text ? JSON.parse(text) : {};
  if (body.code !== undefined && body.code !== 200) {
    throw tagged(`调用 ${path} 失败：${body.code} ${body.message}`, 'http-error', {
      bizCode: body.code,
    });
  }
  return body.data;
}

/**
 * 服务令牌探活：/collab/diag 用它判断"后端是否认这把令牌"。
 */
export function ping() {
  return call('/api/internal/evaluation/ping');
}

/**
 * 拉取播种数据：列取自评分维度，行取自已分配的面试名单。
 */
export function fetchSeed(cycleId) {
  return call(`/api/internal/evaluation/board/${cycleId}/seed`);
}

/**
 * 物化回写。返回 { accepted, rejected, rejectReasons }。
 */
export function materialize(payload) {
  return call('/api/internal/evaluation/materialize', {
    method: 'POST',
    body: JSON.stringify(payload),
  });
}
