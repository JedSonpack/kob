<template>
  <div class="table-responsive">
    <table class="table table-sm align-middle">
      <thead>
        <tr>
          <th>版本</th>
          <th>策略摘要</th>
          <th>公开得分</th>
          <th>胜率</th>
          <th>平均回合</th>
          <th>P95 延迟</th>
          <th>合法移动率</th>
          <th>结果</th>
          <th>操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-if="!versions.length">
          <td colspan="9" class="text-center text-muted">暂无版本</td>
        </tr>
        <tr v-for="v in versions" :key="v.id">
          <td>
            V{{ v.iteration }}
            <span v-if="isBest(v.id)" class="badge bg-success ms-1">最佳</span>
          </td>
          <td class="agent-strategy">{{ v.strategySummary || "-" }}</td>
          <td>{{ formatMetric(v.publicScore, 3) }}</td>
          <td>{{ formatMetric(pct(v.publicWinRate), 1) }}%</td>
          <td>{{ formatMetric(v.publicAverageRounds, 1) }}</td>
          <td>{{ formatMetric(v.publicP95Ms, 0) }} ms</td>
          <td>{{ legalRate(v.publicInvalidMoveCount) }}</td>
          <td>
            <span class="badge" :class="v.accepted ? 'bg-success' : 'bg-secondary'">
              {{ v.accepted ? "接受" : "拒绝" }}
            </span>
          </td>
          <td>
            <button class="btn btn-link btn-sm p-0" @click="$emit('view', v)">
              查看代码
            </button>
            <template v-if="canSave && isBest(v.id)">
              <button
                v-if="v.id === savedVersionId"
                class="btn btn-link btn-sm p-0 ms-2"
                disabled
              >
                已保存
              </button>
              <button
                v-else
                class="btn btn-link btn-sm p-0 ms-2"
                @click="$emit('save', v)"
              >
                保存为我的Bot
              </button>
            </template>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<script>
import { formatMetric } from "@/assets/scripts/agentViewModel";

export default {
  props: {
    versions: { type: Array, default: () => [] },
    bestVersionId: { default: null },
    canSave: { type: Boolean, default: false },
    savedVersionId: { default: null },
  },
  emits: ["view", "save"],
  setup(props) {
    const isBest = (id) => props.bestVersionId != null && id === props.bestVersionId;
    const pct = (v) => (v == null ? null : v * 100);
    const legalRate = (n) =>
      n == null ? "-" : n === 0 ? "100%" : n + " 次非法";
    return { formatMetric, isBest, pct, legalRate };
  },
};
</script>

<style scoped>
.agent-strategy {
  max-width: 220px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
</style>
