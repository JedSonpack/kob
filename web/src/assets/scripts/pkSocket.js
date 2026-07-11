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

function applyGameEvent(game, data, updateLoser) {
  const [snake0, snake1] = game.snakes;

  if (data.event === "move") {
    snake0.set_direction(data.a_direction);
    snake1.set_direction(data.b_direction);
  } else if (data.event === "result") {
    if (data.loser === "all" || data.loser === "A") {
      snake0.status = "die";
    }
    if (data.loser === "all" || data.loser === "B") {
      snake1.status = "die";
    }
    updateLoser(data.loser);
  }
}

function isReadyForNextEvent(game) {
  return game.snakes.every(
    (snake) => snake.status === "idle" && snake.direction === -1
  );
}

export function createGameEventDispatcher(getGame, updateLoser) {
  const pendingEvents = [];

  const catchUpToResult = (game) => {
    if (typeof game.finishCurrentMove === "function") {
      game.finishCurrentMove();
    }
    while (pendingEvents.length > 0) {
      const event = pendingEvents.shift();
      applyGameEvent(game, event, updateLoser);
      if (event.event === "move" && typeof game.finishCurrentMove === "function") {
        game.finishCurrentMove();
      }
    }
    if (typeof game.renderCurrentState === "function") {
      game.renderCurrentState();
    }
  };

  return {
    dispatch(data) {
      pendingEvents.push(data);
      return this.flush();
    },
    flush() {
      const game = getGame();
      if (!game || pendingEvents.length === 0) {
        return false;
      }
      if (pendingEvents.some((event) => event.event === "result")) {
        catchUpToResult(game);
        return true;
      }
      if (!isReadyForNextEvent(game)) return false;

      applyGameEvent(game, pendingEvents.shift(), updateLoser);
      return true;
    },
  };
}
