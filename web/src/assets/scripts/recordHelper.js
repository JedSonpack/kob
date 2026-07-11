const stringTo2D = (map) => {
  let g = [];
  for (let i = 0, k = 0; i < 13; i++) {
    let line = [];
    for (let j = 0; j < 14; j++, k++) {
      if (map[k] === "0") line.push(0);
      else line.push(1);
    }
    g.push(line);
  }
  return g;
};

/**
 * 将后端返回的录像 item 写入 Vuex，供 PlayGround 回放（审计 3.2）。
 * 供列表点击与详情页按 ID 拉取共用，避免重复。
 */
export function populateRecordFromItem(store, item) {
  store.commit("updateIsRecord", true);
  store.commit("updateGame", {
    map: stringTo2D(item.record.map),
    a_id: item.record.aid,
    a_sx: item.record.asx,
    a_sy: item.record.asy,
    b_id: item.record.bid,
    b_sx: item.record.bsx,
    b_sy: item.record.bsy,
  });
  store.commit("updateSteps", {
    a_steps: item.record.asteps,
    b_steps: item.record.bsteps,
  });
  store.commit("updateRecordLoser", item.record.loser);
}
