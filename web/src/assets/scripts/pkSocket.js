/**
 * WebSocket 发送安全包装（审计任务 3.3）。
 *
 * <p>原匹配按钮直接 socket.send，连接未建立时（onopen 前）会抛异常。
 * safeSend 仅在 socket 处于 OPEN 状态时发送，否则静默返回 false，消除"未连接发送"竞态。
 */
export function safeSend(socket, message) {
  if (socket && socket.readyState === WebSocket.OPEN) {
    socket.send(message);
    return true;
  }
  return false;
}
