/**
 * 记录每个单元格最后由谁写入，供物化时填 originUserId。
 *
 * 做法：给 rows 挂深度观察者，把本次事务里变动的单元格收进缓冲区；
 * Hocuspocus 在处理完该条消息后调用 onChange，届时才能拿到发起者身份，
 * 此时把缓冲区归属给这个人。Node 单线程且 Hocuspocus 按文档串行处理消息，
 * 因此 onChange 时缓冲区内容就对应刚处理的那条更新。
 *
 * 这是「谁写了哪一格」的唯一可信来源——客户端自报的字段都可能被伪造。
 */
export function createWriterTracker(doc) {
  const writers = new Map();
  let pending = new Set();

  doc.getMap('rows').observeDeep((events) => {
    for (const event of events) {
      // path 相对于 rows：行内单元格变更为 ['<rowKey>']，Y.Text 内部变更为 ['<rowKey>', '<cellKey>']
      if (event.path.length === 1) {
        const rowKey = event.path[0];
        for (const cellKey of event.changes.keys.keys()) {
          if (cellKey !== '_info') {
            pending.add(`${rowKey}/${cellKey}`);
          }
        }
      } else if (event.path.length === 2) {
        pending.add(`${event.path[0]}/${event.path[1]}`);
      }
    }
  });

  return {
    writers,

    /** 把缓冲区里的变更归属给该用户 */
    commit(userId) {
      if (userId === undefined || userId === null) {
        pending = new Set();
        return;
      }
      for (const cell of pending) {
        writers.set(cell, userId);
      }
      pending = new Set();
    },

    /** 服务端自己的播种/对账写入不算任何人的操作 */
    discard() {
      pending = new Set();
    },
  };
}
