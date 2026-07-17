<template>
  <div class="table-responsive">
    <table class="table table-sm align-middle">
      <thead>
        <tr>
          <th>序号</th>
          <th>阶段</th>
          <th>工具</th>
          <th>状态</th>
          <th>耗时</th>
          <th>Token</th>
          <th>摘要</th>
        </tr>
      </thead>
      <tbody>
        <tr v-if="!steps.length">
          <td colspan="7" class="text-center text-muted">暂无 Trace</td>
        </tr>
        <tr v-for="s in steps" :key="s.id">
          <td>{{ s.sequence }}</td>
          <td>{{ s.phase || "-" }}</td>
          <td>{{ s.toolName || "-" }}</td>
          <td>
            <span class="badge" :class="statusClass(s.status)">{{ s.status }}</span>
          </td>
          <td>{{ formatMetric(s.durationMs, 0) }} ms</td>
          <td>{{ token(s) }}</td>
          <td class="agent-trace-summary">
            {{ s.outputSummary || s.inputSummary || "-" }}
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
    steps: { type: Array, default: () => [] },
  },
  setup() {
    const statusClass = (s) =>
      ({
        SUCCESS: "bg-success",
        FAILED: "bg-danger",
        RUNNING: "bg-primary",
      }[s] || "bg-secondary");
    const token = (s) => {
      const p = s.promptTokens || 0;
      const c = s.completionTokens || 0;
      return p + c === 0 ? "-" : p + "+" + c;
    };
    return { formatMetric, statusClass, token };
  },
};
</script>

<style scoped>
.agent-trace-summary {
  max-width: 320px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
</style>
