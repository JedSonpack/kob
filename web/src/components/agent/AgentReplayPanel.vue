<template>
  <div class="agent-replay-panel">
    <div v-if="!replayLoaded">
      <button
        class="btn btn-sm btn-outline-primary"
        :disabled="loading"
        @click="load"
      >
        {{ loading ? "加载中..." : "播放录像" }}
      </button>
    </div>
    <div v-else>
      <div class="agent-replay-meta">
        <span>对手：{{ opponentLabel(run.opponentKey) }}</span>
        <span>出生侧：{{ run.side }}</span>
        <span>结果：{{ resultLabel(run.result) }}</span>
        <span>回合：{{ run.rounds }}</span>
        <span v-if="run.failureReason">失败原因：{{ run.failureReason }}</span>
        <span v-if="run.datasetType === 'HIDDEN'" class="badge bg-warning text-dark ms-1">隐藏集</span>
      </div>
      <div class="agent-replay-canvas">
        <GameMap :key="replayKey" />
      </div>
      <button class="btn btn-sm btn-outline-secondary mt-2" @click="playAgain">
        重新播放
      </button>
    </div>
  </div>
</template>

<script>
import { ref, onBeforeUnmount } from "vue";
import { useStore } from "vuex";
import GameMap from "@/components/GameMap.vue";
import { populateRecordFromItem } from "@/assets/scripts/recordHelper";

const OPPONENT_LABELS = { greedy: "贪婪", territory: "圈地", safe: "保守" };

export default {
  components: { GameMap },
  props: {
    run: { type: Object, required: true },
  },
  setup(props) {
    const store = useStore();
    let replayLoaded = ref(false);
    let replayData = ref(null);
    let replayKey = ref(0);
    let loading = ref(false);

    const load = async () => {
      loading.value = true;
      try {
        const data = await store.dispatch("agent/loadReplay", props.run.id);
        if (data) {
          // 先写入录像状态再渲染 GameMap，确保挂载时读到 is_record 与 steps
          populateRecordFromItem(store, data);
          replayData.value = data;
          replayLoaded.value = true;
          replayKey.value++;
        }
      } finally {
        loading.value = false;
      }
    };

    const playAgain = () => {
      if (!replayData.value) return;
      // 重新写入状态并以新 key 重挂 GameMap，从头播放
      populateRecordFromItem(store, replayData.value);
      replayKey.value++;
    };

    onBeforeUnmount(() => {
      // 复用 pk/record 状态，卸载时复位录像标记，避免影响在线对战
      store.commit("updateIsRecord", false);
    });

    const opponentLabel = (k) => OPPONENT_LABELS[k] || k || "-";
    const resultLabel = (r) =>
      ({ WIN: "胜", LOSS: "负", DRAW: "平" }[r] || r || "-");

    return {
      replayLoaded,
      replayKey,
      loading,
      load,
      playAgain,
      opponentLabel,
      resultLabel,
    };
  },
};
</script>

<style scoped>
.agent-replay-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  font-size: 13px;
  color: #6c757d;
  margin-bottom: 8px;
}
.agent-replay-canvas {
  width: 100%;
  max-width: 600px;
  height: 400px;
  border: 1px solid #dee2e6;
  border-radius: 6px;
  overflow: hidden;
}
</style>
